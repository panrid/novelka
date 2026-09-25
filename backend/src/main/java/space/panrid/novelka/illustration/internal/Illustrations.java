package space.panrid.novelka.illustration.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiImageRequest;
import space.panrid.novelka.ai.AiPicture;
import space.panrid.novelka.ai.AiPrice;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

@Service
class Illustrations {

    static final String SETTINGS_KEY = "illustration.settings";
    static final Set<String> ASPECTS = Set.of("3:4", "16:9", "1:1");
    /** The price of a шаг is autotranslation's (рішення 22); pictures are counted in the same шаги. */
    private static final String SHAH_KEY = "autotranslate.settings";
    private static final long DEFAULT_MICRO_USD_PER_SHAH = 36_000;

    private static final String PROMPT = """
            You write the description an image model will draw from, for an illustration of a web novel.
            You get a fragment of a chapter in Ukrainian. Describe in English, in at most 120 words, one moment of it:
            who is there and how they look (only what the text says or clearly implies), where, what happens, the mood,
            the light and the composition. No names the model cannot know: describe people instead. No dialogue, no text
            in the picture. End with the style given.""";

    private final Ai ai;
    private final Images images;
    private final SiteSettings siteSettings;
    private final JsonMapper json;
    private final DSLContext db;

    Illustrations(Ai ai, Images images, SiteSettings siteSettings, JsonMapper json, DSLContext db) {
        this.ai = ai;
        this.images = images;
        this.siteSettings = siteSettings;
        this.json = json;
        this.db = db;
    }

    IllustrationSettings settings() {
        return siteSettings.json(SETTINGS_KEY).map(value -> json.readValue(value, IllustrationSettings.class))
                .orElseGet(IllustrationSettings::defaults);
    }

    void saveSettings(IllustrationSettings settings, long ownerId) {
        if (settings.model() == null || !settings.model().matches("[a-z0-9._-]+/[a-zA-Z0-9._:-]+")
                || settings.promptModel() == null || !settings.promptModel().matches("[a-z0-9._-]+/[a-zA-Z0-9._:-]+")) {
            throw UserFacingException.badRequest("Перевірте назви моделей (вигляд «постачальник/модель»).");
        }
        if (settings.microUsdPerImage() < 1_000 || settings.microUsdPerImage() > 2_000_000) {
            throw UserFacingException.badRequest("Ціна картинки — від $0,001 до $2.");
        }
        if (settings.style() == null || settings.style().length() > 500) {
            throw UserFacingException.badRequest("Стиль — до 500 знаків.");
        }
        siteSettings.put(SETTINGS_KEY, json.writeValueAsString(settings), ownerId);
    }

    long microUsdPerShah() {
        return siteSettings.json(SHAH_KEY).map(value -> json.readTree(value).path("microUsdPerShah").asLong(DEFAULT_MICRO_USD_PER_SHAH))
                .orElse(DEFAULT_MICRO_USD_PER_SHAH);
    }

    /** Price of one picture in шаги, rounded up: a picture never looks cheaper than it is. */
    int shah(long microUsd) {
        return (int) Math.max(1, (microUsd + microUsdPerShah() - 1) / microUsdPerShah());
    }

    static BigDecimal usd(long microUsd) {
        return BigDecimal.valueOf(microUsd, 6).setScale(3, RoundingMode.HALF_UP);
    }

    /** A description for the artist, written by a text model from the fragment. */
    String describe(String novelTitle, String fragment) {
        String text = fragment(fragment);
        IllustrationSettings settings = settings();
        String user = "Novel: " + novelTitle + "\nStyle: " + settings.style() + "\n\nFragment:\n" + text;
        for (int attempt = 0; attempt < 2; attempt++) {
            AiAnswer answer;
            try {
                answer = ai.ask(new AiRequest(settings.promptModel(), PROMPT, user, "illustration_prompt",
                        Map.of("type", "object", "properties", Map.of("prompt", Map.of("type", "string")),
                                "required", List.of("prompt"), "additionalProperties", false),
                        800, new AiPrice(settings.promptInputPerMillion(), settings.promptOutputPerMillion()),
                        new AiTag(null, null, "illustration_prompt", null), attempt));
            } catch (AiException error) {
                throw UserFacingException.badGateway(error.getMessage());
            }
            try {
                String prompt = json.readTree(answer.content()).path("prompt").asString("").strip();
                if (!prompt.isEmpty()) {
                    return prompt;
                }
            } catch (RuntimeException notJson) {
                // asked once more
            }
        }
        throw UserFacingException.badGateway("Модель не змогла описати сцену. Спробуйте інший фрагмент.");
    }

    record Drawn(long imageId, String url, BigDecimal costUsd, int costShah) {
    }

    Drawn draw(long ownerId, String prompt, String fragment, String aspect, Integer chapter) {
        String description = prompt == null ? "" : prompt.strip();
        if (description.isEmpty() || description.length() > 2_000) {
            throw UserFacingException.badRequest("Опис для художника — від 1 до 2000 знаків.");
        }
        if (aspect == null || !ASPECTS.contains(aspect)) {
            throw UserFacingException.badRequest("Невідома форма картинки.");
        }
        IllustrationSettings settings = settings();
        AiPicture picture;
        try {
            picture = ai.image(new AiImageRequest(settings.model(), description, aspect, settings.microUsdPerImage(),
                    new AiTag(null, chapter, "illustration", null)));
        } catch (AiException error) {
            throw UserFacingException.badGateway(error.getMessage());
        }
        StoredImage stored = images.storeDrawn(ownerId, picture.content(), description,
                fragment == null ? null : fragment(fragment), picture.callId());
        return new Drawn(stored.id(), stored.url(1280), usd(picture.costMicroUsd()), shah(picture.costMicroUsd()));
    }

    record Spent(int pictures, BigDecimal usd, BigDecimal average) {
    }

    /** Pictures drawn in the period and what they really cost (descriptions included). */
    Spent spent(int days) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);
        var total = DSL.sum(DSL.coalesce(AI_CALL.COST_ACTUAL_MUSD, AI_CALL.COST_ESTIMATED_MUSD)).cast(Long.class);
        Long all = db.select(total).from(AI_CALL)
                .where(AI_CALL.STAGE.in("illustration", "illustration_prompt"), AI_CALL.CREATED_AT.gt(since)).fetchOne(0, Long.class);
        int count = db.fetchCount(AI_CALL, AI_CALL.STAGE.eq("illustration").and(AI_CALL.STATE.eq("complete"))
                .and(AI_CALL.ERROR.isNull()).and(AI_CALL.CREATED_AT.gt(since)));
        long sum = all == null ? 0 : all;
        return new Spent(count, usd(sum), count == 0 ? usd(0) : usd(sum / count));
    }

    private static String fragment(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw UserFacingException.badRequest("Виділіть фрагмент глави, який треба намалювати.");
        }
        return text.length() > 4_000 ? text.substring(0, 4_000) : text;
    }
}
