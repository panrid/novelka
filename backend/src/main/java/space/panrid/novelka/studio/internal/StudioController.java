package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITOR_DRAFT;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionChanges;
import space.panrid.novelka.catalog.EditionData;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.TeamRole;
import space.panrid.novelka.team.Teams;
import space.panrid.novelka.text.ChapterFiles;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels;

@RestController
@RequestMapping("/api/studio")
class StudioController {

    // ---- request and response shapes -------------------------------------------------------

    record MyEdition(long editionId, String novelSlug, String title, String coverUrl, String kind, String status,
            int chapterCount, String teamHandle, String teamName, String role, int drafts) {
    }

    record CreateRequest(String kind, String title, String author, List<Block> description, List<String> tags,
            Boolean adult, String team) {
    }

    record Created(long editionId, String novelSlug) {
    }

    record Overview(long editionId, String novelSlug, String title, String author, List<StudioBlock> description,
            List<String> tags, String kind, String status, boolean adult, String coverUrl, int chapterCount,
            boolean ownNovel, String teamHandle, String teamName, String role) {
    }

    record UpdateRequest(String title, String author, List<Block> description, List<String> tags, String status,
            Boolean adult) {
    }

    record CoverRequest(Long imageId) {
    }

    /** A block as the editor draws it: pictures carry their URL too. */
    record StudioBlock(String id, String type, List<Span> content, Long imageId, String imageUrl) {
    }

    record EditorDraft(String title, List<StudioBlock> blocks, Long baseRevisionId, OffsetDateTime updatedAt) {
    }

    record EditorView(int number, String title, List<StudioBlock> blocks, Long revisionId, boolean published,
            EditorDraft draft, String role, boolean mayAddPictures, Integer previous, Integer next, String label) {
    }

    record TextRequest(String title, List<Block> blocks, Long baseRevisionId) {
    }

    record Published(long revisionId) {
    }

    record ChapterNumber(int number) {
    }

    record RevisionView(long id, String title, List<StudioBlock> blocks, String parentTitle, List<StudioBlock> parentBlocks) {
    }

    record ImportPreview(List<PreviewChapter> chapters, List<String> simplified, int firstNumber) {
    }

    record PreviewChapter(String title, int paragraphs, int characters, int pictures) {
    }

    record ImportResult(List<Integer> numbers) {
    }

    private static final Set<String> CREATE_KINDS = Set.of("human", "original");

    private final AccessPolicy access;
    private final Teams teams;
    private final Catalog catalog;
    private final Chapters chapters;
    private final Images images;
    private final DSLContext db;

    StudioController(AccessPolicy access, Teams teams, Catalog catalog, Chapters chapters, Images images, DSLContext db) {
        this.access = access;
        this.teams = teams;
        this.catalog = catalog;
        this.chapters = chapters;
        this.images = images;
        this.db = db;
    }

    // ---- publications ---------------------------------------------------------------------

    /** Everything the person works on: editions of teams they own or belong to. */
    @GetMapping
    List<MyEdition> mine() {
        Viewer viewer = access.requireSignedIn();
        var member = DSL.select(TEAM_MEMBER.TEAM_ID).from(TEAM_MEMBER).where(TEAM_MEMBER.ACCOUNT_ID.eq(viewer.accountId()));
        var drafts = DSL.select(DSL.count()).from(EDITOR_DRAFT).join(CHAPTER).on(CHAPTER.ID.eq(EDITOR_DRAFT.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(EDITION.ID).and(EDITOR_DRAFT.ACCOUNT_ID.eq(viewer.accountId()))).<Integer>asField("drafts");
        var rows = db.select(EDITION.ID, NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), EDITION.COVER_IMAGE_ID,
                        EDITION.KIND, EDITION.STATUS, EDITION.CHAPTER_COUNT, TEAM.ID, drafts)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(TEAM.OWNER_ID.eq(viewer.accountId()).or(TEAM.ID.in(member)))
                .orderBy(DSL.greatest(EDITION.CREATED_AT, DSL.coalesce(EDITION.LAST_PUBLISHED_AT, EDITION.CREATED_AT)).desc())
                .fetch();
        Map<Long, StoredImage> covers = images.findAll(rows.map(r -> r.value4()));
        return rows.map(r -> {
            TeamInfo team = teams.find(r.value8()).orElseThrow();
            TeamRole role = teams.roleOf(r.value8(), viewer.accountId()).orElseThrow();
            return new MyEdition(r.value1(), r.value2(), r.value3(), cover(covers, r.value4()), r.value5(), r.value6(),
                    r.value7(), team.handle(), team.name(), role.code(), r.value9());
        });
    }

