package space.panrid.novelka.studio.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;

/**
 * The shapes of the Studio's API, {@code /api/v1/studio} (етап 22.3). Kept apart from the Studio's
 * own JSON on purpose: the site's pages may change freely, these change only by adding fields. A
 * translation is named as in the public API — the novel's {@code slug} and its team's handle.
 */
final class StudioApiV1 {

    private StudioApiV1() {
    }

    /** A list read by key: pass {@code next} as {@code after} for the following page; null on the last. */
    record Page<T>(List<T> items, String next) {
    }

    /**
     * A translation the person works on.
     *
     * @param kind     human, machine, mixed or original
     * @param status   ongoing, completed, paused or abandoned
     * @param role     the person's role in the team: owner, translator or editor
     * @param chapters published chapters
     */
    @Schema(name = "StudioTranslation", description = "Переклад, у команді якого ви є.")
    record Translation(String novel, String team, String teamName, String title, String kind, String status, String role,
            int chapters, int pendingSuggestions, int originals) {
    }

    /**
     * A chapter as the team sees it in the list.
     *
     * @param label the number readers see when it is not the position; null shows {@code number}
     * @param draft the person has an unpublished draft of it
     */
    @Schema(name = "StudioChapterEntry", description = "Глава в списку, як її бачить команда.")
    record ChapterEntry(int number, String label, String title, boolean published, boolean draft) {
    }

    /**
     * A chapter's text for the team: what readers see ({@code revision}, null before the first
     * publication) and the person's own draft, if any.
     */
    @Schema(name = "StudioChapter", description = "Текст глави для команди: опублікований і ваша чернетка.")
    record Chapter(int number, String label, String title, Long revision, boolean published, List<TextBlock> blocks, Draft draft) {
    }

    /** @param baseRevision the published revision the draft was started from */
    record Draft(String title, List<TextBlock> blocks, Long baseRevision, OffsetDateTime updatedAt) {
    }

    /**
     * A block of the text: heading, paragraph, preface, afterword, separator or image.
     *
     * @param imageId  the picture of an image block (from «Мої картинки»)
     * @param imageUrl where it is shown; ignored when sent
     */
    record TextBlock(String id, String type, List<TextSpan> content, Long imageId, String imageUrl) {
    }

    /** A run of text; marks are bold, italic, underline, strike. */
    record TextSpan(String text, List<String> marks) {
    }

    /**
     * Text to save as a draft or to publish.
     *
     * @param baseRevision the revision it was edited from: publishing over someone else's newer one is refused (409)
     */
    record Text(String title, List<TextBlock> blocks, Long baseRevision) {
    }

    record Published(int number, long revision) {
    }

    /**
     * What {@code PUT …/chapters/{number}} did (етап 22.10): the chapter created or its draft replaced.
     * The text itself is not sent back — {@code GET …/chapters/{number}} has it.
     */
    @Schema(name = "StudioChapterSaved", description = "Глава створена чи оновлена: текст став вашою чернеткою. Сам текст — у `GET …/chapters/{number}`.")
    record ChapterSaved(
            @Schema(description = "Місце глави, воно ж її адреса: не змінюється ніколи", example = "12") int number,
            @Schema(description = "Номер, який бачать читачі, коли він не збігається з місцем; null — показується місце", example = "11.1")
            String label,
            @Schema(description = "true — главу створено цим запитом; false — вона вже була, замінено вашу чернетку") boolean created,
            @Schema(description = "Чи бачать главу читачі; опублікований текст не змінюється, доки не викличете `POST …/publish`")
            boolean published,
            @Schema(description = "Опублікована редакція, яку бачать читачі; null — ще не опублікована") Long revision,
            SavedDraft draft) {
    }

    /** @param baseRevision the published revision the draft was started from */
    @Schema(name = "StudioSavedDraft", description = "Ваша чернетка глави після збереження.")
    record SavedDraft(String title,
            @Schema(description = "Опублікована редакція, від якої вона йде; публікацію поверх новішої чужої буде відхилено (409)")
            Long baseRevision,
            OffsetDateTime updatedAt) {
    }

    /**
     * A picture of «Мої картинки».
     *
     * @param drawn drawn on the site, not uploaded
     * @param uses  published chapters showing it
     */
    record Picture(long id, String url, String fullUrl, int width, int height, String fileName, boolean drawn, OffsetDateTime createdAt,
            List<PictureUse> uses) {
    }

    record PictureUse(String novel, int chapter) {
    }

    /**
     * Pictures to put into a translation.
     *
     * @param pictures each picture, with its place or without (then its file's name says it: «27-5.png»)
     */
    record Placement(List<Spot> pictures) {
    }

    /** @param after the paragraph the picture goes after; 0 — the very start of the chapter */
    record Spot(Long id, Integer chapter, Integer after) {
    }

    /** @param problem why the picture will not be placed, null when it will */
    record Place(long id, String fileName, Integer chapter, Integer after, String quote, String problem) {
    }

