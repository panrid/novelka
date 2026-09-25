package space.panrid.novelka.suggestion.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.text.EditorModels.CurrentText;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
class SuggestionController {

    record BlockDraft(long editionId, int number, String blockId, List<Span> content, String note) {
    }

    record ReplaceDraft(long editionId, int number, String find, String replacement, String note) {
    }

    record ChapterDraftRequest(long editionId, int number, String title, List<Block> blocks, String note) {
    }

    record Submit(long editionId) {
    }

    record Saved(long id) {
    }

    record Submitted(int count) {
    }

    record Count(int occurrences) {
    }

    /** The author's own drafts and sent suggestions in a chapter, drawn over the text. */
    record Mine(List<MineItem> items, int draftsInEdition) {
    }

    record MineItem(long id, String kind, String state, String blockId, List<Span> proposed, String find,
            String replacement, String note) {
    }

    record ReviewItem(long id, String kind, String authorNick, String note, String blockId, List<Span> current,
            List<Span> proposed, String proposedTitle, List<Block> proposedBlocks, String find, String replacement,
            int occurrences, boolean stale, OffsetDateTime createdAt) {
    }

    record QueueRow(int number, String title, int pending) {
    }

    record MySuggestion(long id, String novelSlug, String novelTitle, String teamHandle, int chapter, String kind,
            String preview, String state, String reviewNote, OffsetDateTime updatedAt) {
    }

    record ReviewRequest(List<SuggestionService.Decision> decisions, String note) {
    }

    private static final TypeReference<List<Span>> SPANS = new TypeReference<>() { };

    private final SuggestionService service;
    private final AccessPolicy access;
    private final DSLContext db;
    private final JsonMapper json;

    SuggestionController(SuggestionService service, AccessPolicy access, DSLContext db, JsonMapper json) {
        this.service = service;
        this.access = access;
        this.db = db;
        this.json = json;
    }

    // ---- readers -----------------------------------------------------------------------------

    @PutMapping("/api/suggestions/draft/block")
    Saved block(@RequestBody BlockDraft body) {
        return new Saved(service.draftBlock(access.requireSignedIn(), body.editionId(), body.number(), body.blockId(),
                body.content(), body.note()));
    }

    @PutMapping("/api/suggestions/draft/replace")
    Saved replace(@RequestBody ReplaceDraft body) {
        return new Saved(service.draftReplace(access.requireSignedIn(), body.editionId(), body.number(), body.find(),
                body.replacement(), body.note()));
    }

    @PutMapping("/api/suggestions/draft/chapter")
    Saved chapter(@RequestBody ChapterDraftRequest body) {
        return new Saved(service.draftChapter(access.requireSignedIn(), body.editionId(), body.number(), body.title(),
                body.blocks(), body.note()));
    }

    @GetMapping("/api/suggestions/count")
    Count count(@RequestParam long editionId, @RequestParam int number, @RequestParam String find) {
        return new Count(service.occurrences(access.requireSignedIn(), editionId, number, find));
    }

    @PostMapping("/api/suggestions/submit")
    Submitted submit(@RequestBody Submit body) {
        return new Submitted(service.submit(access.requireSignedIn(), body.editionId()));
    }

    @DeleteMapping("/api/suggestions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable long id) {
        service.withdraw(access.requireSignedIn(), id);
    }

