package space.panrid.novelka.telegram.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_LINK;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_OUTBOX;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.jooq.DSLContext;
import org.jooq.InsertValuesStep7;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import space.panrid.novelka.jooq.tables.records.TelegramOutboxRecord;
import space.panrid.novelka.notification.ChapterNewsBatch;
import space.panrid.novelka.notification.NotificationChoices;
import space.panrid.novelka.telegram.TelegramApi;
import space.panrid.novelka.telegram.TelegramApi.Delivery;
import space.panrid.novelka.telegram.TelegramApi.Outcome;
import space.panrid.novelka.telegram.TelegramQueue;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Telegram messages about new chapters (план 21.9). A batch of readers told on the site queues
 * its messages in the same transaction ({@link ChapterNewsBatch}); this sender sends them no
 * faster than Telegram allows, tries again after pauses when Telegram or the network is down,
 * forgets chats that blocked the bot and drops messages whose chapters were unpublished
 * meanwhile. A message is marked «sending» before it goes: one left so by a crash is not sent
 * again, since it may have reached the person.
 */
@Component
class TelegramOutbox implements TelegramQueue {

    /** Taken from the queue at once. */
    static final int BURST = 25;
    /** At most 25 messages a second; Telegram allows about 30 to different chats. */
    static final long GAP_MILLIS = 40;
    static final Duration LEASE = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 6;
    private static final List<Integer> BACKOFF_SECONDS = List.of(15, 60, 300, 900, 3600);
    private static final Duration KEEP_FINISHED = Duration.ofDays(7);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final Logger log = LoggerFactory.getLogger(TelegramOutbox.class);

    private final DSLContext db;
    private final TelegramApi telegram;
    private final TelegramLinks links;
    private final Texts texts;
    private final NotificationChoices choices;
    private final JsonMapper json;
    private final Clock clock;
    private long lastSent;
    /** Telegram asked to slow down: nothing goes before this. */
    private volatile Instant pausedUntil = Instant.EPOCH;

    TelegramOutbox(DSLContext db, TelegramApi telegram, TelegramLinks links, Texts texts, NotificationChoices choices,
            JsonMapper json, Clock clock) {
        this.db = db;
        this.telegram = telegram;
        this.links = links;
        this.texts = texts;
        this.choices = choices;
        this.json = json;
        this.clock = clock;
    }

    /** In the batch's transaction: readers with a tied chat who want this topic in Telegram. */
    @EventListener
    void on(ChapterNewsBatch batch) {
        if (!telegram.configured() || batch.recipients().isEmpty()) {
            return;
        }
        Set<Long> off = choices.offTelegram(batch.recipients(), NotificationChoices.Topic.of(batch.kind()));
        List<Long> tied = db.select(TELEGRAM_LINK.ACCOUNT_ID).from(TELEGRAM_LINK)
                .where(TELEGRAM_LINK.ACCOUNT_ID.in(batch.recipients()))
                .fetch(TELEGRAM_LINK.ACCOUNT_ID).stream().filter(id -> !off.contains(id)).toList();
        if (tied.isEmpty()) {
            return;
        }
        JSONB payload = JSONB.valueOf(json.writeValueAsString(batch.payload()));
        InsertValuesStep7<TelegramOutboxRecord, Long, Long, Long, Integer, Integer, String, JSONB> insert = db.insertInto(TELEGRAM_OUTBOX,
                TELEGRAM_OUTBOX.ACCOUNT_ID, TELEGRAM_OUTBOX.NEWS_ID, TELEGRAM_OUTBOX.EDITION_ID, TELEGRAM_OUTBOX.FIRST_NUMBER,
                TELEGRAM_OUTBOX.LAST_NUMBER, TELEGRAM_OUTBOX.KIND, TELEGRAM_OUTBOX.PAYLOAD);
        for (long accountId : tied) {
            insert = insert.values(accountId, batch.newsId(), batch.editionId(), batch.first(), batch.last(), batch.kind(), payload);
        }
        // The same reader of the same publication twice is still one message.
        insert.onConflictDoNothing().execute();
    }

