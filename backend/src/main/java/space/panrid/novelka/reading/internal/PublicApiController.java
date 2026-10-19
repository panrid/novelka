package space.panrid.novelka.reading.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.autotranslate.GlossaryCards;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.reading.internal.ReadingQueries.EditionRow;
import space.panrid.novelka.reading.internal.ReadingQueries.NovelRow;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public reading API (етап 22.2), {@code /api/v1}: for anyone, or with a token's «read»
 * right. Novels, the catalog with its filters and search, translations, published chapters
 * (never drafts, never the original), volumes, tags and «Хто це» as of a chapter. The same rules
 * as the site's pages: hidden translations are not there, 18+ only for those who confirmed the age.
 * Lists are read by key ({@code after} → {@code next}); every answer has an ETag (304 when it has
 * not changed) and may be cached for a minute — privately when it depends on who asks. How often
 * one may ask is limited per token and per address (the security chain, {@code ApiRateLimits}).
 * The {@code @Operation} and {@code @Parameter} texts describe it to developers (етап 22.4,
 * {@code ApiDescription}); a change that breaks old programs fails {@code ApiDescriptionTests}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Читання", description = "Новели, каталог, переклади, опубліковані глави, томи, теги й «Хто це» — те саме, що бачать читачі на сайті.")
class PublicApiController {

    private static final int PAGE = 20;
    private static final Duration FRESH = Duration.ofSeconds(60);

    private final ReadingQueries queries;
    private final CurrentUser currentUser;
    private final GlossaryCards cards;
    private final JsonMapper json;

    PublicApiController(ReadingQueries queries, CurrentUser currentUser, GlossaryCards cards, JsonMapper json) {
        this.queries = queries;
        this.currentUser = currentUser;
        this.cards = cards;
        this.json = json;
    }

