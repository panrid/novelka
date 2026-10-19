package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AUTOTRANSLATE_PRESET;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.jooq.tables.records.AutotranslatePresetRecord;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Ready sets of models with a judged result, to pick instead of three models. Their steps are
 * priced from real runs: a model that thinks before it answers costs several times what its
 * token prices promise, and a quote from the prices alone would stop the run on its cap.
 */
@Component
class Presets {

    private final DSLContext db;

    Presets(DSLContext db) {
        this.db = db;
    }

    /** The qualities people pick from (етап 19), best first. */
    static final List<String> QUALITIES = List.of("best", "high", "draft");

    /**
     * @param proofread null: no proofreading
     * @param quality   what people get it as: «best», «high» or «draft»; null — the site owner's alone
     */
    record Preset(long id, String name, String summary, BigDecimal rating, String analyze, String translate, String proofread,
            long analyzeMicroUsdPerThousand, long translateMicroUsdPerThousand, long proofreadMicroUsdPerThousand, String quality) {

        Jobs.Models models() {
            return new Jobs.Models(analyze, translate, proofread, proofread != null);
        }

        /**
         * A chapter of {@code chars} at these settings: the measured price of each step the
         * preset still runs, the estimate from token prices for a step changed by hand.
         */
        long expectedMicroUsd(int chars, boolean analyzeToo, boolean translateToo, Settings settings) {
            return expectedMicroUsd(chars, analyzeToo, translateToo, translateToo && settings.proofread().enabled(), settings);
        }

        long expectedMicroUsd(int chars, boolean analyzeToo, boolean translateToo, boolean proofreadToo, Settings settings) {
            double thousands = chars / 1000.0;
            long total = 0;
            if (analyzeToo) {
                total += settings.analyze().model().equals(analyze) ? Math.round(thousands * analyzeMicroUsdPerThousand)
                        : estimate(0, chars, settings.analyze());
            }
            if (translateToo) {
                total += settings.translate().model().equals(translate) ? Math.round(thousands * translateMicroUsdPerThousand)
                        : estimate(1, chars, settings.translate());
            }
            if (proofreadToo) {
                total += settings.proofread().model().equals(proofread) && proofreadMicroUsdPerThousand > 0
                        ? Math.round(thousands * proofreadMicroUsdPerThousand) : estimate(2, chars, settings.proofread());
            }
            return total;
        }

        private static long estimate(int stage, int chars, Settings.Stage model) {
            return Settings.stageMicroUsd(stage, chars, model.inputPerMillion(), model.outputPerMillion());
        }
    }

    List<Preset> list() {
        return db.selectFrom(AUTOTRANSLATE_PRESET).where(AUTOTRANSLATE_PRESET.ACTIVE.isTrue())
                .orderBy(AUTOTRANSLATE_PRESET.POSITION, AUTOTRANSLATE_PRESET.ID).fetch(Presets::preset);
    }

    /** What people pick from, best first: the sets the site owner gave a quality. */
    List<Preset> forPeople() {
        return list().stream().filter(preset -> preset.quality() != null)
                .sorted(java.util.Comparator.comparingInt(preset -> QUALITIES.indexOf(preset.quality()))).toList();
    }

    /** The site owner gives each quality its set, or none (етап 19); a set has one quality at most. */
    @org.springframework.transaction.annotation.Transactional
    void setQualities(Map<String, Long> chosen) {
        if (!QUALITIES.containsAll(chosen.keySet())) {
            throw UserFacingException.badRequest("Невідома якість.");
        }
        List<Long> sets = chosen.values().stream().filter(java.util.Objects::nonNull).toList();
        if (sets.size() != new java.util.HashSet<>(sets).size()) {
            throw UserFacingException.badRequest("Один набір моделей не може бути двома якостями.");
        }
        for (Long id : sets) {
            find(id).orElseThrow(() -> UserFacingException.notFound("Такого набору моделей немає."));
        }
        db.update(AUTOTRANSLATE_PRESET).setNull(AUTOTRANSLATE_PRESET.QUALITY).where(AUTOTRANSLATE_PRESET.QUALITY.in(chosen.keySet())).execute();
        chosen.forEach((quality, id) -> {
            if (id != null) {
                db.update(AUTOTRANSLATE_PRESET).set(AUTOTRANSLATE_PRESET.QUALITY, quality).where(AUTOTRANSLATE_PRESET.ID.eq(id)).execute();
            }
        });
    }

    Optional<Preset> find(long id) {
        return db.selectFrom(AUTOTRANSLATE_PRESET).where(AUTOTRANSLATE_PRESET.ID.eq(id), AUTOTRANSLATE_PRESET.ACTIVE.isTrue())
                .fetchOptional(Presets::preset);
    }

    private static Preset preset(AutotranslatePresetRecord r) {
        return new Preset(r.getId(), r.getName(), r.getSummary(), r.getRating(), r.getAnalyzeModel(), r.getTranslateModel(),
                r.getProofreadModel(), r.getAnalyzeMusdPerKchar(), r.getTranslateMusdPerKchar(), r.getProofreadMusdPerKchar(), r.getQuality());
    }
}