    @Override
    public int drain() {
        return drain(() -> false);
    }

    /** Sends until nothing is ready, {@code stop} says so, or Telegram asks to wait. */
    int drain(BooleanSupplier stop) {
        int tried = 0;
        while (!stop.getAsBoolean() && Instant.now(clock).isAfter(pausedUntil)) {
            List<TelegramOutboxRecord> rows = claim();
            if (rows.isEmpty()) {
                break;
            }
            for (int i = 0; i < rows.size(); i++) {
                if (!pace()) {
                    release(rows.subList(i, rows.size()), Duration.ZERO);
                    return tried;
                }
                Duration wait = send(rows.get(i));
                tried++;
                if (wait != null) {
                    pausedUntil = Instant.now(clock).plus(wait);
                    release(rows.subList(i + 1, rows.size()), wait);
                    return tried;
                }
            }
        }
        return tried;
    }

    /** The next ready messages, marked as being sent: other senders skip them. */
    private List<TelegramOutboxRecord> claim() {
        // Left «sending» by a crash: it may have gone, so it is not sent again.
        db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.STATE, "unknown").set(TELEGRAM_OUTBOX.FINISHED_AT, now())
                .where(TELEGRAM_OUTBOX.STATE.eq("sending"), TELEGRAM_OUTBOX.NOT_BEFORE.lt(DSL.currentOffsetDateTime()))
                .execute();
        var ready = TELEGRAM_OUTBOX.as("ready");
        return db.update(TELEGRAM_OUTBOX)
                .set(TELEGRAM_OUTBOX.STATE, "sending")
                .set(TELEGRAM_OUTBOX.ATTEMPTS, TELEGRAM_OUTBOX.ATTEMPTS.plus(1))
                .set(TELEGRAM_OUTBOX.NOT_BEFORE, now().plus(LEASE))
                .where(TELEGRAM_OUTBOX.ID.in(DSL.select(ready.ID).from(ready)
                        .where(ready.STATE.eq("pending"), ready.NOT_BEFORE.le(DSL.currentOffsetDateTime()))
                        .orderBy(ready.ID).limit(BURST)
                        .forUpdate().skipLocked()))
                .returning()
                .fetch()
                .sortAsc(TELEGRAM_OUTBOX.ID);
    }

    /** Waits for the gap between two messages; false when interrupted. */
    private synchronized boolean pace() {
        long wait = lastSent + GAP_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        lastSent = System.currentTimeMillis();
        return true;
    }

    /** One message. Returns how long Telegram asked everyone to wait, if it did. */
    private Duration send(TelegramOutboxRecord row) {
        if (!stillNews(row) || !choices.inTelegram(row.getAccountId(), NotificationChoices.Topic.of(row.getKind()))) {
            finish(row, "cancelled");
            return null;
        }
        Optional<TelegramLinks.Link> link = links.of(row.getAccountId());
        if (link.isEmpty()) {
            finish(row, "gone");
            return null;
        }
        Delivery delivery;
        try {
            Map<String, Object> payload = json.readValue(row.getPayload().data(), MAP);
            delivery = telegram.deliver(link.get().chatId(), texts.photo(row.getKind(), payload), texts.notification(row.getKind(), payload));
        } catch (RuntimeException error) {
            log.warn("Telegram message {} failed", row.getId(), error);
            delivery = Delivery.of(Outcome.RETRY);
        }
        switch (delivery.outcome()) {
            case SENT -> finish(row, "sent");
            case GONE -> {
                links.forget(link.get().chatId());
                finish(row, "gone");
            }
            case REFUSED -> finish(row, "failed");
            case RETRY -> {
                if (row.getAttempts() >= MAX_ATTEMPTS) {
                    finish(row, "failed");
                } else {
                    Duration pause = Duration.ofSeconds(BACKOFF_SECONDS.get(Math.min(row.getAttempts(), BACKOFF_SECONDS.size()) - 1));
                    if (delivery.retryAfter() != null && delivery.retryAfter().compareTo(pause) > 0) {
                        pause = delivery.retryAfter();
                    }
                    db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.STATE, "pending").set(TELEGRAM_OUTBOX.NOT_BEFORE, now().plus(pause))
                            .where(TELEGRAM_OUTBOX.ID.eq(row.getId()), TELEGRAM_OUTBOX.STATE.eq("sending")).execute();
                }
            }
        }
        return delivery.outcome() == Outcome.RETRY ? delivery.retryAfter() : null;
    }

    /** Readers can still open some of the chapters the message is about. */
    private boolean stillNews(TelegramOutboxRecord row) {
        return db.fetchExists(DSL.selectOne().from(CHAPTER).join(EDITION).on(EDITION.ID.eq(CHAPTER.EDITION_ID))
                .where(CHAPTER.EDITION_ID.eq(row.getEditionId()),
                        CHAPTER.NUMBER.between(row.getFirstNumber(), row.getLastNumber()),
                        CHAPTER.PUBLISHED_REVISION_ID.isNotNull(), CHAPTER.DELETED_AT.isNull(),
                        EDITION.DELETED_AT.isNull(), EDITION.HIDDEN_AT.isNull()));
    }

    private void finish(TelegramOutboxRecord row, String state) {
        db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.STATE, state).set(TELEGRAM_OUTBOX.FINISHED_AT, now())
                .where(TELEGRAM_OUTBOX.ID.eq(row.getId())).execute();
    }

    /** Taken but not tried: back to the queue as they were. */
    private void release(List<TelegramOutboxRecord> rows, Duration wait) {
        if (rows.isEmpty()) {
            return;
        }
        db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.STATE, "pending").set(TELEGRAM_OUTBOX.ATTEMPTS, TELEGRAM_OUTBOX.ATTEMPTS.minus(1))
                .set(TELEGRAM_OUTBOX.NOT_BEFORE, now().plus(wait))
                .where(TELEGRAM_OUTBOX.ID.in(rows.stream().map(TelegramOutboxRecord::getId).toList()),
                        TELEGRAM_OUTBOX.STATE.eq("sending"))
                .execute();
    }

    /** What went (or never will) is kept a week, to look into; then it goes. */
    void forgetOld() {
        db.deleteFrom(TELEGRAM_OUTBOX)
                .where(TELEGRAM_OUTBOX.STATE.notIn("pending", "sending"), TELEGRAM_OUTBOX.FINISHED_AT.lt(now().minus(KEEP_FINISHED)))
                .execute();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** The schedule, on a thread of its own; switched off in tests, which call {@link #drain()} themselves. */
    @Component
    @ConditionalOnProperty(name = "novelka.telegram.outbox", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final TelegramOutbox outbox;
        private final AtomicBoolean busy = new AtomicBoolean();
        private volatile boolean stopping;
        private final ExecutorService thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "telegram-outbox");
            worker.setDaemon(true);
            return worker;
        });

        Schedule(TelegramOutbox outbox) {
            this.outbox = outbox;
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 1_000)
        void tick() {
            if (!busy.compareAndSet(false, true)) {
                return;
            }
            thread.execute(() -> {
                try {
                    outbox.drain(() -> stopping);
                } catch (RuntimeException error) {
                    log.error("Telegram outbox failed", error);
                } finally {
                    busy.set(false);
                }
            });
        }

        @Scheduled(initialDelay = 600_000, fixedDelay = 3_600_000)
        void forgetOld() {
            try {
                outbox.forgetOld();
            } catch (RuntimeException error) {
                log.error("Forgetting old Telegram messages failed", error);
            }
        }

        /** A deploy: the message on its way goes, the rest waits in the queue. */
        @jakarta.annotation.PreDestroy
        void stop() throws InterruptedException {
            stopping = true;
            thread.shutdown();
            thread.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}
