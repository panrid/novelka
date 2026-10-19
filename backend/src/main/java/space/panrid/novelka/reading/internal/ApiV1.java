package space.panrid.novelka.reading.internal;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The shapes of the public reading API, {@code /api/v1} (етап 22.2). Kept apart from the site's
 * own JSON ({@link Views}) on purpose: the site may change its pages freely, while these change
 * only by adding fields. No internal ids — a novel is its {@code slug}, a translation its team's
 * {@code team} handle — and never the original's text.
 * <p>
 * The {@code @Schema} texts are the API's description for developers (етап 22.4,
 * {@code /api/v1/openapi.json}): Ukrainian, with examples that never change, so the committed
 * snapshot ({@code doc/api/openapi-v1.json}) stays put. Every field is always present, null or not.
 */
final class ApiV1 {

    private ApiV1() {
    }

    /** A list read by key: pass {@code next} as {@code after} for the following page; null on the last. */
    @Schema(description = "Сторінка списку. Щоб отримати наступну, передайте `next` як `after`; на останній сторінці `next` — null.",
            requiredProperties = {"items", "next"})
    record Page<T>(
            @Schema(description = "Записи сторінки") List<T> items,
            @Schema(description = "Ключ наступної сторінки для `after`; null — це остання сторінка", nullable = true,
                    example = "eyJwIjoxMjN9") String next) {
    }

    /**
     * A translation (or an original work) of a novel.
     *
     * @param kind     human, machine, mixed or original
     * @param status   ongoing, completed, paused or abandoned
     * @param chapters published chapters
     * @param rating   readers' average (1–5), null before the first score
     */
    @Schema(description = "Переклад новели (або оригінальний твір) — так, як його показує каталог.",
            requiredProperties = {"novel", "team", "teamName", "title", "author", "coverUrl", "kind", "status", "adult", "chapters",
                    "tags", "lastPublishedAt", "rating", "ratings"})
    record Translation(
            @Schema(description = "Адреса новели (`slug`)", example = "mag-vody") String novel,
            @Schema(description = "Команда перекладу — її адреса; разом із `novel` визначає переклад", example = "zoria") String team,
            @Schema(description = "Назва команди", example = "Зоря") String teamName,
            @Schema(description = "Назва новели в цьому перекладі", example = "Маг води") String title,
            @Schema(description = "Автор оригіналу", nullable = true, example = "Мідзуно Рьо") String author,
            @Schema(description = "Обкладинка", nullable = true, example = "https://novelka.in.ua/media/covers/mag-vody.webp")
            String coverUrl,
            @Schema(description = "Який це переклад", allowableValues = {"human", "machine", "mixed", "original"}, example = "human")
            String kind,
            @Schema(description = "Стан перекладу", allowableValues = {"ongoing", "completed", "paused", "abandoned"},
                    example = "ongoing") String status,
            @Schema(description = "18+: видно лише тим, хто підтвердив вік", example = "false") boolean adult,
            @Schema(description = "Скільки глав опубліковано", example = "128") int chapters,
            @Schema(description = "Теги новели (їхні адреси)", example = "[\"fentezi\", \"magiia\"]") List<String> tags,
            @Schema(description = "Коли опубліковано останню главу", nullable = true, example = "2026-10-01T12:00:00Z")
            OffsetDateTime lastPublishedAt,
            @Schema(description = "Середня оцінка читачів, 1–5; null, доки ніхто не оцінив", nullable = true, example = "4.6")
            Double rating,
            @Schema(description = "Скільки читачів оцінили", example = "37") int ratings) {
    }

    /**
     * @param origin   translation or original
     * @param language the original's language (ja, en…), null when not known
     */
    @Schema(description = "Новела з усіма перекладами, які може читати той, хто питає; спершу найпопулярніший.",
            requiredProperties = {"slug", "title", "author", "origin", "language", "description", "tags", "adult", "translations"})
    record Novel(
            @Schema(description = "Адреса новели", example = "mag-vody") String slug,
            @Schema(description = "Назва (з найпопулярнішого перекладу)", example = "Маг води") String title,
            @Schema(description = "Автор оригіналу", nullable = true, example = "Мідзуно Рьо") String author,
            @Schema(description = "Переклад чи оригінальний твір", allowableValues = {"translation", "original"},
                    example = "translation") String origin,
            @Schema(description = "Мова оригіналу (ja, en, ko, zh…); null, якщо невідома", nullable = true, example = "ja")
            String language,
            @Schema(description = "Опис — блоки тексту, як у главі") List<Block> description,
            @Schema(description = "Теги новели (їхні адреси)", example = "[\"fentezi\", \"magiia\"]") List<String> tags,
            @Schema(description = "18+: усі переклади лише для дорослих", example = "false") boolean adult,
            @Schema(description = "Переклади, спершу найпопулярніший") List<Translation> translations) {
    }

