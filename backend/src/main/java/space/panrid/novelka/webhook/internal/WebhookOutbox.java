package space.panrid.novelka.webhook.internal;

import static space.panrid.novelka.jooq.Tables.WEBHOOK_DELIVERY;
import static space.panrid.novelka.jooq.Tables.WEBHOOK_ENDPOINT;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import space.panrid.novelka.jooq.tables.records.WebhookDeliveryRecord;
import space.panrid.novelka.jooq.tables.records.WebhookEndpointRecord;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.webhook.WebhookAddresses;
import space.panrid.novelka.webhook.WebhookAddresses.Target;
import space.panrid.novelka.webhook.WebhookDisabled;

/**
 * Sends the queued webhook deliveries (етап 22.5). A delivery is taken («sending», leased for
 * {@link #LEASE}) before it goes, so two senders never send it at once; one left so by a crash goes
 * back to the queue — webhooks are at least once, and the receiver drops repeats by
 * {@code X-Novelka-Delivery}. Who may hear is checked again right before sending. A failed
 * attempt is tried again after a pause growing to 12 hours, {@link #MAX_ATTEMPTS} in all — about a
 * day; an address failing {@link #DISABLE_AFTER_FAILURES} times in a row for longer than
 * {@link #DISABLE_AFTER} is switched off, and its owner hears in «Вхідні».
 */
@Component
class WebhookOutbox {

    /** Taken from the queue at once and sent side by side. */
    static final int BURST = 10;
    static final Duration LEASE = Duration.ofMinutes(2);
    /** Pauses before the 2nd … 9th attempt: about 22 hours together. */
    static final List<Integer> BACKOFF_SECONDS = List.of(10, 60, 300, 900, 3_600, 10_800, 21_600, 43_200);
    static final int MAX_ATTEMPTS = BACKOFF_SECONDS.size() + 1;
    static final int DISABLE_AFTER_FAILURES = 20;
    static final Duration DISABLE_AFTER = Duration.ofHours(24);
    private static final Duration KEEP_FINISHED = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(WebhookOutbox.class);

    private final DSLContext db;
    private final WebhookAddresses addresses;
    private final WebhookTransport transport;
    private final WebhookAccess access;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();

