package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL;
import static space.panrid.novelka.jooq.Tables.EDITOR_DRAFT;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.security.TokenGrant;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.Idempotency;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.Teams;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Studio's API (етап 22.3), {@code /api/v1/studio}: the translations a person works on, their
 * chapters with drafts and publication, and «Мої картинки» — uploaded and put into chapters. With a
 * token it needs the «studio» right; whatever the token allows, the team's rules decide as on the
 * site ({@link AccessPolicy}): everyone in the team edits text, owners and translators add pictures.
 * Autotranslation, the original and the glossary are the autotranslation module's part of this API,
 * правки — the suggestion module's.
 */
@RestController
@RequestMapping("/api/v1/studio")
@Tag(name = "Студія", description = "Переклади, де ви в команді: глави, чернетки, публікація, «Мої картинки». Право токена — `studio`.")
class StudioApiController {

    private static final int PAGE = 20;

    private final AccessPolicy access;
    private final Teams teams;
    private final Chapters chapters;
    private final Images images;
    private final PictureLibrary library;
    private final DSLContext db;
    private final Idempotency idempotency;
    private final JsonMapper json;

    StudioApiController(AccessPolicy access, Teams teams, Chapters chapters, Images images, PictureLibrary library, DSLContext db,
            Idempotency idempotency, JsonMapper json) {
        this.idempotency = idempotency;
        this.json = json;
        this.access = access;
        this.teams = teams;
        this.chapters = chapters;
        this.images = images;
        this.library = library;
        this.db = db;
    }

    // ---- translations -------------------------------------------------------------------------

    /**
     * The translations of the teams the person is in, the newest first, 20 to a page (at most 100);
     * for a token limited to one translation (етап 22.12) — that one alone.
     */
    @GetMapping("/translations")
    @Operation(summary = "Мої переклади", description = "Переклади команд, де ви є, — найновіші першими. Токен, обмежений одним перекладом, бачить лише його.")
    StudioApiV1.Page<StudioApiV1.Translation> translations(@RequestParam(required = false) String after,
            @RequestParam(defaultValue = "" + PAGE) int limit, HttpServletRequest request) {
        Viewer viewer = access.requireSignedIn();
        int size = size(limit, 100);
        Long from = id(after);
        Long only = TokenGrant.edition(request);
        var member = DSL.select(TEAM_MEMBER.TEAM_ID).from(TEAM_MEMBER).where(TEAM_MEMBER.ACCOUNT_ID.eq(viewer.accountId()));
        var rows = db.select(EDITION.ID).from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(TEAM.OWNER_ID.eq(viewer.accountId()).or(TEAM.ID.in(member)), EDITION.DELETED_AT.isNull(),
                        from == null ? DSL.noCondition() : EDITION.ID.lt(from), only == null ? DSL.noCondition() : EDITION.ID.eq(only))
                .orderBy(EDITION.ID.desc()).limit(size).fetch(EDITION.ID);
        List<StudioApiV1.Translation> items = new ArrayList<>();
        for (long editionId : rows) {
            translation(editionId, viewer.accountId()).ifPresent(items::add);
        }
        return new StudioApiV1.Page<>(items, rows.size() == size ? String.valueOf(rows.getLast()) : null);
    }