    /**
     * A chapter in the list.
     *
     * @param label  the number readers see when it is not the position («12.5», «Екстра»); null shows {@code number}
     * @param volume «Том 2. Назва», null before the first volume
     */
    @Schema(description = "Глава в списку глав перекладу.", requiredProperties = {"number", "label", "title", "publishedAt", "volume"})
    record ChapterEntry(
            @Schema(description = "Номер глави — її місце в перекладі й адреса", example = "12") int number,
            @Schema(description = "Номер, який бачать читачі, коли він інший («12.5», «Екстра»); null — показується `number`",
                    nullable = true, example = "Екстра") String label,
            @Schema(description = "Назва глави", nullable = true, example = "Нічний ринок") String title,
            @Schema(description = "Коли опубліковано", nullable = true, example = "2026-10-01T12:00:00Z") OffsetDateTime publishedAt,
            @Schema(description = "Том, якому належить глава; null — до першого тому", nullable = true, example = "Том 2. Пустеля")
            String volume) {
    }

    /** A published chapter's text; {@code previous} and {@code next} are the neighbouring published numbers. */
    @Schema(description = "Текст опублікованої глави (оригіналу тут немає ніколи).",
            requiredProperties = {"novel", "team", "number", "label", "title", "volume", "blocks", "previous", "next"})
    record Chapter(
            @Schema(description = "Адреса новели", example = "mag-vody") String novel,
            @Schema(description = "Команда перекладу", example = "zoria") String team,
            @Schema(description = "Номер глави", example = "12") int number,
            @Schema(description = "Номер, який бачать читачі, коли він інший; null — показується `number`", nullable = true,
                    example = "Екстра") String label,
            @Schema(description = "Назва глави", nullable = true, example = "Нічний ринок") String title,
            @Schema(description = "Том; null — до першого тому", nullable = true, example = "Том 2. Пустеля") String volume,
            @Schema(description = "Текст глави блоками") List<Block> blocks,
            @Schema(description = "Номер попередньої опублікованої глави; null — це перша", nullable = true, example = "11")
            Integer previous,
            @Schema(description = "Номер наступної опублікованої глави; null — це остання", nullable = true, example = "13")
            Integer next) {
    }

    /**
     * A block of the text: heading, paragraph, preface, afterword, separator or image.
     *
     * @param imageUrl the picture of an image block
     */
    @Schema(description = "Блок тексту: заголовок, абзац, передмова, післямова, розділювач чи картинка.",
            requiredProperties = {"id", "type", "content", "imageUrl"})
    record Block(
            @Schema(description = "Позначка блоку в главі (для посилань на абзац)", nullable = true, example = "b7") String id,
            @Schema(description = "Вид блоку", allowableValues = {"heading", "paragraph", "preface", "afterword", "separator", "image"},
                    example = "paragraph") String type,
            @Schema(description = "Текст блоку відрізками; порожній у розділювача й картинки") List<Span> content,
            @Schema(description = "Картинка блоку `image`; null в інших", nullable = true,
                    example = "https://novelka.in.ua/media/pictures/rynok.webp") String imageUrl) {
    }

    /** A run of text; marks are bold, italic, underline, strike. */
    @Schema(description = "Відрізок тексту з однаковим оформленням.", requiredProperties = {"text", "marks"})
    record Span(
            @Schema(description = "Текст", example = "Ліхтарі спалахнули.") String text,
            @Schema(description = "Оформлення: bold, italic, underline, strike; порожньо чи null — звичайний текст", nullable = true,
                    example = "[\"italic\"]") List<String> marks) {
    }

    /** @param lastNumber the last chapter number it may hold; null for the last volume */
    @Schema(description = "Том, у якому є опубліковані глави.", requiredProperties = {"firstNumber", "lastNumber", "title", "chapters"})
    record Volume(
            @Schema(description = "Номер першої глави тому", example = "41") int firstNumber,
            @Schema(description = "Останній номер глави, що може належати тому; null — це останній том", nullable = true,
                    example = "80") Integer lastNumber,
            @Schema(description = "Назва тому", example = "Том 2. Пустеля") String title,
            @Schema(description = "Скільки опублікованих глав у томі", example = "40") int chapters) {
    }

    @Schema(description = "Тег і скільки новел ним позначено.", requiredProperties = {"name", "slug", "novels"})
    record Tag(
            @Schema(description = "Назва тегу", example = "Фентезі") String name,
            @Schema(description = "Адреса тегу — її передають у `tag` каталогу", example = "fentezi") String slug,
            @Schema(description = "Скільки новел із цим тегом", example = "214") int novels) {
    }

    /**
     * «Хто це»: a name or a term of the translation's glossary, with what the novel told by the chapter asked about.
     *
     * @param kind character, place, organization, term or other
     */
    @Schema(description = "«Хто це»: ім'я чи термін зі словника перекладу й те, що новела розповіла про нього до вказаної глави — без спойлерів.",
            requiredProperties = {"name", "kind", "gender", "facts", "portraitUrl"})
    record Character(
            @Schema(description = "Ім'я чи термін українською", example = "Рьо") String name,
            @Schema(description = "Що це", allowableValues = {"character", "place", "organization", "term", "other"},
                    example = "character") String kind,
            @Schema(description = "Рід персонажа: male, female чи unknown; null, якщо не вказано", nullable = true, example = "male")
            String gender,
            @Schema(description = "Що відомо станом на главу", example = "[\"Учень мага води\"]") List<String> facts,
            @Schema(description = "Портрет; null, якщо його немає", nullable = true,
                    example = "https://novelka.in.ua/media/pictures/ryo.webp") String portraitUrl) {
    }
}
