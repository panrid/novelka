package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.JOB;

import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.live.LiveEvents;

/** Tells the person who started a job that it moved, so the page updates without polling. */
@Component
class Progress {

    private final DSLContext db;
    private final LiveEvents live;

    Progress(DSLContext db, LiveEvents live) {
        this.db = db;
        this.live = live;
    }

    void changed(long jobId) {
        var job = db.select(JOB.REQUESTED_BY, JOB.EDITION_ID).from(JOB).where(JOB.ID.eq(jobId)).fetchOne();
        if (job != null) {
            live.send(job.value1(), "job", Map.of("editionId", job.value2()));
        }
    }
}