    @PostMapping("/editions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Created create(@RequestBody CreateRequest body) {
        Viewer viewer = access.requireSignedIn();
        if (!CREATE_KINDS.contains(body.kind())) {
            throw UserFacingException.badRequest("Оберіть: свій переклад чи свій твір.");
        }
        long teamId = body.team() == null || body.team().isBlank()
                ? teams.personalTeam(viewer.accountId())
                : teams.findByHandle(body.team()).orElseThrow(() -> UserFacingException.notFound("Такої команди немає.")).id();
        access.requireTeamTranslator(teamId);
        boolean original = body.kind().equals("original");
        String author = original && (body.author() == null || body.author().isBlank()) ? viewer.nick() : body.author();
        EditionRef ref = catalog.createNovel(new NewNovel(body.title(), author,
                body.description() == null ? List.of() : description(body.description()),
                body.tags() == null ? List.of() : body.tags(), body.kind(), Boolean.TRUE.equals(body.adult()), teamId,
                original ? viewer.accountId() : null));
        return new Created(ref.editionId(), ref.novelSlug());
    }

    @GetMapping("/editions/{editionId}")
    Overview overview(@PathVariable long editionId) {
        EditionAccess who = access.requireTextEditor(editionId);
        EditionData data = catalog.edition(editionId).orElseThrow();
        TeamInfo team = teams.find(data.teamId()).orElseThrow();
        return new Overview(editionId, data.novelSlug(), data.title(), data.author(), studioBlocks(data.description()),
                data.tags(), data.kind(), data.status(), data.adult(),
                data.coverImageId() == null ? null : images.find(data.coverImageId()).map(image -> image.url(480)).orElse(null),
                data.chapterCount(), data.ownNovel(), team.handle(), team.name(), who.role().code());
    }

    @PatchMapping("/editions/{editionId}")
    Overview update(@PathVariable long editionId, @RequestBody UpdateRequest body) {
        access.requireEditionOwner(editionId);
        catalog.updateEdition(editionId, new EditionChanges(body.title(), body.author(),
                body.description() == null ? null : description(body.description()), body.tags(), body.status(), body.adult()));
        return overview(editionId);
    }

    @PutMapping("/editions/{editionId}/cover")
    Overview setCover(@PathVariable long editionId, @RequestBody CoverRequest body) {
        EditionAccess who = access.requireEditionOwner(editionId);
        if (body.imageId() != null) {
            boolean own = db.fetchExists(IMAGE, IMAGE.ID.eq(body.imageId()).and(IMAGE.KIND.eq("cover"))
                    .and(IMAGE.OWNER_ACCOUNT_ID.eq(who.viewer().accountId())).and(IMAGE.HIDDEN_AT.isNull()));
            if (!own) {
                throw UserFacingException.badRequest("Спершу завантажте обкладинку.");
            }
        }
        catalog.setCover(editionId, body.imageId());
        return overview(editionId);
    }

    // ---- chapters and the editor -------------------------------------------------------------

    @GetMapping("/editions/{editionId}/chapters")
    List<EditorModels.StudioChapter> chapterList(@PathVariable long editionId, @RequestParam(defaultValue = "1") int page) {
        EditionAccess who = access.requireTextEditor(editionId);
        return chapters.studioChapters(editionId, who.viewer().accountId(), Math.max(1, page), 100);
    }

