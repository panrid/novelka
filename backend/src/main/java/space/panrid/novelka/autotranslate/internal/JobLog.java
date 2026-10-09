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
    /** Chapters to a page of the journal (owner's rule: lists show 20 rows). */
    static final int CHAPTERS = 20;

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

    /** One page of the journal: {@code chapters} — how many chapters it has in all. */
    record Page(int chapters, List<Event> events) {
    }

    /**
     * Twenty chapters of a run with every event of each, the chapters by number — the last ones
     * first unless {@code oldestFirst}, so a long run shows where it is now on its first page; a chapter's
     * events in the order they happened.
     */
    Page chapters(long jobId, int page, boolean oldestFirst) {
        var number = JOB_EVENT.CHAPTER_NUMBER;
        var ofJob = JOB_EVENT.JOB_ID.eq(jobId);
        int total = db.fetchCount(db.selectDistinct(number).from(JOB_EVENT).where(ofJob));
        var byNumber = oldestFirst ? number.asc() : number.desc();
        List<Integer> shown = db.selectDistinct(number).from(JOB_EVENT).where(ofJob).orderBy(byNumber)
                .limit(CHAPTERS).offset((Math.max(1, page) - 1) * CHAPTERS).fetch(number);
        List<Event> events = db.selectFrom(JOB_EVENT).where(ofJob, number.in(shown)).orderBy(byNumber, JOB_EVENT.ID)
                .fetch(r -> new Event(r.getId(), r.getChapterNumber(), r.getKind(), json.readValue(r.getPayload().data(), MAP),
                        r.getCreatedAt()));
        return new Page(total, events);
    }
}