    @GetMapping("/novels/{novel}/translations/{team}")
    @Operation(summary = "Переклад", description = "Переклад і ваша роль у його команді.")
    StudioApiV1.Translation translation(@PathVariable String novel, @PathVariable String team) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return translation(who.editionId(), who.viewer().accountId()).orElseThrow();
    }

    private java.util.Optional<StudioApiV1.Translation> translation(long editionId, long accountId) {
        var pending = DSL.select(DSL.count()).from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(EDITION.ID).and(SUGGESTION.STATE.eq("pending"))).<Integer>asField("pending");
        var originals = DSL.select(DSL.count()).from(EDITION_ORIGINAL).where(EDITION_ORIGINAL.EDITION_ID.eq(EDITION.ID))
                .<Integer>asField("originals");
        return db.select(NOVEL.SLUG, TEAM.HANDLE, TEAM.NAME, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), EDITION.KIND, EDITION.STATUS,
                        EDITION.CHAPTER_COUNT, TEAM.ID, pending, originals)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.ID.eq(editionId)).fetchOptional()
                .flatMap(r -> teams.roleOf(r.value8(), accountId).map(role -> new StudioApiV1.Translation(r.value1(), r.value2(),
                        r.value3(), r.value4(), r.value5(), r.value6(), role.code(), r.value7(), r.value9(), r.value10())));
    }

    // ---- chapters -------------------------------------------------------------------------------

    /** Every chapter of the translation, published or not, by number; 20 to a page (at most 100). */
    @GetMapping("/novels/{novel}/translations/{team}/chapters")
    @Operation(summary = "Усі глави", description = "Усі глави перекладу — опубліковані й ні — за номером.")
    StudioApiV1.Page<StudioApiV1.ChapterEntry> chapters(@PathVariable String novel, @PathVariable String team,
            @RequestParam(required = false) String after, @RequestParam(defaultValue = "" + PAGE) int limit) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        int size = size(limit, 100);
        Long from = id(after);
        Field<Boolean> draft = DSL.field(DSL.exists(DSL.selectOne().from(EDITOR_DRAFT)
                .where(EDITOR_DRAFT.CHAPTER_ID.eq(CHAPTER.ID), EDITOR_DRAFT.ACCOUNT_ID.eq(who.viewer().accountId())))).as("draft");
        var rows = db.select(CHAPTER.NUMBER, CHAPTER.LABEL, REVISION.TITLE, CHAPTER.PUBLISHED_REVISION_ID, draft)
                .from(CHAPTER).leftJoin(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(who.editionId()), CHAPTER.DELETED_AT.isNull(),
                        from == null ? DSL.noCondition() : CHAPTER.NUMBER.gt(from.intValue()))
                .orderBy(CHAPTER.NUMBER).limit(size)
                .fetch(r -> new StudioApiV1.ChapterEntry(r.value1(), r.value2(), r.value3(), r.value4() != null, Boolean.TRUE.equals(r.value5())));
        return new StudioApiV1.Page<>(rows, rows.size() == size ? String.valueOf(rows.getLast().number()) : null);
    }

    @GetMapping("/novels/{novel}/translations/{team}/chapters/{number}")
    @Operation(summary = "Глава з чернеткою", description = "Опублікований текст глави і ваша чернетка, якщо є.")
    StudioApiV1.Chapter chapter(@PathVariable String novel, @PathVariable String team, @PathVariable int number) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return chapter(who, number);
    }

    private StudioApiV1.Chapter chapter(EditionAccess who, int number) {
        EditorModels.EditorState state = chapters.editorState(who.editionId(), number, who.viewer().accountId());
        EditorModels.Draft draft = state.draft();
        List<Long> pictures = new ArrayList<>(state.blocks().stream().map(Block::imageId).toList());
        if (draft != null) {
            pictures.addAll(draft.blocks().stream().map(Block::imageId).toList());
        }
        Map<Long, StoredImage> stored = images.findAll(pictures.stream().filter(java.util.Objects::nonNull).toList());
        return new StudioApiV1.Chapter(number, state.label(), state.title(), state.revisionId(), state.published(),
                StudioApiV1.blocks(state.blocks(), stored),
                draft == null ? null : new StudioApiV1.Draft(draft.title(), StudioApiV1.blocks(draft.blocks(), stored),
                        draft.baseRevisionId(), draft.updatedAt()));
    }

    /** A new empty chapter at the end, unpublished until its first publication. */
    @PostMapping("/novels/{novel}/translations/{team}/chapters")
    @Operation(summary = "Нова глава", description = "Порожня глава в кінці; неопублікована до першої публікації.")
    @ResponseStatus(HttpStatus.CREATED)
    StudioApiV1.Chapter newChapter(@PathVariable String novel, @PathVariable String team) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        return chapter(who, chapters.createChapter(who.editionId()));
    }

    /**
     * The chapter by its number in one request (етап 22.10): created there if there is none, its
     * draft replaced if there is. Publishing stays a request of its own, so readers never see a
     * text change quietly. With {@code Idempotency-Key} a request sent again gets the first answer.
     */
    @PutMapping("/novels/{novel}/translations/{team}/chapters/{number}")
    @Operation(summary = "Глава за номером: створити або оновити", description = """
            Створює главу на цьому місці, якщо її ще немає (лише власник і перекладачі команди), або замінює вашу чернетку, \
            якщо є. Текст завжди стає вашою чернеткою: опублікована глава лишається для читачів такою, як була, доки ви не \
            викличете `POST …/publish`.

            Місце — будь-яке вільне від 1 до 99 999: можна з пропуском чи всередині тому; адреси інших глав не змінюються, \
            а номери для читачів перераховуються, як на сайті. Без `baseRevision` чернетка йде від редакції, яку читачі \
            бачать зараз.

            Заголовок `Idempotency-Key` (до 255 знаків, напр. UUID) робить повтор безпечним: той самий запит із тим самим \
            ключем протягом 7 днів отримує ту саму відповідь (і `Idempotent-Replayed: true`) і нічого не змінює вдруге; \
            той самий ключ з іншим тілом чи адресою — 409. Відповідь із помилкою не запам'ятовується.""")
    @ApiResponse(responseCode = "200", description = "Готово: главу вже було, чернетку замінено",
            content = @Content(schema = @Schema(implementation = StudioApiV1.ChapterSaved.class)),
            headers = @Header(name = Idempotency.REPLAYED, description = "З `Idempotency-Key`: `true` — це збережена відповідь першого "
                    + "такого запиту, нічого не змінено", schema = @Schema(type = "string", example = "true")))
    @ApiResponse(responseCode = "201", description = "Готово: главу створено",
            content = @Content(schema = @Schema(implementation = StudioApiV1.ChapterSaved.class)),
            headers = @Header(name = Idempotency.REPLAYED, description = "З `Idempotency-Key`: `true` — це збережена відповідь першого "
                    + "такого запиту, нічого не змінено", schema = @Schema(type = "string", example = "true")))
    @ApiResponse(responseCode = "409", description = "Ключ `Idempotency-Key` уже використано для іншого запиту; або глава на цьому місці в кошику",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/Problem")))
    ResponseEntity<String> putChapter(@PathVariable String novel, @PathVariable String team, @PathVariable int number,
            @Parameter(description = "Будь-який унікальний рядок до 255 знаків (напр. UUID); повтор із ним не зробить нічого вдруге",
                    example = "3f1c9a6e-0d4b-4a57-9a8e-2b6f1c0d7e21")
            @RequestHeader(name = Idempotency.HEADER, required = false) String key,
            @RequestBody StudioApiV1.Text body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        if (body == null || body.blocks() == null) {
            throw UserFacingException.badRequest("Надішліть текст глави блоками (blocks); порожній список — порожня глава.");
        }
        long accountId = who.viewer().accountId();
        List<Block> blocks = StudioApiV1.blocks(body.blocks());
        // The request as the key remembers it: the translation by its id, so a renamed address or another
        // spelling of the team is the same request; the body as parsed, so spacing and field order do not matter.
        String request = "PUT chapter " + who.editionId() + " " + number + " " + json.writeValueAsString(body);
        return idempotency.once(accountId, key, request, () -> {
            boolean created = chapters.putDraft(who.editionId(), number, accountId, body.title(), blocks, body.baseRevision(),
                    who.role().translates());
            EditorModels.EditorState state = chapters.editorState(who.editionId(), number, accountId);
            EditorModels.Draft draft = state.draft();
            return new Idempotency.Answer(created ? HttpStatus.CREATED : HttpStatus.OK, new StudioApiV1.ChapterSaved(number,
                    state.label(), created, state.published(), state.revisionId(),
                    draft == null ? null : new StudioApiV1.SavedDraft(draft.title(), draft.baseRevisionId(), draft.updatedAt())));
        });
    }

    /** The person's draft of the chapter: kept for them alone until published or replaced. */
    @PutMapping("/novels/{novel}/translations/{team}/chapters/{number}/draft")
    @Operation(summary = "Зберегти чернетку", description = "Ваша чернетка глави: її бачите лише ви, доки не опублікуєте.")
    StudioApiV1.Chapter saveDraft(@PathVariable String novel, @PathVariable String team, @PathVariable int number,
            @RequestBody StudioApiV1.Text body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        chapters.saveDraft(who.editionId(), number, who.viewer().accountId(), body.title(), StudioApiV1.blocks(body.blocks()),
                body.baseRevision());
        return chapter(who, number);
    }

    /**
     * Publishes the chapter: the text sent, or — with no blocks sent — the person's draft as it is
     * (an autotranslation run with {@code publish: false} leaves its chapters there). Someone else's
     * newer publication is never overwritten: 409, and the text stays the person's draft.
     */
    @PostMapping("/novels/{novel}/translations/{team}/chapters/{number}/publish")
    @Operation(summary = "Опублікувати главу", description = "Надісланий текст або, без блоків, вашу чернетку. Новішу чужу публікацію не перезаписує: 409, а текст лишається чернеткою.")
    StudioApiV1.Published publish(@PathVariable String novel, @PathVariable String team, @PathVariable int number,
            @RequestBody(required = false) StudioApiV1.Text body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        long accountId = who.viewer().accountId();
        String title;
        List<Block> blocks;
        Long base;
        if (body == null || body.blocks() == null) {
            EditorModels.Draft draft = chapters.editorState(who.editionId(), number, accountId).draft();
            if (draft == null) {
                throw UserFacingException.badRequest("Немає ні тексту, ні вашої чернетки цієї глави — нічого публікувати.");
            }
            title = body != null && body.title() != null ? body.title() : draft.title();
            blocks = draft.blocks();
            base = draft.baseRevisionId();
        } else {
            title = body.title();
            blocks = StudioApiV1.blocks(body.blocks());
            base = body.baseRevision();
        }
        try {
            return new StudioApiV1.Published(number, chapters.publish(who.editionId(), number, accountId, title, blocks, base,
                    who.role().translates()));
        } catch (UserFacingException conflict) {
            if (conflict.status() == HttpStatus.CONFLICT && body != null && body.blocks() != null) {
                chapters.saveDraft(who.editionId(), number, accountId, title, blocks, base);
            }
            throw conflict;
        }
    }

    // ---- «Мої картинки» -------------------------------------------------------------------------

    /** The person's pictures, the newest first, 20 to a page (at most 100). */
    @GetMapping("/pictures")
    @Operation(summary = "Мої картинки", description = "Ваші картинки — найновіші першими.")
    StudioApiV1.Page<StudioApiV1.Picture> pictures(@RequestParam(required = false) String after,
            @RequestParam(defaultValue = "" + PAGE) int limit) {
        Viewer viewer = access.requireSignedIn();
        int size = size(limit, 100);
        List<PictureLibrary.Picture> found = library.after(viewer.accountId(), id(after), size);
        return new StudioApiV1.Page<>(found.stream().map(StudioApiV1::picture).toList(),
                found.size() == size ? String.valueOf(found.getLast().id()) : null);
    }

    /**
     * Uploads a picture into «Мої картинки»: JPEG, PNG or WebP up to 5 MB and 40 megapixels. Its
     * file's name is kept — «27-5.png» places it in chapter 27 after the fifth paragraph.
     */
    @PostMapping("/pictures")
    @Operation(summary = "Завантажити картинку", description = "JPEG, PNG чи WebP до 5 МБ. Назва файла зберігається: «27-5.png» стане в главу 27 після п'ятого абзацу.")
    @ResponseStatus(HttpStatus.CREATED)
    StudioApiV1.Picture upload(@RequestParam("file") MultipartFile file) {
        Viewer viewer = access.requireSignedIn();
        if (file == null || file.isEmpty()) {
            throw UserFacingException.badRequest("Виберіть файл картинки.");
        }
        StoredImage stored = images.store(viewer.accountId(), ImageKind.ILLUSTRATION, bytes(file), file.getOriginalFilename());
        return library.after(viewer.accountId(), stored.id() + 1, 1).stream().filter(picture -> picture.id() == stored.id())
                .findFirst().map(StudioApiV1::picture)
                .orElseThrow(() -> UserFacingException.notFound("Такої картинки у вас немає."));
    }

    /** Where each picture would go; nothing is saved. Owners and translators of the team only. */
    @PostMapping("/novels/{novel}/translations/{team}/pictures/preview")
    @Operation(summary = "Куди стануть картинки", description = "Лише показує, куди стала б кожна картинка; нічого не зберігає.")
    List<StudioApiV1.Place> previewPlacement(@PathVariable String novel, @PathVariable String team,
            @RequestBody StudioApiV1.Placement body) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        Spots spots = spots(body);
        return library.plan(who.viewer().accountId(), who.editionId(), spots.ids(), spots.places()).stream()
                .map(place -> new StudioApiV1.Place(place.imageId(), place.fileName(), place.chapter(), place.paragraph(), place.quote(),
                        place.problem())).toList();
    }

    /** Puts the pictures into their chapters: one new published revision per chapter. */
    @PostMapping("/novels/{novel}/translations/{team}/pictures")
    @Operation(summary = "Розставити картинки", description = "Ставить картинки в глави: одна нова опублікована редакція на главу.")
    StudioApiV1.Placed place(@PathVariable String novel, @PathVariable String team, @RequestBody StudioApiV1.Placement body) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        Spots spots = spots(body);
        PictureLibrary.Placed placed = library.place(who.viewer().accountId(), who.editionId(), spots.ids(), spots.places());
        return new StudioApiV1.Placed(placed.chapters(), placed.pictures(), placed.skipped());
    }

    private record Spots(Set<Long> ids, Map<Long, int[]> places) {
    }

    private static Spots spots(StudioApiV1.Placement body) {
        if (body == null || body.pictures() == null || body.pictures().isEmpty()) {
            throw UserFacingException.badRequest("Виберіть картинки.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        Map<Long, int[]> places = new HashMap<>();
        for (StudioApiV1.Spot spot : body.pictures()) {
            if (spot == null || spot.id() == null) {
                throw UserFacingException.badRequest("Вкажіть картинку.");
            }
            ids.add(spot.id());
            if (spot.chapter() != null || spot.after() != null) {
                if (spot.chapter() == null || spot.after() == null || spot.chapter() < 1 || spot.after() < 0) {
                    throw UserFacingException.badRequest("Місце картинки — номер глави й абзац, після якого її поставити (0 — на початку).");
                }
                places.put(spot.id(), new int[] {spot.chapter(), spot.after()});
            }
        }
        return new Spots(ids, places);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static int size(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }

    /** A cursor of this API: the last id (or number) of the page before. */
    static Long id(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(after.strip());
        } catch (NumberFormatException error) {
            throw UserFacingException.badRequest("Список змінився — почніть із першої сторінки.");
        }
    }
}
