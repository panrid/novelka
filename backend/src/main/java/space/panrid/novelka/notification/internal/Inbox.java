package space.panrid.novelka.notification.internal;

import static space.panrid.novelka.jooq.Tables.NOTIFICATION;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import space.panrid.novelka.notification.NotificationAdded;
import space.panrid.novelka.notification.NotificationChoices;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Writing and reading inbox rows; every change nudges the recipient's open tabs. */
@Component
class Inbox {

    private static final int PAGE = 20;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final space.panrid.novelka.access.PublicationNotices publicationNotices;
    private final DSLContext db;
    private final JsonMapper json;
    private final LiveEvents live;
    private final ApplicationEventPublisher events;
    private final NotificationChoices choices;

    Inbox(DSLContext db, JsonMapper json, LiveEvents live, ApplicationEventPublisher events, NotificationChoices choices, space.panrid.novelka.access.PublicationNotices publicationNotices) {
        this.publicationNotices = publicationNotices;
        this.db = db;
        this.json = json;
        this.live = live;
        this.events = events;
        this.choices = choices;
    }

    /**
     * The filters of «Вхідні» (етап 20): what each one shows. A kind not named here is only
     * under «Усі».
     */
    static final Map<String, List<String>> GROUPS = Map.of(
            "chapters", List.of("new_chapters"),
            "edits", List.of("suggestions_submitted", "suggestions_reviewed"),
            "replies", List.of("reply", "mention", "team_mention"),
            "studio", List.of("source_chapters", "takeover_request", "takeover_answered", "autotranslate_request",
                    "autotranslate_answered", "proposal_taken"),
            "system", List.of("shahs_granted", "achievement", "report", "webhook_disabled"));

    /** The person turned this topic off on the site: no row, though Telegram may still tell. */
    private boolean offSite(long recipientId, String kind) {
        return !choices.onSite(recipientId, NotificationChoices.Topic.of(kind));
    }

    void add(long recipientId, String kind, Map<String, Object> payload) {
        if (!publicationNotices.allowed(recipientId, payload)) {
            return;
        }
        if (offSite(recipientId, kind)) {
            events.publishEvent(new NotificationAdded(recipientId, kind, new java.util.HashMap<>(payload)));
            return;
        }
        db.insertInto(NOTIFICATION).set(NOTIFICATION.RECIPIENT_ID, recipientId).set(NOTIFICATION.KIND, kind)
                .set(NOTIFICATION.PAYLOAD, JSONB.valueOf(json.writeValueAsString(payload))).execute();
        nudge(recipientId);
        events.publishEvent(new NotificationAdded(recipientId, kind, new java.util.HashMap<>(payload)));
    }

    /**
     * One unread row per group: new chapters of a translation widen the range of the row
     * the reader has not seen yet instead of piling up.
     */
    void addGrouped(long recipientId, String kind, String groupKey, Map<String, Object> payload, int first, int last) {
        if (!publicationNotices.allowed(recipientId, payload)) {
            return;
        }
        Map<String, Object> fresh = new java.util.HashMap<>(payload);
        fresh.put("first", first);
        fresh.put("last", last);
        if (offSite(recipientId, kind)) {
            events.publishEvent(new NotificationAdded(recipientId, kind, fresh));
            return;
        }
        // One statement, so chapters published at the same moment widen the row instead of racing.
        db.execute(WIDEN, recipientId, kind, groupKey, json.writeValueAsString(fresh));
        nudge(recipientId);
        events.publishEvent(new NotificationAdded(recipientId, kind, fresh));
    }

    /**
     * {@link #addGrouped} for a whole batch of readers at once (one round trip), without
     * {@link NotificationAdded}: whoever sends the batch tells messengers in one event. Readers who
     * keep the topic off the site get no row.
     *
     * @param payload the row as it is, with {@code first} and {@code last}
     */
    void addGroupedToMany(List<Long> recipientIds, String kind, String groupKey, Map<String, Object> payload) {
        Set<Long> off = choices.offSite(recipientIds, NotificationChoices.Topic.of(kind));
        List<Long> told = recipientIds.stream().filter(id -> !off.contains(id) && publicationNotices.allowed(id, payload)).toList();
        if (told.isEmpty()) {
            return;
        }
        String body = json.writeValueAsString(payload);
        BatchBindStep batch = db.batch(db.query(WIDEN, 0L, "", "", "{}"));
        for (long recipientId : told) {
            batch = batch.bind(recipientId, kind, groupKey, body);
        }
        batch.execute();
        AfterCommit.run(() -> told.stream().filter(live::connected)
                .forEach(id -> live.send(id, "notifications", Map.of("unread", unread(id)))));
    }

