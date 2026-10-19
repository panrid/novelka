package space.panrid.novelka.webhook.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_SUBSCRIPTION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;
import static space.panrid.novelka.jooq.Tables.WEBHOOK_DELIVERY;
import static space.panrid.novelka.jooq.Tables.WEBHOOK_ENDPOINT;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.autotranslate.RunEnded;
import space.panrid.novelka.autotranslate.SourceChaptersFound;
import space.panrid.novelka.jooq.tables.records.WebhookDeliveryRecord;
import space.panrid.novelka.jooq.tables.records.WebhookEndpointRecord;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.suggestion.SuggestionsSubmitted;
import space.panrid.novelka.text.ChaptersPublished;
import space.panrid.novelka.webhook.WebhookAddresses;
import tools.jackson.databind.json.JsonMapper;

/**
 * Webhooks (етап 22.5): a person's endpoints, the queue they are fed from and the journal of
 * deliveries. An event is queued by a plain listener, so in the transaction of whatever happened:
 * the deliveries commit with the chapters published (the run ended, the suggestions sent…) or
 * not at all. {@link WebhookOutbox} sends them.
 */
@Component
class Webhooks {

    /** Endpoints one person may have. */
    static final int MAX_ENDPOINTS = 10;
    /** Translations one endpoint may name. */
    static final int MAX_EDITIONS = 50;
    /** Deliveries the journal shows. */
    static final int JOURNAL = 50;
    static final String SECRET_PREFIX = "whsec_";

    /**
     * An endpoint as its owner sees it; the secret only by its first characters.
     *
     * @param editionIds the translations it hears about; empty — all it may
     * @param failures   failed attempts in a row
     */
    record EndpointView(long id, String url, List<String> events, List<Long> editionIds, boolean enabled, OffsetDateTime disabledAt,
            String disabledReason, int failures, String secretHint, OffsetDateTime createdAt) {
    }

    /** An endpoint just made: the only time {@code secret} is told. */
    record Created(EndpointView endpoint, String secret) {
    }

    /**
     * A row of the journal.
     *
     * @param state       pending, sending, delivered, failed or cancelled
     * @param status      what the receiver answered last, null when it did not
     * @param nextAttemptAt when it goes again, for a pending one
     */
    record DeliveryView(long id, String deliveryId, String event, String state, int attempts, Integer status, String error,
            Integer durationMs, OffsetDateTime createdAt, OffsetDateTime lastAttemptAt, OffsetDateTime nextAttemptAt) {
    }

    /** @param team only for the translation's team */
    record EventOption(String code, boolean team) {
    }

    /** A translation an endpoint may name: one of the person's teams, or one they follow. */
    record TranslationOption(long editionId, String title, String novel, String team, boolean member) {
    }

    record Overview(List<EndpointView> endpoints, List<EventOption> events, List<TranslationOption> translations) {
    }