    @GetMapping("/api/suggestions/mine")
    Mine mine(@RequestParam long editionId, @RequestParam int number) {
        Viewer viewer = access.requireSignedIn();
        var chapter = CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.NUMBER.eq(number));
        List<MineItem> items = db.select(SUGGESTION.ID, SUGGESTION.KIND, SUGGESTION.STATE, SUGGESTION.BLOCK_ID,
                        SUGGESTION.PROPOSED, SUGGESTION.FIND_TEXT, SUGGESTION.REPLACEMENT, SUGGESTION.NOTE)
                .from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID))
                .where(chapter.and(SUGGESTION.AUTHOR_ID.eq(viewer.accountId())).and(SUGGESTION.STATE.in("draft", "pending")))
                .orderBy(SUGGESTION.CREATED_AT)
                .fetch(r -> new MineItem(r.value1(), r.value2(), r.value3(), r.value4(),
                        r.value2().equals("block") ? json.readValue(r.value5().data(), SPANS) : null, r.value6(), r.value7(), r.value8()));
        int drafts = db.fetchCount(SUGGESTION.join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID)),
                CHAPTER.EDITION_ID.eq(editionId).and(SUGGESTION.AUTHOR_ID.eq(viewer.accountId())).and(SUGGESTION.STATE.eq("draft")));
        return new Mine(items, drafts);
    }

    @GetMapping("/api/me/suggestions")
    List<MySuggestion> history() {
        Viewer viewer = access.requireSignedIn();
        return db.select(SUGGESTION.ID, NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), TEAM.HANDLE, CHAPTER.NUMBER,
                        SUGGESTION.KIND, SUGGESTION.PROPOSED, SUGGESTION.FIND_TEXT, SUGGESTION.REPLACEMENT, SUGGESTION.STATE,
                        SUGGESTION.REVIEW_NOTE, SUGGESTION.UPDATED_AT)
                .from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID))
                .join(EDITION).on(EDITION.ID.eq(CHAPTER.EDITION_ID)).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(SUGGESTION.AUTHOR_ID.eq(viewer.accountId()).and(SUGGESTION.STATE.ne("withdrawn")))
                .orderBy(SUGGESTION.UPDATED_AT.desc())
                .limit(200)
                .fetch(r -> new MySuggestion(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
                        preview(r.value6(), r.value7() == null ? null : r.value7().data(), r.value8(), r.value9()),
                        r.value10(), r.value11(), r.value12()));
    }

    // ---- the team ----------------------------------------------------------------------------

    @GetMapping("/api/studio/editions/{editionId}/suggestions")
    List<QueueRow> queue(@PathVariable long editionId) {
        access.requireTextEditor(editionId);
        var pending = DSL.count().as("pending");
        return db.select(CHAPTER.NUMBER, REVISION.TITLE, pending)
                .from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID))
                .join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId).and(SUGGESTION.STATE.eq("pending")))
                .groupBy(CHAPTER.NUMBER, REVISION.TITLE)
                .orderBy(CHAPTER.NUMBER)
                .fetch(r -> new QueueRow(r.value1(), r.value2(), r.value3()));
    }

    @GetMapping("/api/studio/editions/{editionId}/chapters/{number}/suggestions")
    List<ReviewItem> pending(@PathVariable long editionId, @PathVariable int number) {
        access.requireTextEditor(editionId);
        CurrentText current = service.readable(access.requireSignedIn(), editionId, number);
        Map<String, Block> blocks = new java.util.HashMap<>();
        current.blocks().forEach(block -> blocks.put(block.id(), block));
        return db.select(SUGGESTION.asterisk(), ACCOUNT.NICK)
                .from(SUGGESTION).join(ACCOUNT).on(ACCOUNT.ID.eq(SUGGESTION.AUTHOR_ID))
                .where(SUGGESTION.CHAPTER_ID.eq(current.chapterId()).and(SUGGESTION.STATE.eq("pending")))
                .orderBy(SUGGESTION.CREATED_AT)
                .fetch(r -> {
                    String kind = r.get(SUGGESTION.KIND);
                    Block target = kind.equals("block") ? blocks.get(r.get(SUGGESTION.BLOCK_ID)) : null;
                    boolean stale = switch (kind) {
                        case "block" -> target == null || !target.text().equals(r.get(SUGGESTION.ORIGINAL_TEXT));
                        case "replace" -> SuggestionService.occurrences(current.blocks(), r.get(SUGGESTION.FIND_TEXT)) == 0;
                        default -> r.get(SUGGESTION.BASE_REVISION_ID) != current.revisionId();
                    };
                    SuggestionService.ChapterDraft chapter = kind.equals("chapter")
                            ? json.readValue(r.get(SUGGESTION.PROPOSED).data(), SuggestionService.ChapterDraft.class) : null;
                    return new ReviewItem(r.get(SUGGESTION.ID), kind, r.get(ACCOUNT.NICK), r.get(SUGGESTION.NOTE),
                            r.get(SUGGESTION.BLOCK_ID), target == null ? null : target.content(),
                            kind.equals("block") ? json.readValue(r.get(SUGGESTION.PROPOSED).data(), SPANS) : null,
                            chapter == null ? null : chapter.title(), chapter == null ? null : chapter.blocks(),
                            r.get(SUGGESTION.FIND_TEXT), r.get(SUGGESTION.REPLACEMENT),
                            kind.equals("replace") ? SuggestionService.occurrences(current.blocks(), r.get(SUGGESTION.FIND_TEXT)) : 0,
                            stale, r.get(SUGGESTION.CREATED_AT));
                });
    }

    @PostMapping("/api/studio/editions/{editionId}/chapters/{number}/suggestions/review")
    SuggestionService.ReviewResult review(@PathVariable long editionId, @PathVariable int number,
            @RequestBody ReviewRequest body) {
        var who = access.requireTextEditor(editionId);
        return service.review(who.viewer(), editionId, number, body.decisions() == null ? List.of() : body.decisions(), body.note());
    }

    private String preview(String kind, String proposed, String find, String replacement) {
        return switch (kind) {
            case "block" -> json.readValue(proposed, SPANS).stream().map(Span::text).reduce("", String::concat);
            case "replace" -> "«" + find + "» → «" + replacement + "»";
            default -> "Зміни в усій главі";
        };
    }
}