    /**
     * Translations as the catalog lists them, 20 to a page by default (at most 50).
     *
     * @param sort popular, updated, new, title, rating or relevance
     */
    @GetMapping("/catalog")
    @Operation(operationId = "listCatalog", summary = "Каталог",
            description = "Переклади так, як їх показує каталог сайту: пошук, теги й фільтри. Сторінки за ключем — `after` і `next`.")
    ResponseEntity<ApiV1.Page<ApiV1.Translation>> catalog(
            @Parameter(description = "Пошук: назва будь-якою мовою, автор, тег чи назва глави; до 100 символів", example = "маг води")
            @RequestParam(required = false) String q,
            @Parameter(description = "Адреса тегу; можна кілька (до 5) — тоді потрібні всі", example = "fentezi")
            @RequestParam(required = false) List<String> tag,
            @Parameter(description = "Переклади чи оригінальні твори", schema = @Schema(type = "string", defaultValue = "all", allowableValues = {"all", "translation", "original"}))
            @RequestParam(defaultValue = "all") String kind,
            @Parameter(description = "Людський переклад (`human`) чи з ШІ (`machine`)",
                    schema = @Schema(type = "string", defaultValue = "all", allowableValues = {"all", "human", "machine"}))
            @RequestParam(defaultValue = "all") String machine,
            @Parameter(description = "Стан перекладу", schema = @Schema(type = "string", allowableValues = {"ongoing", "completed", "paused", "abandoned"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "Стан оригіналу", schema = @Schema(type = "string", allowableValues = {"ongoing", "completed", "paused"}))
            @RequestParam(required = false) String source,
            @Parameter(description = "Щонайменше стільки опублікованих глав", example = "50")
            @RequestParam(required = false) Integer chaptersFrom,
            @Parameter(description = "Щонайбільше стільки опублікованих глав", example = "500")
            @RequestParam(required = false) Integer chaptersTo,
            @Parameter(description = "Лише ті, що читачі оцінили в середньому на 4 і вище")
            @RequestParam(defaultValue = "false") boolean rated,
            @Parameter(description = "Нові глави за тиждень чи місяць", schema = @Schema(type = "string", allowableValues = {"week", "month"}))
            @RequestParam(required = false) String fresh,
            @Parameter(description = "Порядок: популярні, оновлені, нові, за назвою, за оцінкою, за збігом із пошуком",
                    schema = @Schema(type = "string", defaultValue = "popular", allowableValues = {"popular", "updated", "new", "title", "rating", "relevance"}))
            @RequestParam(defaultValue = "popular") String sort,
            @Parameter(description = "Ключ сторінки — `next` із попередньої відповіді")
            @RequestParam(required = false) String after,
            @Parameter(description = "Скільки записів на сторінці, 1–50") @RequestParam(defaultValue = "" + PAGE) int limit,
            HttpServletRequest request) {
        Views.Found<Views.Card> found = queries.search(
                ReadingController.filter(q, tag, kind, machine, status, source, chaptersFrom, chaptersTo, rated, fresh), sort,
                ReadingController.adult(currentUser.viewer()), null, 1, after == null || after.isBlank() ? null : after,
                Math.max(1, Math.min(limit, 50)));
        return cached(request, new ApiV1.Page<>(found.items().stream().map(PublicApiController::translation).toList(), found.next()));
    }

    @GetMapping("/tags")
    @Operation(operationId = "listTags", summary = "Теги", description = "Теги каталогу й скільки новел кожним позначено, найуживаніші першими.")
    ResponseEntity<List<ApiV1.Tag>> tags(HttpServletRequest request) {
        return cached(request, queries.tags(ReadingController.adult(currentUser.viewer()), 300).stream()
                .map(tag -> new ApiV1.Tag(tag.name(), tag.slug(), tag.novels())).toList());
    }

    /** The novel with every translation the asker may read, the most popular first. */
    @GetMapping("/novels/{slug}")
    @Operation(operationId = "getNovel", summary = "Новела", description = "Новела з усіма перекладами, які можна читати, — найпопулярніший першим.")
    ResponseEntity<ApiV1.Novel> novel(@Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            HttpServletRequest request) {
        NovelRow novel = queries.novel(slug).orElseThrow(ReadingController::noNovel);
        List<EditionRow> editions = ReadingController.visibleEditions(queries, novel, currentUser.viewer());
        List<Views.EditionSummary> summaries = queries.summaries(editions);
        List<String> tags = queries.allTags(novel.id());
        List<ApiV1.Translation> translations = new ArrayList<>();
        for (int i = 0; i < editions.size(); i++) {
            EditionRow edition = editions.get(i);
            Views.EditionSummary summary = summaries.get(i);
            translations.add(new ApiV1.Translation(novel.slug(), edition.teamHandle(), edition.teamName(),
                    edition.title() != null ? edition.title() : novel.title(), novel.author(), summary.coverUrl(), edition.kind(),
                    edition.status(), edition.adult(), edition.chapterCount(), tags, edition.lastPublishedAt(), summary.rating(),
                    summary.ratings()));
        }
        EditionRow first = editions.getFirst();
        return cached(request, new ApiV1.Novel(novel.slug(), first.title() != null ? first.title() : novel.title(), novel.author(),
                ReadingController.origin(novel.source()), novel.language(),
                blocks(queries.readerBlocks(first.description() != null ? first.description() : novel.description())), tags,
                editions.stream().allMatch(EditionRow::adult), translations));
    }

    /** Published chapters in order (or newest first with {@code order=desc}), at most 100 to a page. */
    @GetMapping("/novels/{slug}/translations/{team}/chapters")
    @Operation(operationId = "listChapters", summary = "Глави перекладу",
            description = "Опубліковані глави по порядку (або від нових до старих). Сторінки за ключем — `after` і `next`.")
    ResponseEntity<ApiV1.Page<ApiV1.ChapterEntry>> chapters(
            @Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            @Parameter(description = "Команда перекладу", example = "zoria") @PathVariable String team,
            @Parameter(description = "Від першої (`asc`) чи від найновішої (`desc`)", schema = @Schema(type = "string", defaultValue = "asc", allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "asc") String order,
            @Parameter(description = "Ключ сторінки — `next` із попередньої відповіді") @RequestParam(required = false) String after,
            @Parameter(description = "Скільки глав на сторінці, 1–100") @RequestParam(defaultValue = "" + PAGE) int limit,
            HttpServletRequest request) {
        EditionRow edition = edition(slug, team);
        Views.Page<Views.ChapterRow> page = queries.chapters(edition.id(), order.equals("desc"), 1, Math.max(1, Math.min(limit, 100)),
                null, null, null, number(after));
        return cached(request, new ApiV1.Page<>(page.items().stream().map(row -> new ApiV1.ChapterEntry(row.number(), row.label(),
                row.title(), row.publishedAt(), row.volume() == null ? null : ReadingQueries.volumeTitle(row.volume()))).toList(),
                page.next()));
    }

    @GetMapping("/novels/{slug}/translations/{team}/chapters/{number}")
    @Operation(operationId = "getChapter", summary = "Глава",
            description = "Текст опублікованої глави блоками й сусідні глави. Чернеток і оригіналу тут немає.")
    ResponseEntity<ApiV1.Chapter> chapter(@Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            @Parameter(description = "Команда перекладу", example = "zoria") @PathVariable String team,
            @Parameter(description = "Номер глави", example = "12") @PathVariable int number, HttpServletRequest request) {
        EditionRow edition = edition(slug, team);
        ReadingQueries.ChapterText text = queries.chapter(edition.id(), number)
                .orElseThrow(() -> UserFacingException.notFound("Такої глави немає."));
        Views.VolumeRef volume = ReadingQueries.volumeOf(queries.volumes(edition.id()), text.number());
        NovelRow novel = queries.novel(slug).orElseThrow(ReadingController::noNovel);
        return cached(request, new ApiV1.Chapter(novel.slug(), edition.teamHandle(), text.number(), text.label(), text.title(),
                volume == null ? null : ReadingQueries.volumeTitle(volume), blocks(queries.readerBlocks(text.blocks())),
                queries.neighbour(edition.id(), number, false), queries.neighbour(edition.id(), number, true)));
    }

    /** The volumes that have published chapters. */
    @GetMapping("/novels/{slug}/translations/{team}/volumes")
    @Operation(operationId = "listVolumes", summary = "Томи", description = "Томи перекладу, у яких є опубліковані глави.")
    ResponseEntity<List<ApiV1.Volume>> volumes(@Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            @Parameter(description = "Команда перекладу", example = "zoria") @PathVariable String team, HttpServletRequest request) {
        EditionRow edition = edition(slug, team);
        return cached(request, queries.volumeChoices(edition.id()).stream()
                .map(volume -> new ApiV1.Volume(volume.firstNumber(), volume.lastNumber(), volume.title(), volume.chapters())).toList());
    }

    /** «Хто це»: who or what {@code q} names, with what the novel has told by {@code chapter} — no spoilers. */
    @GetMapping("/novels/{slug}/translations/{team}/who")
    @Operation(operationId = "findWho", summary = "Хто це",
            description = "Кого чи що називає `q` і що новела розповіла про це до глави `chapter` — без спойлерів.")
    ResponseEntity<List<ApiV1.Character>> who(@Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            @Parameter(description = "Команда перекладу", example = "zoria") @PathVariable String team,
            @Parameter(description = "Глава, станом на яку розповідати", example = "12") @RequestParam int chapter,
            @Parameter(description = "Ім'я чи слово з тексту, 1–200 символів", example = "Рьо") @RequestParam String q,
            HttpServletRequest request) {
        if (q.isBlank() || q.length() > 200) {
            throw UserFacingException.badRequest("Запит — від 1 до 200 символів.");
        }
        EditionRow edition = edition(slug, team);
        return cached(request, cards.named(edition.id(), chapter, q, null).stream().map(PublicApiController::character).toList());
    }

    /** The characters met by {@code chapter}, each with what the novel told about them by then. */
    @GetMapping("/novels/{slug}/translations/{team}/characters")
    @Operation(operationId = "listCharacters", summary = "Персонажі",
            description = "Персонажі, що з'явилися до глави `chapter`, і що про кожного відомо на той час; найчастіше згадувані першими.")
    ResponseEntity<List<ApiV1.Character>> characters(
            @Parameter(description = "Адреса новели", example = "mag-vody") @PathVariable String slug,
            @Parameter(description = "Команда перекладу", example = "zoria") @PathVariable String team,
            @Parameter(description = "Глава, станом на яку розповідати", example = "12") @RequestParam int chapter,
            HttpServletRequest request) {
        EditionRow edition = edition(slug, team);
        return cached(request, cards.characters(edition.id(), Math.max(0, chapter), GlossaryCards.Sort.MENTIONS).stream()
                .map(PublicApiController::character).toList());
    }

    // ---- shapes ---------------------------------------------------------------------------------

    private EditionRow edition(String slug, String team) {
        NovelRow novel = queries.novel(slug).orElseThrow(ReadingController::noNovel);
        return ReadingController.pick(ReadingController.visibleEditions(queries, novel, currentUser.viewer()), team);
    }

    private static Integer number(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(after.strip());
        } catch (NumberFormatException error) {
            throw UserFacingException.badRequest("Список змінився — почніть із першої сторінки.");
        }
    }

    private static ApiV1.Translation translation(Views.Card card) {
        return new ApiV1.Translation(card.novelSlug(), card.teamHandle(), card.teamName(), card.title(), card.author(), card.coverUrl(),
                card.kind(), card.status(), card.adult(), card.chapterCount(), card.tags(), card.lastPublishedAt(), card.rating(),
                card.ratings());
    }

    private static ApiV1.Character character(GlossaryCards.Card card) {
        return new ApiV1.Character(card.ukrainian(), card.kind(), card.gender(), card.facts(), card.portraitUrl());
    }

    private static List<ApiV1.Block> blocks(List<Views.ReaderBlock> blocks) {
        return blocks.stream().map(block -> new ApiV1.Block(block.id(), block.type(),
                block.content() == null ? List.of() : block.content().stream().map(span -> new ApiV1.Span(span.text(), span.marks())).toList(),
                block.imageUrl())).toList();
    }

    /**
     * The answer with its ETag and how long it may be kept; 304 when the asker has it already. What
     * depends on who asks (18+ for those who confirmed the age) is cached only privately.
     */
    private <T> ResponseEntity<T> cached(HttpServletRequest request, T body) {
        String etag = "\"" + fingerprint(json.writeValueAsBytes(body)) + "\"";
        Optional<Viewer> viewer = currentUser.viewer();
        CacheControl cache = viewer.isPresent() ? CacheControl.maxAge(FRESH).cachePrivate() : CacheControl.maxAge(FRESH).cachePublic();
        String asked = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (asked != null && (asked.strip().equals("*") || Arrays.stream(asked.split(","))
                .map(String::strip).map(tag -> tag.startsWith("W/") ? tag.substring(2) : tag).anyMatch(etag::equals))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache)
                    .varyBy(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(cache).varyBy(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE).body(body);
    }

    private static String fingerprint(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)).substring(0, 32);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