    /** A new row, or the reader's unread row of the group widened to the new chapters too. */
    private static final String WIDEN = """
            INSERT INTO notification (recipient_id, kind, group_key, payload) VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (recipient_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
            DO UPDATE SET payload = (notification.payload || jsonb_build_object(
                    'first', LEAST((notification.payload ->> 'first')::int, (EXCLUDED.payload ->> 'first')::int),
                    'last', GREATEST((notification.payload ->> 'last')::int, (EXCLUDED.payload ->> 'last')::int),
                    'firstLabel', CASE WHEN (EXCLUDED.payload ->> 'first')::int < (notification.payload ->> 'first')::int
                        THEN EXCLUDED.payload -> 'firstLabel' ELSE notification.payload -> 'firstLabel' END,
                    'lastLabel', CASE WHEN (EXCLUDED.payload ->> 'last')::int > (notification.payload ->> 'last')::int
                        THEN EXCLUDED.payload -> 'lastLabel' ELSE notification.payload -> 'lastLabel' END))
                    -- a chapter's name only while the row is about one chapter
                    - CASE WHEN LEAST((notification.payload ->> 'first')::int, (EXCLUDED.payload ->> 'first')::int)
                              = GREATEST((notification.payload ->> 'last')::int, (EXCLUDED.payload ->> 'last')::int)
                        THEN '' ELSE 'chapterTitle' END,
                created_at = now()
            """;

    /** One unread row per group whose «count» grows: suggestions sent to a team one batch after another. */
    void addCounted(long recipientId, String kind, String groupKey, Map<String, Object> payload, int count) {
        if (!publicationNotices.allowed(recipientId, payload)) {
            return;
        }
        Map<String, Object> fresh = new java.util.HashMap<>(payload);
        fresh.put("count", count);
        if (offSite(recipientId, kind)) {
            events.publishEvent(new NotificationAdded(recipientId, kind, fresh));
            return;
        }
        db.execute("""
                INSERT INTO notification (recipient_id, kind, group_key, payload) VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (recipient_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
                DO UPDATE SET payload = EXCLUDED.payload || jsonb_build_object(
                        'count', (notification.payload ->> 'count')::int + (EXCLUDED.payload ->> 'count')::int),
                    created_at = now()
                """, recipientId, kind, groupKey, json.writeValueAsString(fresh));
        nudge(recipientId);
        events.publishEvent(new NotificationAdded(recipientId, kind, fresh));
    }

    record Item(long id, String kind, Map<String, Object> payload, OffsetDateTime createdAt, boolean read) {
    }

    /**
     * @param newest   the newest row of all (not of this page): opening the list marks up to it as seen
     * @param archived how many rows are in the archive, for its tab
     * @param groups   for each filter (and «all»): how many rows of this shelf it holds, and how many are unread
     */
    record Page(List<Item> items, int unread, boolean hasMore, int total, int page, Long newest, int archived,
            Map<String, Group> groups) {
    }

    record Group(int total, int unread) {

        Group plus(Group other) {
            return new Group(total + other.total, unread + other.unread);
        }
    }

