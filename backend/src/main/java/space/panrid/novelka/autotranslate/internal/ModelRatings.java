package space.panrid.novelka.autotranslate.internal;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How fit a model is for Japanese → Ukrainian literary translation, judged by its id. The
 * catalogue has hundreds of models; this keeps the ones that cannot translate out of sight.
 */
final class ModelRatings {

    enum Rating {
        /** Known to handle Japanese and write good Ukrainian. */
        RECOMMENDED,
        /** Nothing known against it. */
        USUAL,
        /** Tolerable but weak: small models, free tiers with tight limits. Shown on request. */
        WEAK,
        /** Not for translation at all: never offered, refused when typed in. */
        AWFUL
    }

    private ModelRatings() {
    }

    private static final Pattern RECOMMENDED = Pattern.compile(
            "anthropic/claude-(sonnet|opus)-(4\\.[5-9]|[5-9])(\\.\\d+)?"
                    + "|google/gemini-(2\\.5|[3-9]\\.\\d+)-(pro|flash)"
                    + "|openai/gpt-4\\.1(-mini)?"
                    + "|openai/gpt-5(\\.\\d+)?"
                    + "|deepseek/deepseek-v3\\.\\d+");

    /**
     * Code, role-play, moderation and routers: none of them is a translator. «:batch» variants
     * answer only through OpenRouter's batch API, never in a chat.
     */
    private static final Pattern AWFUL = Pattern.compile(
            "coder|codex|codestral|devstral|-code\\b|guard|moderat|:batch$"
                    + "|mytho|euryale|magnum|cydonia|skyfall|unslop|rocinante|lumimaid|remm-slerp|anubis|roleplay"
                    + "|^openrouter/");

    /** Models made for translation stay usable however small they are. */
    private static final Pattern TRANSLATOR = Pattern.compile("-mt\\d*-|translat");

    /** The size in billions of parameters, the first «NNb» in the id (a mixture of experts gives its total). */
    private static final Pattern SIZE = Pattern.compile("(?<![\\d.a-z])(\\d+(?:\\.\\d+)?)b(?![a-z])");

    static Rating of(String id) {
        String model = id.toLowerCase(Locale.ROOT);
        String base = model.replaceFirst(":.*$", "");
        if (AWFUL.matcher(model).find()) {
            return Rating.AWFUL;
        }
        boolean translator = TRANSLATOR.matcher(model).find();
        Double size = size(base);
        if (!translator && size != null && size <= 4) {
            return Rating.AWFUL;
        }
        if (model.endsWith(":free") || (!translator && size != null && size <= 14) || base.contains("nano")) {
            return Rating.WEAK;
        }
        return RECOMMENDED.matcher(base).matches() ? Rating.RECOMMENDED : Rating.USUAL;
    }

    private static Double size(String model) {
        Matcher found = SIZE.matcher(model.substring(model.indexOf('/') + 1));
        return found.find() ? Double.valueOf(found.group(1)) : null;
    }
}
