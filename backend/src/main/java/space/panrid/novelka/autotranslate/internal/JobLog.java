package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.JOB_EVENT;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a run did, step by step, for the team to read (етап 17). Written as it happens, outside
 * any transaction, so a failed step still leaves what came before it.
 */
@Component
class JobLog {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final int PAGE = 500;

    private final DSLContext db;
    private final JsonMapper json;

    JobLog(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    void add(long jobId, int chapter, String kind, Map<String, ?> payload) {
        db.insertInto(JOB_EVENT).set(JOB_EVENT.JOB_ID, jobId).set(JOB_EVENT.CHAPTER_NUMBER, chapter).set(JOB_EVENT.KIND, kind)
                .set(JOB_EVENT.PAYLOAD, JSONB.valueOf(json.writeValueAsString(payload))).execute();
    }

    record Event(long id, int chapter, String kind, Map<String, Object> payload, OffsetDateTime at) {
    }

    /** @param after the last event the page has; 0 for all */
    List<Event> events(long jobId, long after) {
        return db.selectFrom(JOB_EVENT).where(JOB_EVENT.JOB_ID.eq(jobId), JOB_EVENT.ID.gt(after)).orderBy(JOB_EVENT.ID).limit(PAGE)
                .fetch(r -> new Event(r.getId(), r.getChapterNumber(), r.getKind(), json.readValue(r.getPayload().data(), MAP),
                        r.getCreatedAt()));
    }
}