    @PostMapping("/editions/{editionId}/chapters")
    @ResponseStatus(HttpStatus.CREATED)
    ChapterNumber newChapter(@PathVariable long editionId) {
        access.requireTranslator(editionId);
        return new ChapterNumber(chapters.createChapter(editionId));
    }

    @GetMapping("/editions/{editionId}/chapters/{number}")
    EditorView editor(@PathVariable long editionId, @PathVariable int number) {
        EditionAccess who = access.requireTextEditor(editionId);
        EditorModels.EditorState state = chapters.editorState(editionId, number, who.viewer().accountId());
        EditorModels.Draft draft = state.draft();
        return new EditorView(number, state.title(), studioBlocks(state.blocks()), state.revisionId(), state.published(),
                draft == null ? null : new EditorDraft(draft.title(), studioBlocks(draft.blocks()), draft.baseRevisionId(), draft.updatedAt()),
                who.role().code(), who.role().translates(), neighbour(editionId, number, false), neighbour(editionId, number, true),
                state.label());
    }

    @PutMapping("/editions/{editionId}/chapters/{number}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveDraft(@PathVariable long editionId, @PathVariable int number, @RequestBody TextRequest body) {
        EditionAccess who = access.requireTextEditor(editionId);
        chapters.saveDraft(editionId, number, who.viewer().accountId(), body.title(), body.blocks(), body.baseRevisionId());
    }

    @DeleteMapping("/editions/{editionId}/chapters/{number}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardDraft(@PathVariable long editionId, @PathVariable int number) {
        EditionAccess who = access.requireTextEditor(editionId);
        chapters.discardDraft(editionId, number, who.viewer().accountId());
    }

    record LabelRequest(String label) {
    }

    /** The number readers see; any team member who edits text may fix it. */
    @PutMapping("/editions/{editionId}/chapters/{number}/label")
    void label(@PathVariable long editionId, @PathVariable int number, @RequestBody LabelRequest body) {
        access.requireTextEditor(editionId);
        chapters.setLabel(editionId, number, body.label());
    }

    @PostMapping("/editions/{editionId}/chapters/{number}/publish")
    Published publish(@PathVariable long editionId, @PathVariable int number, @RequestBody TextRequest body) {
        EditionAccess who = access.requireTextEditor(editionId);
        try {
            return new Published(chapters.publish(editionId, number, who.viewer().accountId(), body.title(),
                    body.blocks(), body.baseRevisionId(), who.role().translates()));
        } catch (UserFacingException conflict) {
            // Whatever went wrong, the text the person typed is kept as their draft.
            if (conflict.status() == HttpStatus.CONFLICT) {
                chapters.saveDraft(editionId, number, who.viewer().accountId(), body.title(), body.blocks(), body.baseRevisionId());
            }
            throw conflict;
        }
    }

    @GetMapping("/editions/{editionId}/chapters/{number}/revisions")
    List<EditorModels.RevisionInfo> revisions(@PathVariable long editionId, @PathVariable int number) {
        access.requireTextEditor(editionId);
        return chapters.revisions(editionId, number);
    }

    @GetMapping("/editions/{editionId}/chapters/{number}/revisions/{revisionId}")
    RevisionView revision(@PathVariable long editionId, @PathVariable int number, @PathVariable long revisionId) {
        access.requireTextEditor(editionId);
        EditorModels.RevisionText text = chapters.revision(editionId, number, revisionId);
        return new RevisionView(text.id(), text.title(), studioBlocks(text.blocks()), text.parentTitle(),
                studioBlocks(text.parentBlocks()));
    }

    // ---- files and contribution ------------------------------------------------------------

