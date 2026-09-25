package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.BadOutput;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.Cancelled;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.OverBudget;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.jooq.tables.records.JobStepRecord;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Takes the next chapter of any job and runs it. Within a job chapters go strictly in
 * order (a chapter needs the previous one's summary and glossary); different jobs share
 * the worker. A step is leased: if the process dies, the lease runs out and the step is
 * picked up again from its checkpoint.
 */
@Component
class Worker {

    static final int LEASE_MINUTES = 20;
    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    /** Pauses before retrying a failure that certainly cost nothing. */
    private static final List<Integer> BACKOFF_SECONDS = List.of(30, 120, 300, 900, 1800, 3600);

    private final DSLContext db;
    private final Pipeline pipeline;
    private final Clock clock;

    Worker(DSLContext db, Pipeline pipeline, Clock clock) {
        this.db = db;
        this.pipeline = pipeline;
        this.clock = clock;
    }

    /** Runs steps until none is ready. Returns how many ran. */
    int drain() {
        int ran = 0;
        releaseExpiredLeases();
        JobStepRecord step;
        while ((step = claim()) != null) {
            process(step);
            ran++;
        }
        return ran;
    }

    JobStepRecord claim() {
        OffsetDateTime now = now();
        var s = JOB_STEP.as("s");
        var earlier = JOB_STEP.as("e");
        Long id = db.select(s.ID).from(s).join(JOB).on(JOB.ID.eq(s.JOB_ID))
                .where(JOB.STATE.in("queued", "running"), s.STATE.eq("pending"), s.NOT_BEFORE.le(now))
                .andNotExists(DSL.selectOne().from(earlier)
                        .where(earlier.JOB_ID.eq(s.JOB_ID), earlier.CHAPTER_NUMBER.lt(s.CHAPTER_NUMBER),
                                earlier.STATE.ne("done")))
                .orderBy(JOB.CREATED_AT, s.CHAPTER_NUMBER)
                .limit(1)
                .forUpdate().of(s).skipLocked()
                .fetchOne(s.ID);
        if (id == null) {
            return null;
        }
        JobStepRecord step = db.update(JOB_STEP)
                .set(JOB_STEP.STATE, "running")
                .set(JOB_STEP.ATTEMPTS, JOB_STEP.ATTEMPTS.plus(1))
                .set(JOB_STEP.NOT_BEFORE, now.plusMinutes(LEASE_MINUTES))
                .set(JOB_STEP.UPDATED_AT, now)
                .where(JOB_STEP.ID.eq(id), JOB_STEP.STATE.eq("pending"))
                .returning().fetchOne();
        if (step != null) {
            db.update(JOB).set(JOB.STATE, "running").set(JOB.STARTED_AT, DSL.coalesce(JOB.STARTED_AT, DSL.val(now)))
                    .where(JOB.ID.eq(step.getJobId()), JOB.STATE.eq("queued")).execute();
        }
        return step;
    }

    void process(JobStepRecord step) {
        JobRecord job = db.selectFrom(JOB).where(JOB.ID.eq(step.getJobId())).fetchSingle();
        try {
            pipeline.run(job, step);
            finishStep(step, "done", null, null);
            finishJobIfComplete(job.getId());
        } catch (Cancelled cancelled) {
            finishStep(step, "cancelled", null, null);
        } catch (AiException error) {
            switch (error.kind()) {
                case UNPAID -> retryLater(step, job, error.getMessage());
                case FAILED -> fail(step, job, error.getMessage(), "failed");
                case UNCERTAIN -> fail(step, job, error.getMessage()
                        + " Перевірте баланс і натисніть «Продовжити», щоб дозволити нову спробу.", "uncertain");
            }
        } catch (BadOutput | OverBudget error) {
            fail(step, job, error.getMessage(), error instanceof OverBudget ? "budget" : "output");
        } catch (UserFacingException error) {
            // Syosetu: a busy site is worth another try, a missing chapter is not.
            if (error.status().is5xxServerError()) {
                retryLater(step, job, error.getMessage());
            } else {
                fail(step, job, error.getMessage(), "source");
            }
        } catch (RuntimeException error) {
            log.error("Job {} chapter {} failed", job.getId(), step.getChapterNumber(), error);
            fail(step, job, "Непередбачена помилка на главі %d. Подробиці в журналі сервера.".formatted(step.getChapterNumber()),
                    "internal");
        }
    }

    private void retryLater(JobStepRecord step, JobRecord job, String message) {
        int attempts = db.select(JOB_STEP.ATTEMPTS).from(JOB_STEP).where(JOB_STEP.ID.eq(step.getId())).fetchSingle().value1();
        if (attempts > BACKOFF_SECONDS.size()) {
            fail(step, job, message + " Спроби вичерпано.", "unpaid");
            return;
        }
        int pause = BACKOFF_SECONDS.get(Math.min(attempts, BACKOFF_SECONDS.size()) - 1);
        db.update(JOB_STEP)
                .set(JOB_STEP.STATE, "pending")
                .set(JOB_STEP.NOT_BEFORE, now().plusSeconds(pause))
                .set(JOB_STEP.ERROR, message + " Спробуємо ще раз автоматично.")
                .set(JOB_STEP.ERROR_REASON, "retry")
                .set(JOB_STEP.UPDATED_AT, now())
                .where(JOB_STEP.ID.eq(step.getId())).execute();
    }

    private void fail(JobStepRecord step, JobRecord job, String message, String reason) {
        finishStep(step, "failed", message, reason);
        db.update(JOB).set(JOB.STATE, "failed").set(JOB.ERROR, message).set(JOB.FINISHED_AT, now())
                .where(JOB.ID.eq(job.getId()), JOB.STATE.in("queued", "running")).execute();
    }

    private void finishStep(JobStepRecord step, String state, String error, String reason) {
        db.update(JOB_STEP)
                .set(JOB_STEP.STATE, state)
                .set(JOB_STEP.ERROR, error)
                .set(JOB_STEP.ERROR_REASON, reason)
                .set(JOB_STEP.NOT_BEFORE, now())
                .set(JOB_STEP.UPDATED_AT, now())
                .where(JOB_STEP.ID.eq(step.getId())).execute();
    }

    private void finishJobIfComplete(long jobId) {
        boolean open = db.fetchExists(JOB_STEP, JOB_STEP.JOB_ID.eq(jobId).and(JOB_STEP.STATE.ne("done")));
        if (!open) {
            db.update(JOB).set(JOB.STATE, "done").set(JOB.FINISHED_AT, now()).set(JOB.ERROR, (String) null)
                    .where(JOB.ID.eq(jobId), JOB.STATE.eq("running")).execute();
        }
    }

    /** A step whose worker died goes back to the queue; its checkpoint keeps what was done. */
    void releaseExpiredLeases() {
        db.update(JOB_STEP).set(JOB_STEP.STATE, "pending")
                .where(JOB_STEP.STATE.eq("running"), JOB_STEP.NOT_BEFORE.lt(now())).execute();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** The schedule; switched off in tests, which call {@link #drain()} themselves. */
    @Component
    @ConditionalOnProperty(name = "novelka.autotranslate.worker", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final Worker worker;

        Schedule(Worker worker) {
            this.worker = worker;
        }

        @Scheduled(initialDelay = 10_000, fixedDelay = 3_000)
        void tick() {
            try {
                worker.drain();
            } catch (RuntimeException error) {
                log.error("Autotranslate worker tick failed", error);
            }
        }
    }
}
