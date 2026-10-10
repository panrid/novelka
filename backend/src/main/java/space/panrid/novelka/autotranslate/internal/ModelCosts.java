package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * What a model really cost per stage on this site, per 1000 characters of the original. Token
 * prices promise less than models that think before answering spend (Grok 4.3: three times),
 * so a price measured on enough chapters is shown instead of the estimate.
 */
@Component
class ModelCosts {

    /** The stages by their number: 0 analysis, 1 translation, 2 proofreading (as ai_call names them). */
    static final List<String> STAGES = List.of("analyze", "translate", "proofread");

    /** Chapters a model must have done in a stage before its measured price is believed. */
    static final int ENOUGH = 3;
    private static final Duration FRESH = Duration.ofMinutes(10);

    record Measured(long microUsdPerThousand, int chapters) {

        long chapter(int chars) {
            return Math.round(microUsdPerThousand * chars / 1000.0);
        }
    }

    private final DSLContext db;
    private final Clock clock;
    private Map<String, Measured> cached = Map.of();
    private Instant cachedAt = Instant.EPOCH;

    ModelCosts(DSLContext db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** @param stage «analyze», «translate» or «proofread» */
    Optional<Measured> of(String model, String stage) {
        return Optional.ofNullable(all().get(model + " " + stage));
    }

    /** A chapter of {@code chars} at this stage: measured when known, else from token prices. */
    long chapterMicroUsd(int stage, int chars, Settings.Stage model) {
        return of(model.model(), STAGES.get(stage)).map(found -> found.chapter(chars))
                .orElseGet(() -> Settings.stageMicroUsd(stage, chars, model.inputPerMillion(), model.outputPerMillion()));
    }

    /** The steps of a chapter, each measured or estimated. */
    long chapterMicroUsd(Settings settings, int chars, boolean analyze, boolean translate, boolean proofread) {
        return (analyze ? chapterMicroUsd(0, chars, settings.analyze()) : 0)
                + (translate ? chapterMicroUsd(1, chars, settings.translate()) : 0)
                + (proofread ? chapterMicroUsd(2, chars, settings.proofread()) : 0);
    }

    private synchronized Map<String, Measured> all() {
        Instant now = clock.instant();
        if (Duration.between(cachedAt, now).compareTo(FRESH) < 0) {
            return cached;
        }
        // One row per chapter of a job and stage: its cost and the length of its original.
        var perChapter = DSL.select(AI_CALL.MODEL, AI_CALL.STAGE, AI_CALL.JOB_ID, AI_CALL.CHAPTER_NUMBER,
                        DSL.sum(AI_CALL.COST_ACTUAL_MUSD).as("cost"), DSL.max(SOURCE_CHAPTER.CHARS).as("chars"))
                .from(AI_CALL).join(JOB).on(JOB.ID.eq(AI_CALL.JOB_ID)).join(EDITION).on(EDITION.ID.eq(JOB.EDITION_ID))
                .join(SOURCE_CHAPTER).on(SOURCE_CHAPTER.NOVEL_ID.eq(EDITION.NOVEL_ID), SOURCE_CHAPTER.NUMBER.eq(AI_CALL.CHAPTER_NUMBER))
                .where(AI_CALL.STAGE.in(STAGES), AI_CALL.COST_ACTUAL_MUSD.isNotNull(), AI_CALL.STATE.eq("complete"))
                .groupBy(AI_CALL.MODEL, AI_CALL.STAGE, AI_CALL.JOB_ID, AI_CALL.CHAPTER_NUMBER).asTable("per_chapter");
        Map<String, Measured> found = new HashMap<>();
        db.select(perChapter.field(AI_CALL.MODEL), perChapter.field(AI_CALL.STAGE), DSL.sum(perChapter.field("cost", Long.class)),
                        DSL.sum(perChapter.field("chars", Integer.class)), DSL.count())
                .from(perChapter).groupBy(perChapter.field(AI_CALL.MODEL), perChapter.field(AI_CALL.STAGE))
                .forEach(row -> {
                    int chapters = row.value5();
                    long chars = row.value4() == null ? 0 : row.value4().longValue();
                    if (chapters >= ENOUGH && chars > 0) {
                        long perThousand = Math.round(row.value3().doubleValue() * 1000.0 / chars);
                        found.put(row.value1() + " " + row.value2(), new Measured(perThousand, chapters));
                    }
                });
        cached = found;
        cachedAt = now;
        return found;
    }

    /** For tests: the next question reads the calls again. */
    synchronized void forget() {
        cachedAt = Instant.EPOCH;
    }
}