    @PostMapping("/editions/{editionId}/import/preview")
    ImportPreview preview(@PathVariable long editionId, @RequestParam("file") MultipartFile file) {
        access.requireTranslator(editionId);
        ChapterFiles.Parsed parsed = ChapterFiles.parse(file.getOriginalFilename(), utf8(file));
        int first = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), DSL.val(0))).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class) + 1;
        return new ImportPreview(parsed.chapters().stream().map(chapter -> new PreviewChapter(chapter.title(),
                (int) chapter.blocks().stream().filter(block -> block.type().equals("paragraph")).count(),
                chapter.blocks().stream().mapToInt(block -> block.text().length()).sum(),
                (int) chapter.blocks().stream().filter(block -> block.sourceUrl() != null).count())).toList(),
                parsed.simplified(), first);
    }

    @PostMapping("/editions/{editionId}/import")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ImportResult importFile(@PathVariable long editionId, @RequestParam("file") MultipartFile file) {
        EditionAccess who = access.requireTranslator(editionId);
        ChapterFiles.Parsed parsed = ChapterFiles.parse(file.getOriginalFilename(), utf8(file));
        // Pictures by link are copied to the site now; the import fails whole if one cannot be.
        List<ChapterFiles.ParsedChapter> ready = parsed.chapters().stream().map(chapter -> new ChapterFiles.ParsedChapter(
                chapter.title(), chapter.blocks().stream().map(block -> block.sourceUrl() == null ? block
                        : new Block(block.id(), "image", List.of(), images.storeFromUrl(who.viewer().accountId(),
                                space.panrid.novelka.media.ImageKind.ILLUSTRATION, block.sourceUrl()).id(), null)).toList())).toList();
        return new ImportResult(chapters.publishNew(editionId, ready, "import", who.viewer().accountId()));
    }

    @GetMapping("/editions/{editionId}/contributions")
    List<EditorModels.Contribution> contributions(@PathVariable long editionId) {
        access.requireTextEditor(editionId);
        return chapters.contributions(editionId);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<StudioBlock> studioBlocks(List<Block> blocks) {
        Map<Long, StoredImage> pictures = images.findAll(blocks.stream().map(Block::imageId).toList());
        return blocks.stream().map(block -> new StudioBlock(block.id(), block.type(), block.content(), block.imageId(),
                block.imageId() == null || !pictures.containsKey(block.imageId()) ? null : pictures.get(block.imageId()).url(1280)))
                .toList();
    }

    /** Descriptions allow paragraphs with marks only, no pictures or headings. */
    private static List<Block> description(List<Block> blocks) {
        List<Block> paragraphs = blocks.stream().filter(block -> block.type().equals("paragraph"))
                .map(block -> Block.paragraph(block.id(), block.content())).toList();
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<Block> normalized = space.panrid.novelka.text.BlockRules.normalize(paragraphs);
        if (normalized.stream().mapToInt(block -> block.text().length()).sum() > 3000) {
            throw UserFacingException.badRequest("Опис — до 3000 знаків.");
        }
        return normalized;
    }

    private Integer neighbour(long editionId, int number, boolean next) {
        var inEdition = CHAPTER.EDITION_ID.eq(editionId);
        return next
                ? db.select(DSL.min(CHAPTER.NUMBER)).from(CHAPTER).where(inEdition.and(CHAPTER.NUMBER.gt(number))).fetchOne(0, Integer.class)
                : db.select(DSL.max(CHAPTER.NUMBER)).from(CHAPTER).where(inEdition.and(CHAPTER.NUMBER.lt(number))).fetchOne(0, Integer.class);
    }

    private static String cover(Map<Long, StoredImage> covers, Long id) {
        return id == null || !covers.containsKey(id) ? null : covers.get(id).url(480);
    }

    /** Files must be UTF-8: a Windows-1251 file would otherwise become mojibake silently. */
    static String utf8(MultipartFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException error) {
            throw UserFacingException.badRequest("Файл має бути в кодуванні UTF-8. Збережіть його як UTF-8 і спробуйте ще раз.");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
