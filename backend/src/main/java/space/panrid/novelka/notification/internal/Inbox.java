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

    private static final int PAGE = 30;
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
                DO UPDATE SET payload = notification.payload || jsonb_build_object(
                        'first', LEAST((notification.payload ->> 'first')::int, (EXCLUDED.payload ->> 'first')::int),
                        'last', GREATEST((notification.payload ->> 'last')::int, (EXCLUDED.payload ->> 'last')::int)),
                    created_at = now()
                """, recipientId, kind, groupKey, json.writeValueAsString(fresh));
        nudge(recipientId);
    }

    record Item(long id, String kind, Map<String, Object> payload, OffsetDateTime createdAt, boolean read) {
    }

    record Page(List<Item> items, int unread, boolean hasMore) {
    }

    Page page(long recipientId, Long before) {
        List<Item> items = db.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.RECIPIENT_ID.eq(recipientId), before == null ? DSL.noCondition() : NOTIFICATION.ID.lt(before))
                .orderBy(NOTIFICATION.ID.desc()).limit(PAGE + 1)
                .fetch(r -> new Item(r.getId(), r.getKind(), json.readValue(r.getPayload().data(), MAP), r.getCreatedAt(), r.getReadAt() != null));
        boolean more = items.size() > PAGE;
        return new Page(more ? items.subList(0, PAGE) : items, unread(recipientId), more);
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