    /** @param skipped the pictures left out, each with its reason */
    record Placed(int chapters, int pictures, List<String> skipped) {
    }

    /**
     * A new work. {@code team}: its handle; none — your own team. For a translation also the name in
     * the original, its language and «Маю право перекладати цей текст».
     */
    @Schema(name = "StudioNewNovel", description = "Новий твір: свій переклад (`human`) чи свій твір (`original`).")
    record NewNovel(
            @Schema(description = "`human` — свій переклад, `original` — свій твір", allowableValues = {"human", "original"},
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "human") String kind,
            @Schema(description = "Назва українською, від 1 до 200 знаків", requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Маг води") String title,
            @Schema(description = "Адреса новели: латинські літери, цифри й дефіси, від 2 до 80 знаків; без неї — з назви",
                    example = "mag-vody") String slug,
            @Schema(description = "Автор; у свого твору без нього — ваш нік") String author,
            @Schema(description = "Опис — абзаци, до 3000 знаків") List<TextBlock> description,
            @Schema(description = "Теги зі списку сайту (`/api/v1/tags`)", example = "[\"Фентезі\"]") List<String> tags,
            @Schema(description = "18+: не видно гостям і тим, хто не підтвердив вік") Boolean adult,
            @Schema(description = "Адреса команди; без неї — ваша особиста команда", example = "panrid") String team,
            @Schema(description = "Мова оригіналу (лише переклад)", allowableValues = {"ja", "en", "zh", "ko"}, example = "ja")
            String language,
            @Schema(description = "Назва в оригіналі (лише переклад), до 300 знаків") String titleOriginal,
            @Schema(description = "«Маю право перекладати цей текст» (лише переклад)") Boolean rightsConfirmed) {
    }

    /** What to change; a field left out (or null) stays as it is. */
    @Schema(name = "StudioNovelChanges", description = "Що змінити; поле, якого немає (чи null), лишається як є.")
    record NovelChanges(
            @Schema(description = "Назва, від 1 до 200 знаків") String title,
            @Schema(description = "Нова адреса; стара й далі веде сюди. Поки переклад у новели один — її змінює власник, "
                    + "інакше адміністрація", example = "mag-vody") String slug,
            @Schema(description = "Автор (у новели, яку ввела ваша команда)") String author,
            @Schema(description = "Опис — абзаци, до 3000 знаків") List<TextBlock> description,
            @Schema(description = "Теги зі списку сайту; порожній список прибирає всі") List<String> tags,
            @Schema(description = "18+") Boolean adult,
            @Schema(description = "Стан: триває, завершено, на паузі, закинуто",
                    allowableValues = {"ongoing", "completed", "paused", "abandoned"}, example = "paused") String status,
            @Schema(description = "До якого дня пауза (`2026-12-01`); порожньо — без дати; діє лише зі станом `paused`",
                    example = "2026-12-01") String pausedUntil,
            @Schema(description = "Читачі можуть завантажити переклад в EPUB") Boolean downloadAllowed) {
    }

    /**
     * A work's data as its team edits it.
     *
     * @param kind     human, machine, mixed or original
     * @param status   ongoing, completed, paused or abandoned
     * @param cover    the cover, 480 wide; null — the generated one shows
     * @param language the original's language (ja, en, zh, ko), when known
     * @param chapters published chapters
     * @param role     your role in the team: owner, translator or editor
     */
    @Schema(name = "StudioNovel", description = "Дані твору, як їх змінює команда.")
    record Novel(String novel, String team, String teamName, String kind, String title, String author, List<TextBlock> description,
            List<String> tags, boolean adult, String status, LocalDate pausedUntil, boolean downloadAllowed, String cover,
            String language, String titleOriginal, int chapters, String role) {
    }

    // ---- conversions ------------------------------------------------------------------------------

    static List<TextBlock> blocks(List<Block> blocks, Map<Long, StoredImage> pictures) {
        return blocks.stream().map(block -> new TextBlock(block.id(), block.type(),
                block.content().stream().map(span -> new TextSpan(span.text(), span.marks() == null ? List.of() : span.marks())).toList(),
                block.imageId(), block.imageId() == null || !pictures.containsKey(block.imageId()) ? null
                        : pictures.get(block.imageId()).url(1280))).toList();
    }

    /** What was sent, as the editor's blocks; the text rules check it where it is saved. */
    static List<Block> blocks(List<TextBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream().map(block -> new Block(block.id(), block.type() == null ? "paragraph" : block.type(),
                block.content() == null ? List.of() : block.content().stream()
                        .map(span -> new Span(span.text() == null ? "" : span.text(), span.marks() == null ? List.of() : span.marks()))
                        .toList(),
                block.imageId(), null)).toList();
    }

    static Picture picture(PictureLibrary.Picture picture) {
        return new Picture(picture.id(), picture.url(), picture.fullUrl(), picture.width(), picture.height(), picture.fileName(),
                picture.drawn(), picture.createdAt(), picture.uses().stream().map(use -> new PictureUse(use.novelSlug(), use.number())).toList());
    }
}