    private final DSLContext db;
    private final WebhookAddresses addresses;
    private final WebhookAccess access;
    private final WebhookPayloads payloads;
    private final WebhookOutbox outbox;
    private final JsonMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    Webhooks(DSLContext db, WebhookAddresses addresses, WebhookAccess access, WebhookPayloads payloads, WebhookOutbox outbox,
            JsonMapper json, Clock clock) {
        this.db = db;
        this.addresses = addresses;
        this.access = access;
        this.payloads = payloads;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    // ---- the person's endpoints (session only) --------------------------------------------------

    Overview overview(Viewer viewer) {
        return new Overview(views(WEBHOOK_ENDPOINT.ACCOUNT_ID.eq(viewer.accountId())),
                WebhookEvent.subscribable().stream().map(event -> new EventOption(event.code(), event.team())).toList(),
                translations(viewer.accountId()));
    }

    @Transactional
    Created create(Viewer viewer, String url, List<String> eventCodes, List<Long> editionIds) {
        String address = addresses.resolve(url).uri().toString();
        String[] events = events(eventCodes);
        Long[] editions = editions(viewer.accountId(), editionIds);
        // One at a time per person, so two quick clicks do not slip past the limit together.
        db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).forUpdate().execute();
        if (db.fetchCount(WEBHOOK_ENDPOINT, WEBHOOK_ENDPOINT.ACCOUNT_ID.eq(viewer.accountId())) >= MAX_ENDPOINTS) {
            throw UserFacingException.conflict("Вебхуків може бути щонайбільше " + MAX_ENDPOINTS + ". Видаліть непотрібні.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long id = db.insertInto(WEBHOOK_ENDPOINT)
                .set(WEBHOOK_ENDPOINT.ACCOUNT_ID, viewer.accountId())
                .set(WEBHOOK_ENDPOINT.URL, address)
                .set(WEBHOOK_ENDPOINT.SECRET, secret)
                .set(WEBHOOK_ENDPOINT.EVENTS, events)
                .set(WEBHOOK_ENDPOINT.EDITION_IDS, editions)
                .set(WEBHOOK_ENDPOINT.CREATED_AT, now())
                .returning(WEBHOOK_ENDPOINT.ID).fetchSingle(WEBHOOK_ENDPOINT.ID);
        return new Created(view(id), secret);
    }

    /** Changes what is given; null keeps it. Switching on again starts the count of failures anew. */
    @Transactional
    EndpointView update(Viewer viewer, long id, List<String> eventCodes, List<Long> editionIds, Boolean enabled) {
        WebhookEndpointRecord endpoint = mine(viewer, id);
        if (eventCodes != null) {
            endpoint.setEvents(events(eventCodes));
        }
        if (editionIds != null) {
            endpoint.setEditionIds(editions(viewer.accountId(), editionIds));
        }
        if (Boolean.TRUE.equals(enabled) && endpoint.getDisabledAt() != null) {
            addresses.resolve(endpoint.getUrl());
            endpoint.setDisabledAt(null);
            endpoint.setDisabledReason(null);
            endpoint.setFailures(0);
            endpoint.setFailingSince(null);
        } else if (Boolean.FALSE.equals(enabled) && endpoint.getDisabledAt() == null) {
            endpoint.setDisabledAt(now());
            endpoint.setDisabledReason("Вимкнено вами.");
        }
        endpoint.store();
        return view(id);
    }

    @Transactional
    void delete(Viewer viewer, long id) {
        mine(viewer, id);
        db.deleteFrom(WEBHOOK_ENDPOINT).where(WEBHOOK_ENDPOINT.ID.eq(id)).execute();
    }

    List<DeliveryView> deliveries(Viewer viewer, long id) {
        mine(viewer, id);
        return db.selectFrom(WEBHOOK_DELIVERY).where(WEBHOOK_DELIVERY.ENDPOINT_ID.eq(id))
                .orderBy(WEBHOOK_DELIVERY.ID.desc()).limit(JOURNAL)
                .fetch(Webhooks::delivery);
    }

    /** «Надіслати пробну подію»: a {@code ping} sent at once, even to an endpoint switched off; tried only once. */
    DeliveryView test(Viewer viewer, long id) {
        mine(viewer, id);
        WebhookPayloads.Ping ping = new WebhookPayloads.Ping("Пробна подія з Новелки: якщо ви це бачите, адреса працює.");
        long deliveryId = db.insertInto(WEBHOOK_DELIVERY)
                .set(WEBHOOK_DELIVERY.ENDPOINT_ID, id)
                .set(WEBHOOK_DELIVERY.DELIVERY_KEY, UUID.randomUUID())
                .set(WEBHOOK_DELIVERY.EVENT, WebhookEvent.PING.code())
                .set(WEBHOOK_DELIVERY.DATA, JSONB.valueOf(json.writeValueAsString(ping)))
                .set(WEBHOOK_DELIVERY.CREATED_AT, now())
                .returning(WEBHOOK_DELIVERY.ID).fetchSingle(WEBHOOK_DELIVERY.ID);
        outbox.sendNow(deliveryId);
        return deliveryView(deliveryId);
    }

    /**
     * «Повторити»: the same delivery — the same {@code X-Novelka-Delivery}, the same body — sent
     * now, with the full set of attempts again. Who may hear is checked as for any other.
     */
    DeliveryView retry(Viewer viewer, long id, long deliveryId) {
        WebhookEndpointRecord endpoint = mine(viewer, id);
        WebhookDeliveryRecord delivery = db.selectFrom(WEBHOOK_DELIVERY)
                .where(WEBHOOK_DELIVERY.ID.eq(deliveryId), WEBHOOK_DELIVERY.ENDPOINT_ID.eq(id)).fetchOne();
        if (delivery == null) {
            throw UserFacingException.notFound("Такої доставки немає.");
        }
        if (endpoint.getDisabledAt() != null && !WebhookEvent.PING.code().equals(delivery.getEvent())) {
            throw UserFacingException.conflict("Вебхук вимкнено — спершу увімкніть його.");
        }
        int changed = db.update(WEBHOOK_DELIVERY)
                .set(WEBHOOK_DELIVERY.STATE, "pending")
                .set(WEBHOOK_DELIVERY.ATTEMPTS, 0)
                .set(WEBHOOK_DELIVERY.NOT_BEFORE, now())
                .set(WEBHOOK_DELIVERY.FINISHED_AT, (OffsetDateTime) null)
                .where(WEBHOOK_DELIVERY.ID.eq(deliveryId), WEBHOOK_DELIVERY.STATE.in("delivered", "failed", "cancelled"))
                .execute();
        if (changed == 0) {
            throw UserFacingException.conflict("Ця доставка ще в черзі — дочекайтеся її.");
        }
        outbox.sendNow(deliveryId);
        return deliveryView(deliveryId);
    }

    // ---- events, queued in their own transaction -------------------------------------------------

    @EventListener
    void on(ChaptersPublished published) {
        enqueue(WebhookEvent.CHAPTER_PUBLISHED, published.editionId(), published.first(), published.last(),
                () -> payloads.chaptersPublished(published.editionId(), published.first(), published.last()));
    }

    @EventListener
    void on(RunEnded ended) {
        WebhookEvent event = "done".equals(ended.state()) ? WebhookEvent.RUN_FINISHED : WebhookEvent.RUN_STOPPED;
        enqueue(event, ended.editionId(), null, null,
                () -> payloads.run(ended.editionId(), ended.jobId(), ended.state(), ended.first(), ended.last(), ended.error()));
    }

    @EventListener
    void on(SuggestionsSubmitted submitted) {
        enqueue(WebhookEvent.SUGGESTION_CREATED, submitted.editionId(), null, null,
                () -> payloads.suggestions(submitted.editionId(), submitted.authorId(), submitted.count()));
    }

    @EventListener
    void on(SourceChaptersFound found) {
        enqueue(WebhookEvent.SOURCE_CHAPTERS, found.editionId(), null, null,
                () -> payloads.sourceChapters(found.editionId(), found.first(), found.last()));
    }

    /**
     * One delivery per live endpoint that wants the event and may hear about the translation. The
     * body is made once, only when someone listens. Runs in the caller's transaction: every event
     * above is published inside one.
     */
    void enqueue(WebhookEvent event, long editionId, Integer first, Integer last, Supplier<Object> data) {
        List<Record2<Long, Long>> endpoints = db.select(WEBHOOK_ENDPOINT.ID, WEBHOOK_ENDPOINT.ACCOUNT_ID).from(WEBHOOK_ENDPOINT)
                .where(WEBHOOK_ENDPOINT.DISABLED_AT.isNull(),
                        DSL.condition("{0} = ANY({1})", DSL.val(event.code()), WEBHOOK_ENDPOINT.EVENTS),
                        DSL.condition("(cardinality({0}) = 0 OR {1} = ANY({0}))", WEBHOOK_ENDPOINT.EDITION_IDS, DSL.val(editionId)))
                .orderBy(WEBHOOK_ENDPOINT.ID)
                .fetch();
        JSONB body = null;
        for (Record2<Long, Long> endpoint : endpoints) {
            if (!access.allowed(endpoint.value2(), editionId, event.team())) {
                continue;
            }
            if (body == null) {
                body = JSONB.valueOf(json.writeValueAsString(data.get()));
            }
            db.insertInto(WEBHOOK_DELIVERY)
                    .set(WEBHOOK_DELIVERY.ENDPOINT_ID, endpoint.value1())
                    .set(WEBHOOK_DELIVERY.DELIVERY_KEY, UUID.randomUUID())
                    .set(WEBHOOK_DELIVERY.EVENT, event.code())
                    .set(WEBHOOK_DELIVERY.EDITION_ID, editionId)
                    .set(WEBHOOK_DELIVERY.FIRST_NUMBER, first)
                    .set(WEBHOOK_DELIVERY.LAST_NUMBER, last)
                    .set(WEBHOOK_DELIVERY.DATA, body)
                    .set(WEBHOOK_DELIVERY.CREATED_AT, now())
                    .execute();
        }
    }

    // ---- reading and checking ---------------------------------------------------------------------

    private WebhookEndpointRecord mine(Viewer viewer, long id) {
        WebhookEndpointRecord endpoint = db.selectFrom(WEBHOOK_ENDPOINT)
                .where(WEBHOOK_ENDPOINT.ID.eq(id), WEBHOOK_ENDPOINT.ACCOUNT_ID.eq(viewer.accountId())).fetchOne();
        if (endpoint == null) {
            throw UserFacingException.notFound("Такого вебхука немає.");
        }
        return endpoint;
    }

    private static String[] events(List<String> codes) {
        Set<String> chosen = new LinkedHashSet<>();
        for (String code : codes == null ? List.<String>of() : codes) {
            WebhookEvent event = WebhookEvent.of(code).filter(WebhookEvent.subscribable()::contains)
                    .orElseThrow(() -> UserFacingException.badRequest("Такої події немає: " + code + "."));
            chosen.add(event.code());
        }
        if (chosen.isEmpty()) {
            throw UserFacingException.badRequest("Позначте хоча б одну подію.");
        }
        return chosen.toArray(String[]::new);
    }

    /** The translations named must be ones the person may hear about: their team's, or ones readers see. */
    private Long[] editions(long accountId, List<Long> ids) {
        Set<Long> chosen = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (chosen.size() > MAX_EDITIONS) {
            throw UserFacingException.badRequest("Перекладів для одного вебхука — щонайбільше " + MAX_EDITIONS + ".");
        }
        for (long editionId : chosen) {
            if (!access.allowed(accountId, editionId, false)) {
                throw UserFacingException.badRequest("Одного з перекладів немає або він вам недоступний.");
            }
        }
        return chosen.toArray(Long[]::new);
    }

    /** The person's teams' translations first, then those they rang the bell for. */
    private List<TranslationOption> translations(long accountId) {
        Condition member = TEAM.OWNER_ID.eq(accountId).orExists(DSL.selectOne().from(TEAM_MEMBER)
                .where(TEAM_MEMBER.TEAM_ID.eq(TEAM.ID), TEAM_MEMBER.ACCOUNT_ID.eq(accountId)));
        List<TranslationOption> options = new ArrayList<>(db.select(EDITION.ID, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), NOVEL.SLUG, TEAM.HANDLE)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.DELETED_AT.isNull(), member)
                .orderBy(EDITION.ID.desc()).limit(200)
                .fetch(r -> new TranslationOption(r.value1(), r.value2(), r.value3(), r.value4(), true)));
        Set<Long> seen = new LinkedHashSet<>(options.stream().map(TranslationOption::editionId).toList());
        db.select(EDITION.ID, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), NOVEL.SLUG, TEAM.HANDLE)
                .from(EDITION_SUBSCRIPTION).join(EDITION).on(EDITION.ID.eq(EDITION_SUBSCRIPTION.EDITION_ID))
                .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION_SUBSCRIPTION.ACCOUNT_ID.eq(accountId), EDITION.DELETED_AT.isNull(), EDITION.HIDDEN_AT.isNull())
                .orderBy(EDITION_SUBSCRIPTION.CREATED_AT.desc()).limit(200)
                .fetch(r -> new TranslationOption(r.value1(), r.value2(), r.value3(), r.value4(), false))
                .forEach(option -> {
                    if (seen.add(option.editionId())) {
                        options.add(option);
                    }
                });
        return options;
    }

    private EndpointView view(long id) {
        return views(WEBHOOK_ENDPOINT.ID.eq(id)).getFirst();
    }

    private List<EndpointView> views(Condition where) {
        return db.selectFrom(WEBHOOK_ENDPOINT).where(where).orderBy(WEBHOOK_ENDPOINT.ID).fetch(e -> new EndpointView(e.getId(),
                e.getUrl(), Arrays.asList(e.getEvents()), Arrays.asList(e.getEditionIds()), e.getDisabledAt() == null, e.getDisabledAt(),
                e.getDisabledReason(), e.getFailures(), e.getSecret().substring(0, SECRET_PREFIX.length() + 4) + "…", e.getCreatedAt()));
    }

    private DeliveryView deliveryView(long deliveryId) {
        return delivery(db.selectFrom(WEBHOOK_DELIVERY).where(WEBHOOK_DELIVERY.ID.eq(deliveryId)).fetchSingle());
    }

    private static DeliveryView delivery(WebhookDeliveryRecord d) {
        return new DeliveryView(d.getId(), d.getDeliveryKey().toString(), d.getEvent(), d.getState(), d.getAttempts(), d.getLastStatus(),
                d.getLastError(), d.getLastDurationMs(), d.getCreatedAt(), d.getLastAttemptAt(),
                "pending".equals(d.getState()) ? d.getNotBefore() : null);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