    /**
     * The list or, with {@code archived}, the rows put away; newest first either way.
     *
     * @param group one of {@link #GROUPS}, or {@code null} for all
     */
    Page page(long recipientId, int page, boolean archived, String group) {
        int at = Math.max(1, page);
        Condition mine = NOTIFICATION.RECIPIENT_ID.eq(recipientId)
                .and(space.panrid.novelka.access.PublicationNotices.visible(NOTIFICATION.PAYLOAD, NOTIFICATION.RECIPIENT_ID));
        Condition shelf = archived ? NOTIFICATION.ARCHIVED_AT.isNotNull() : NOTIFICATION.ARCHIVED_AT.isNull();
        List<String> kinds = group == null ? null : GROUPS.get(group);
        Condition filter = kinds == null ? DSL.noCondition() : NOTIFICATION.KIND.in(kinds);
        int total = db.fetchCount(NOTIFICATION, mine.and(shelf).and(filter));
        List<Item> items = db.selectFrom(NOTIFICATION)
                .where(mine, shelf, filter)
                .orderBy(NOTIFICATION.ID.desc()).limit(PAGE).offset((at - 1) * PAGE)
                .fetch(r -> new Item(r.getId(), r.getKind(), json.readValue(r.getPayload().data(), MAP), r.getCreatedAt(), r.getReadAt() != null));
        Long newest = db.select(DSL.max(NOTIFICATION.ID)).from(NOTIFICATION).where(mine, NOTIFICATION.ARCHIVED_AT.isNull())
                .fetchOne(0, Long.class);
        int inArchive = archived ? total : db.fetchCount(NOTIFICATION, mine.and(NOTIFICATION.ARCHIVED_AT.isNotNull()));
        return new Page(items, unread(recipientId), at * PAGE < total, total, at, newest, inArchive, groups(mine.and(shelf)));
    }

    private Map<String, Group> groups(Condition where) {
        Map<String, Group> byKind = new java.util.HashMap<>();
        db.select(NOTIFICATION.KIND, DSL.count(), DSL.count().filterWhere(NOTIFICATION.READ_AT.isNull()))
                .from(NOTIFICATION).where(where).groupBy(NOTIFICATION.KIND)
                .forEach(r -> byKind.put(r.value1(), new Group(r.value2(), r.value3())));
        Map<String, Group> groups = new java.util.HashMap<>();
        GROUPS.forEach((name, members) -> groups.put(name, members.stream().map(byKind::get).filter(java.util.Objects::nonNull)
                .reduce(new Group(0, 0), Group::plus)));
        groups.put("all", byKind.values().stream().reduce(new Group(0, 0), Group::plus));
        return groups;
    }

    int unread(long recipientId) {
        return db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(recipientId)
                .and(NOTIFICATION.READ_AT.isNull()).and(NOTIFICATION.ARCHIVED_AT.isNull())
                .and(space.panrid.novelka.access.PublicationNotices.visible(NOTIFICATION.PAYLOAD, NOTIFICATION.RECIPIENT_ID)));
    }

    /** Everything up to {@code upTo} (or everything) counts as seen. */
    void markRead(long recipientId, Long upTo) {
        db.update(NOTIFICATION).set(NOTIFICATION.READ_AT, DSL.currentOffsetDateTime())
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId), NOTIFICATION.READ_AT.isNull(),
                        upTo == null ? DSL.noCondition() : NOTIFICATION.ID.le(upTo))
                .execute();
        nudge(recipientId);
    }

    /**
     * Puts rows away (the given ones, or all of the list). A row put away counts as seen, so new
     * chapters of the same translation start a fresh row in the list instead of widening it.
     */
    void archive(long recipientId, List<Long> ids) {
        db.update(NOTIFICATION).set(NOTIFICATION.ARCHIVED_AT, DSL.currentOffsetDateTime())
                .set(NOTIFICATION.READ_AT, DSL.coalesce(NOTIFICATION.READ_AT, DSL.currentOffsetDateTime()))
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId), NOTIFICATION.ARCHIVED_AT.isNull(), chosen(ids))
                .execute();
        nudge(recipientId);
    }

    /** Brings rows back from the archive (the given ones, or all of it). */
    void restore(long recipientId, List<Long> ids) {
        db.update(NOTIFICATION).setNull(NOTIFICATION.ARCHIVED_AT)
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId), NOTIFICATION.ARCHIVED_AT.isNotNull(), chosen(ids))
                .execute();
    }

    /** Deletes rows for good: the given ones, or all of the list or of the archive. */
    void delete(long recipientId, List<Long> ids, boolean archived) {
        db.deleteFrom(NOTIFICATION)
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId),
                        ids == null ? (archived ? NOTIFICATION.ARCHIVED_AT.isNotNull() : NOTIFICATION.ARCHIVED_AT.isNull()) : chosen(ids))
                .execute();
        nudge(recipientId);
    }

    private static Condition chosen(List<Long> ids) {
        return ids == null ? DSL.noCondition() : NOTIFICATION.ID.in(ids);
    }

    private void nudge(long recipientId) {
        AfterCommit.run(() -> live.send(recipientId, "notifications", Map.of("unread", unread(recipientId))));
    }
}