    WebhookOutbox(DSLContext db, WebhookAddresses addresses, WebhookTransport transport, WebhookAccess access,
            TransactionTemplate transactions, ApplicationEventPublisher events, Clock clock) {
        this.db = db;
        this.addresses = addresses;
        this.transport = transport;
        this.access = access;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** Sends everything ready now. Returns how many were tried. */
    int drain() {
        return drain(() -> false);
    }

    int drain(BooleanSupplier stop) {
        int tried = 0;
        while (!stop.getAsBoolean()) {
            List<WebhookDeliveryRecord> rows = claim();
            if (rows.isEmpty()) {
                break;
            }
            List<Callable<Void>> sends = new ArrayList<>();
            for (WebhookDeliveryRecord row : rows) {
                sends.add(() -> {
                    sendSafely(row);
                    return null;
                });
            }
            try {
                senders.invokeAll(sends);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return tried;
            }
            tried += rows.size();
        }
        return tried;
    }

    /** The one delivery, now, on this thread: «Надіслати пробну подію» and «Повторити». */
    void sendNow(long deliveryId) {
        WebhookDeliveryRecord row = db.update(WEBHOOK_DELIVERY)
                .set(WEBHOOK_DELIVERY.STATE, "sending")
                .set(WEBHOOK_DELIVERY.ATTEMPTS, WEBHOOK_DELIVERY.ATTEMPTS.plus(1))
                .set(WEBHOOK_DELIVERY.NOT_BEFORE, now().plus(LEASE))
                .where(WEBHOOK_DELIVERY.ID.eq(deliveryId), WEBHOOK_DELIVERY.STATE.eq("pending"))
                .returning().fetchOne();
        if (row != null) {
            sendSafely(row);
        }
    }

    /** The next ready deliveries, marked as being sent: other senders skip them. */
    private List<WebhookDeliveryRecord> claim() {
        // Left «sending» by a crash: back to the queue, the attempt counted.
        db.update(WEBHOOK_DELIVERY).set(WEBHOOK_DELIVERY.STATE, "pending")
                .where(WEBHOOK_DELIVERY.STATE.eq("sending"), WEBHOOK_DELIVERY.NOT_BEFORE.lt(DSL.currentOffsetDateTime()))
                .execute();
        var ready = WEBHOOK_DELIVERY.as("ready");
        return db.update(WEBHOOK_DELIVERY)
                .set(WEBHOOK_DELIVERY.STATE, "sending")
                .set(WEBHOOK_DELIVERY.ATTEMPTS, WEBHOOK_DELIVERY.ATTEMPTS.plus(1))
                .set(WEBHOOK_DELIVERY.NOT_BEFORE, now().plus(LEASE))
                .where(WEBHOOK_DELIVERY.ID.in(DSL.select(ready.ID).from(ready)
                        .where(ready.STATE.eq("pending"), ready.NOT_BEFORE.le(DSL.currentOffsetDateTime()))
                        .orderBy(ready.ID).limit(BURST)
                        .forUpdate().skipLocked()))
                .returning()
                .fetch()
                .sortAsc(WEBHOOK_DELIVERY.ID);
    }

    private void sendSafely(WebhookDeliveryRecord row) {
        try {
            send(row);
        } catch (RuntimeException error) {
            log.error("Webhook delivery {} failed", row.getId(), error);
            // Back to the queue after the lease, as if the sender had crashed.
        }
    }

    private void send(WebhookDeliveryRecord row) {
        WebhookEndpointRecord endpoint = db.selectFrom(WEBHOOK_ENDPOINT).where(WEBHOOK_ENDPOINT.ID.eq(row.getEndpointId())).fetchOne();
        if (endpoint == null) {
            return;
        }
        WebhookEvent event = WebhookEvent.of(row.getEvent()).orElse(WebhookEvent.PING);
        boolean ping = event == WebhookEvent.PING;
        if (!ping) {
            if (endpoint.getDisabledAt() != null) {
                cancel(row, "Вебхук вимкнено.");
                return;
            }
            String refusal = access.refusal(endpoint.getAccountId(), row, event);
            if (refusal != null) {
                cancel(row, refusal);
                return;
            }
        }
        WebhookTransport.Result result = attempt(endpoint, row);
        Integer status = result.status();
        if (status != null && status >= 200 && status < 300) {
            finish(row, "delivered", result, null);
            db.update(WEBHOOK_ENDPOINT).set(WEBHOOK_ENDPOINT.FAILURES, 0).set(WEBHOOK_ENDPOINT.FAILING_SINCE, (OffsetDateTime) null)
                    .where(WEBHOOK_ENDPOINT.ID.eq(endpoint.getId())).execute();
            return;
        }
        String error = result.error() != null ? result.error()
                : status != null && status >= 300 && status < 400
                        ? "Сервер відповів " + status + " — переадресацію не виконуємо. Вкажіть кінцеву адресу."
                        : "Сервер відповів " + status + "; чекаємо 2xx.";
        // A trial does not count against the address.
        boolean off = !ping && failed(endpoint.getId());
        if (ping || row.getAttempts() >= MAX_ATTEMPTS) {
            finish(row, "failed", result, error);
        } else if (off) {
            finish(row, "cancelled", result, error + " Вебхук вимкнено.");
        } else {
            int pause = BACKOFF_SECONDS.get(Math.min(row.getAttempts(), BACKOFF_SECONDS.size()) - 1);
            db.update(WEBHOOK_DELIVERY)
                    .set(WEBHOOK_DELIVERY.STATE, "pending")
                    .set(WEBHOOK_DELIVERY.NOT_BEFORE, now().plusSeconds(pause))
                    .set(WEBHOOK_DELIVERY.LAST_STATUS, status)
                    .set(WEBHOOK_DELIVERY.LAST_ERROR, error)
                    .set(WEBHOOK_DELIVERY.LAST_DURATION_MS, result.millis())
                    .set(WEBHOOK_DELIVERY.LAST_ATTEMPT_AT, now())
                    .where(WEBHOOK_DELIVERY.ID.eq(row.getId()), WEBHOOK_DELIVERY.STATE.eq("sending"))
                    .execute();
        }
    }

    /** One POST: the address checked again (DNS may have changed), the body signed. */
    private WebhookTransport.Result attempt(WebhookEndpointRecord endpoint, WebhookDeliveryRecord row) {
        Target target;
        try {
            target = addresses.resolve(endpoint.getUrl());
        } catch (UserFacingException refused) {
            return new WebhookTransport.Result(null, 0, refused.getMessage());
        }
        byte[] body = body(row);
        long timestamp = Instant.now(clock).getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Novelka-Event", row.getEvent());
        headers.put("X-Novelka-Delivery", row.getDeliveryKey().toString());
        headers.put("X-Novelka-Timestamp", String.valueOf(timestamp));
        headers.put("X-Novelka-Signature", Signatures.sign(endpoint.getSecret(), timestamp, body));
        return transport.post(target, headers, body);
    }

    /** The envelope: the same bytes on every attempt. */
    static byte[] body(WebhookDeliveryRecord row) {
        String created = DateTimeFormatter.ISO_INSTANT.format(row.getCreatedAt().toInstant());
        return ("{\"id\":\"" + row.getDeliveryKey() + "\",\"event\":\"" + row.getEvent() + "\",\"createdAt\":\"" + created
                + "\",\"data\":" + row.getData().data() + "}").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * One more failure in a row; switched off after many over a long time. The deliveries still
     * waiting are cancelled — «Повторити» brings any back once the address is on again. Returns
     * whether the endpoint is off now (switched off by this failure, or before it).
     */
    private boolean failed(long endpointId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            WebhookEndpointRecord endpoint = db.update(WEBHOOK_ENDPOINT)
                    .set(WEBHOOK_ENDPOINT.FAILURES, WEBHOOK_ENDPOINT.FAILURES.plus(1))
                    .set(WEBHOOK_ENDPOINT.FAILING_SINCE, DSL.coalesce(WEBHOOK_ENDPOINT.FAILING_SINCE, DSL.val(now())))
                    .where(WEBHOOK_ENDPOINT.ID.eq(endpointId), WEBHOOK_ENDPOINT.DISABLED_AT.isNull())
                    .returning().fetchOne();
            if (endpoint == null) {
                return true;
            }
            if (endpoint.getFailures() < DISABLE_AFTER_FAILURES || endpoint.getFailingSince().isAfter(now().minus(DISABLE_AFTER))) {
                return false;
            }
            String reason = "Адреса не приймала події " + endpoint.getFailures() + " разів поспіль понад добу.";
            db.update(WEBHOOK_ENDPOINT).set(WEBHOOK_ENDPOINT.DISABLED_AT, now()).set(WEBHOOK_ENDPOINT.DISABLED_REASON, reason)
                    .where(WEBHOOK_ENDPOINT.ID.eq(endpointId)).execute();
            db.update(WEBHOOK_DELIVERY).set(WEBHOOK_DELIVERY.STATE, "cancelled").set(WEBHOOK_DELIVERY.FINISHED_AT, now())
                    .set(WEBHOOK_DELIVERY.LAST_ERROR, "Вебхук вимкнено: " + reason)
                    .where(WEBHOOK_DELIVERY.ENDPOINT_ID.eq(endpointId), WEBHOOK_DELIVERY.STATE.eq("pending"))
                    .execute();
            events.publishEvent(new WebhookDisabled(endpoint.getAccountId(), endpointId, endpoint.getUrl(), endpoint.getFailures()));
            return true;
        }));
    }

    private void finish(WebhookDeliveryRecord row, String state, WebhookTransport.Result result, String error) {
        db.update(WEBHOOK_DELIVERY)
                .set(WEBHOOK_DELIVERY.STATE, state)
                .set(WEBHOOK_DELIVERY.FINISHED_AT, now())
                .set(WEBHOOK_DELIVERY.LAST_STATUS, result.status())
                .set(WEBHOOK_DELIVERY.LAST_ERROR, error)
                .set(WEBHOOK_DELIVERY.LAST_DURATION_MS, result.millis())
                .set(WEBHOOK_DELIVERY.LAST_ATTEMPT_AT, now())
                .where(WEBHOOK_DELIVERY.ID.eq(row.getId()))
                .execute();
    }

    private void cancel(WebhookDeliveryRecord row, String why) {
        db.update(WEBHOOK_DELIVERY).set(WEBHOOK_DELIVERY.STATE, "cancelled").set(WEBHOOK_DELIVERY.FINISHED_AT, now())
                .set(WEBHOOK_DELIVERY.LAST_ERROR, why)
                // Not tried: the attempt taken for it does not count.
                .set(WEBHOOK_DELIVERY.ATTEMPTS, WEBHOOK_DELIVERY.ATTEMPTS.minus(1))
                .where(WEBHOOK_DELIVERY.ID.eq(row.getId())).execute();
    }

    /** What went (or never will) is kept a month, for the journal; then it goes. */
    void forgetOld() {
        db.deleteFrom(WEBHOOK_DELIVERY)
                .where(WEBHOOK_DELIVERY.STATE.notIn("pending", "sending"), WEBHOOK_DELIVERY.FINISHED_AT.lt(now().minus(KEEP_FINISHED)))
                .execute();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @jakarta.annotation.PreDestroy
    void close() {
        senders.shutdown();
    }

    /** The schedule, on a thread of its own; switched off in tests, which call {@link #drain()} themselves. */
    @Component
    @ConditionalOnProperty(name = "novelka.webhooks.outbox", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final WebhookOutbox outbox;
        private final AtomicBoolean busy = new AtomicBoolean();
        private volatile boolean stopping;
        private final ExecutorService thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "webhook-outbox");
            worker.setDaemon(true);
            return worker;
        });

        Schedule(WebhookOutbox outbox) {
            this.outbox = outbox;
        }

        @Scheduled(initialDelay = 20_000, fixedDelay = 2_000)
        void tick() {
            if (!busy.compareAndSet(false, true)) {
                return;
            }
            thread.execute(() -> {
                try {
                    outbox.drain(() -> stopping);
                } catch (RuntimeException error) {
                    log.error("Webhook outbox failed", error);
                } finally {
                    busy.set(false);
                }
            });
        }

        @Scheduled(initialDelay = 900_000, fixedDelay = 3_600_000)
        void forgetOld() {
            try {
                outbox.forgetOld();
            } catch (RuntimeException error) {
                log.error("Forgetting old webhook deliveries failed", error);
            }
        }

        /** A deploy: what is on its way goes, the rest waits in the queue. */
        @jakarta.annotation.PreDestroy
        void stop() throws InterruptedException {
            stopping = true;
            thread.shutdown();
            thread.awaitTermination(15, TimeUnit.SECONDS);
        }
    }
}
