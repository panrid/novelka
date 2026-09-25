package space.panrid.novelka.autotranslate.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

import space.panrid.novelka.ai.AiPrice;

/**
 * Models and money for autotranslation. A copy goes into every job, so changing them
 * never changes a job already started.
 *
 * @param segmentChars     original characters per translate/proofread request
 * @param microUsdPerShah  what one шаг costs the site, in millionths of a dollar (рішення 22)
 * @param capFactor        a job stops once it spent this many times its quote
 */
record Settings(Stage analyze, Stage translate, Stage proofread, int segmentChars, long microUsdPerShah, double capFactor,
        Long redo) {

    /** A шаг is up to 10 000 characters of the original (рішення 21). */
    static final int CHARS_PER_SHAH = 10_000;

    record Stage(String model, double inputPerMillion, double outputPerMillion, boolean enabled) {

        AiPrice price() {
            return new AiPrice(inputPerMillion, outputPerMillion);
        }
    }

    static Settings defaults() {
        Stage mini = new Stage("openai/gpt-4.1-mini", 0.40, 1.60, true);
        return new Settings(mini, mini, mini, 4_000, 36_000, 3.0, null);
    }

    /**
     * A job asked to do chapters again: its salt changes every request, so answers saved
     * from earlier jobs are not reused and the model really works anew.
     */
    int salt() {
        return redo == null ? 0 : (int) Math.floorMod(redo, 100_000L) * 10;
    }

    Settings withRedo(long salt) {
        return new Settings(analyze, translate, proofread, segmentChars, microUsdPerShah, capFactor, salt);
    }

    /** A person's run (рішення 29): their price of a шаг, and it stops at what it holds. */
    Settings paidBy(long microUsdPerShahOfPeople) {
        return new Settings(analyze, translate, proofread, segmentChars, microUsdPerShahOfPeople, 1.0, redo);
    }

    Settings withModels(Stage analyze, Stage translate, Stage proofread) {
        return new Settings(analyze, translate, proofread, segmentChars, microUsdPerShah, capFactor, redo);
    }

    /**
     * Tokens per 1000 characters of the original, measured on real chapters: analysis reads
     * the whole text once, translation reads and writes it, proofreading reads the original
     * and the draft and writes the text again.
     */
    static final double[][] TOKENS_PER_THOUSAND = {{910, 130}, {1300, 950}, {2050, 990}};

    /** What a chapter of {@code chars} is expected to cost at these models, in millionths of a dollar. */
    long expectedMicroUsd(int chars, boolean analyzeToo, boolean translateToo) {
        double thousands = chars / 1000.0;
        double total = 0;
        if (analyzeToo) {
            total += thousands * (TOKENS_PER_THOUSAND[0][0] * analyze.inputPerMillion() + TOKENS_PER_THOUSAND[0][1] * analyze.outputPerMillion());
        }
        if (translateToo) {
            total += thousands * (TOKENS_PER_THOUSAND[1][0] * translate.inputPerMillion() + TOKENS_PER_THOUSAND[1][1] * translate.outputPerMillion());
            if (proofread.enabled()) {
                total += thousands * (TOKENS_PER_THOUSAND[2][0] * proofread.inputPerMillion()
                        + TOKENS_PER_THOUSAND[2][1] * proofread.outputPerMillion());
            }
        }
        return Math.round(total);
    }

    /** One stage of a chapter at a model's prices: 0 analysis, 1 translation, 2 proofreading. */
    static long stageMicroUsd(int stage, int chars, double inputPerMillion, double outputPerMillion) {
        double thousands = chars / 1000.0;
        return Math.round(thousands * (TOKENS_PER_THOUSAND[stage][0] * inputPerMillion + TOKENS_PER_THOUSAND[stage][1] * outputPerMillion));
    }

    static int shah(int chars) {
        return Math.max(1, (chars + CHARS_PER_SHAH - 1) / CHARS_PER_SHAH);
    }

    BigDecimal usd(long shah) {
        return BigDecimal.valueOf(shah * microUsdPerShah, 6).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal usdOfMicro(long micro) {
        return BigDecimal.valueOf(micro, 6).setScale(4, RoundingMode.HALF_UP);
    }
}
