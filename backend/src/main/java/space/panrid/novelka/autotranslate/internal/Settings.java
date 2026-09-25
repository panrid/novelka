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
record Settings(Stage analyze, Stage translate, Stage proofread, int segmentChars, long microUsdPerShah, double capFactor) {

    /** A шаг is up to 10 000 characters of the original (рішення 21). */
    static final int CHARS_PER_SHAH = 10_000;

    record Stage(String model, double inputPerMillion, double outputPerMillion, boolean enabled) {

        AiPrice price() {
            return new AiPrice(inputPerMillion, outputPerMillion);
        }
    }

    static Settings defaults() {
        Stage mini = new Stage("openai/gpt-4.1-mini", 0.40, 1.60, true);
        return new Settings(mini, mini, mini, 4_000, 36_000, 3.0);
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
