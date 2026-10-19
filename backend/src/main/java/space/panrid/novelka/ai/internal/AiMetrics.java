package space.panrid.novelka.ai.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Money spent on models and how the calls end, for Prometheus (план, етап 23, п. 5). Read from the
 * call journal when Prometheus asks, so it is the same in every copy of the app and survives a
 * restart. A call still on its way or with a lost answer counts at its estimate: it may be paid.
 */
@Component
class AiMetrics {

    /** «За день» is the owner's day, in Kyiv. */
    static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final DSLContext db;
    private final Clock clock;

    AiMetrics(DSLContext db, Clock clock, MeterRegistry registry) {
        this.db = db;
        this.clock = clock;
        Gauge.builder("novelka.ai.spent.today.usd", this, AiMetrics::spentTodayUsd)
                .description("Dollars spent on models since midnight in Kyiv").register(registry);
        for (String state : List.of("complete", "failed", "uncertain", "pending")) {
            Gauge.builder("novelka.ai.calls.last.hour", this, metrics -> metrics.callsLastHour(state)).tag("state", state)
                    .description("Calls to models made in the last hour, by how they ended").register(registry);
        }
    }

    double spentTodayUsd() {
        OffsetDateTime midnight = LocalDate.now(clock.withZone(KYIV)).atStartOfDay(KYIV).toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
        Long micro = db.select(DSL.sum(DSL.coalesce(AI_CALL.COST_ACTUAL_MUSD, AI_CALL.COST_ESTIMATED_MUSD)).cast(Long.class))
                .from(AI_CALL).where(AI_CALL.CREATED_AT.ge(midnight)).fetchOne(0, Long.class);
        return micro == null ? 0 : micro / 1_000_000.0;
    }

    double callsLastHour(String state) {
        OffsetDateTime hourAgo = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).minusHours(1);
        return db.fetchCount(AI_CALL, AI_CALL.STATE.eq(state).and(AI_CALL.CREATED_AT.gt(hourAgo)));
    }
}
