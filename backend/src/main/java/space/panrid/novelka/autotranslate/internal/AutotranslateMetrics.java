package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The queue and the pace of autotranslation for Prometheus (план, етап 23, п. 5). Read from the
 * database when Prometheus asks (twice a minute), so every copy of the app tells the same and a
 * restart loses nothing. «Idle» is what the alert «автопереклад стоїть» watches: work is waiting
 * or running, yet nothing has moved for that long.
 */
@Component
class AutotranslateMetrics {

    private static final List<String> ACTIVE = List.of("queued", "running");

    private final DSLContext db;
    private final Clock clock;

    AutotranslateMetrics(DSLContext db, Clock clock, MeterRegistry registry) {
        this.db = db;
        this.clock = clock;
        for (String state : ACTIVE) {
            Gauge.builder("novelka.autotranslate.jobs", this, metrics -> metrics.jobs(state)).tag("state", state)
                    .description("Runs of autotranslation queued or running").register(registry);
        }
        for (String state : List.of("ready", "waiting", "running")) {
            Gauge.builder("novelka.autotranslate.steps", this, metrics -> metrics.steps(state)).tag("state", state)
                    .description("Chapters of active runs: ready to start, waiting to retry, being translated")
                    .register(registry);
        }
        Gauge.builder("novelka.autotranslate.chapters.done.last.hour", this, AutotranslateMetrics::chaptersDoneLastHour)
                .description("Chapters finished in the last hour").register(registry);
        Gauge.builder("novelka.autotranslate.idle.seconds", this, AutotranslateMetrics::idleSeconds)
                .description("Seconds since anything moved while work waits or runs; 0 when there is none")
                .register(registry);
    }

    double jobs(String state) {
        return db.fetchCount(JOB, JOB.STATE.eq(state));
    }

    double steps(String state) {
        OffsetDateTime now = now();
        Condition which = switch (state) {
            case "ready" -> JOB_STEP.STATE.eq("pending").and(JOB_STEP.NOT_BEFORE.le(now));
            case "waiting" -> JOB_STEP.STATE.eq("pending").and(JOB_STEP.NOT_BEFORE.gt(now));
            default -> JOB_STEP.STATE.eq("running");
        };
        return db.fetchCount(JOB_STEP.join(JOB).on(JOB.ID.eq(JOB_STEP.JOB_ID)), JOB.STATE.in(ACTIVE).and(which));
    }

    double chaptersDoneLastHour() {
        return db.fetchCount(JOB_STEP, JOB_STEP.STATE.eq("done").and(JOB_STEP.UPDATED_AT.gt(now().minusHours(1))));
    }

    /**
     * Since the last sign of life of active work: a chapter taken or finished, an answer of the
     * model, a run queued. A chapter waiting for its retry time is not «stalled», so only chapters
     * ready or running count as work.
     */
    double idleSeconds() {
        OffsetDateTime now = now();
        Condition active = JOB.STATE.in(ACTIVE);
        boolean work = db.fetchExists(JOB_STEP.join(JOB).on(JOB.ID.eq(JOB_STEP.JOB_ID)), active.and(
                JOB_STEP.STATE.eq("running").or(JOB_STEP.STATE.eq("pending").and(JOB_STEP.NOT_BEFORE.le(now)))));
        if (!work) {
            return 0;
        }
        OffsetDateTime last = latest(
                db.select(DSL.max(DSL.greatest(JOB.CREATED_AT, DSL.coalesce(JOB.LAST_STEP_AT, JOB.CREATED_AT))))
                        .from(JOB).where(active).fetchOne(0, OffsetDateTime.class),
                db.select(DSL.max(JOB_STEP.UPDATED_AT)).from(JOB_STEP).join(JOB).on(JOB.ID.eq(JOB_STEP.JOB_ID))
                        .where(active).fetchOne(0, OffsetDateTime.class),
                db.select(DSL.max(AI_CALL.FINISHED_AT)).from(AI_CALL).join(JOB).on(JOB.ID.eq(AI_CALL.JOB_ID))
                        .where(active).fetchOne(0, OffsetDateTime.class));
        return last == null ? 0 : Math.max(0, Duration.between(last, now).toSeconds());
    }

    private static OffsetDateTime latest(OffsetDateTime... times) {
        return java.util.Arrays.stream(times).filter(Objects::nonNull).max(OffsetDateTime::compareTo).orElse(null);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
