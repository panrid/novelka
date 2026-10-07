package space.panrid.novelka.notification.internal;

import static space.panrid.novelka.jooq.Tables.NOTIFICATION;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Writing and reading inbox rows; every change nudges the recipient's open tabs. */
@Component
class Inbox {

    private static final int PAGE = 20;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;
    private final LiveEvents live;

    Inbox(DSLContext db, JsonMapper json, LiveEvents live) {
        this.db = db;
        this.json = json;
        this.live = live;
    }

    void add(long recipientId, String kind, Map<String, Object> payload) {
        db.insertInto(NOTIFICATION).set(NOTIFICATION.RECIPIENT_ID, recipientId).set(NOTIFICATION.KIND, kind)
                .set(NOTIFICATION.PAYLOAD, JSONB.valueOf(json.writeValueAsString(payload))).execute();
        nudge(recipientId);
    }

    /**
     * One unread row per group: new chapters of a translation widen the range of the row
     * the reader has not seen yet instead of piling up.
     */
    void addGrouped(long recipientId, String kind, String groupKey, Map<String, Object> payload, int first, int last) {
        Map<String, Object> fresh = new java.util.HashMap<>(payload);
        fresh.put("first", first);
        fresh.put("last", last);
        // One statement, so chapters published at the same moment widen the row instead of racing.
        db.execute("""
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
                """, recipientId, kind, groupKey, json.writeValueAsString(fresh));
        nudge(recipientId);
    }

    /** One unread row per group whose «count» grows: suggestions sent to a team one batch after another. */
    void addCounted(long recipientId, String kind, String groupKey, Map<String, Object> payload, int count) {
        Map<String, Object> fresh = new java.util.HashMap<>(payload);
        fresh.put("count", count);
        db.execute("""
                INSERT INTO notification (recipient_id, kind, group_key, payload) VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (recipient_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
                DO UPDATE SET payload = EXCLUDED.payload || jsonb_build_object(
                        'count', (notification.payload ->> 'count')::int + (EXCLUDED.payload ->> 'count')::int),
                    created_at = now()
                """, recipientId, kind, groupKey, json.writeValueAsString(fresh));
        nudge(recipientId);
    }

    record Item(long id, String kind, Map<String, Object> payload, OffsetDateTime createdAt, boolean read) {
    }

    /** @param newest the newest row of all (not of this page): opening the list marks up to it as seen */
    record Page(List<Item> items, int unread, boolean hasMore, int total, int page, Long newest) {
    }

    Page page(long recipientId, int page) {
        int at = Math.max(1, page);
        int total = db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(recipientId));
        List<Item> items = db.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId))
                .orderBy(NOTIFICATION.ID.desc()).limit(PAGE).offset((at - 1) * PAGE)
                .fetch(r -> new Item(r.getId(), r.getKind(), json.readValue(r.getPayload().data(), MAP), r.getCreatedAt(), r.getReadAt() != null));
        Long newest = db.select(DSL.max(NOTIFICATION.ID)).from(NOTIFICATION).where(NOTIFICATION.RECIPIENT_ID.eq(recipientId))
                .fetchOne(0, Long.class);
        return new Page(items, unread(recipientId), at * PAGE < total, total, at, newest);
    }

    int unread(long recipientId) {
        return db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(recipientId).and(NOTIFICATION.READ_AT.isNull()));
    }

    /** Everything up to {@code upTo} (or everything) counts as seen. */
    void markRead(long recipientId, Long upTo) {
        db.update(NOTIFICATION).set(NOTIFICATION.READ_AT, DSL.currentOffsetDateTime())
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId), NOTIFICATION.READ_AT.isNull(),
                        upTo == null ? DSL.noCondition() : NOTIFICATION.ID.le(upTo))
                .execute();
        nudge(recipientId);
    }

    private void nudge(long recipientId) {
        AfterCommit.run(() -> live.send(recipientId, "notifications", Map.of("unread", unread(recipientId))));
    }
}
