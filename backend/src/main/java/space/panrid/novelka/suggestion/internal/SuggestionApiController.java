package space.panrid.novelka.suggestion.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels.CurrentText;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Правки in the Studio's API (етап 22.3), under {@code /api/v1/studio/novels/{novel}/translations/{team}}:
 * anyone who may read the translation sends a batch of them (with a token — the «suggest» right),
 * the team reviews them and accepts or rejects them («studio»). The same rules as in the reader:
 * accepted правки of a chapter become one new revision, outdated ones are marked stale.
 */
@RestController
@RequestMapping("/api/v1/studio/novels/{novel}/translations/{team}/suggestions")
class SuggestionApiController {

    private static final TypeReference<List<Span>> SPANS = new TypeReference<>() { };
    private static final int PAGE = 20;

    // ---- shapes: stable, apart from the site's own -------------------------------------------------

    /** A run of text; marks are bold, italic, underline, strike. */
    record TextSpan(String text, List<String> marks) {
    }

    /**
     * A change: a paragraph as it should read ({@code block} — its id — and {@code content}), or a
     * replacement within the chapter ({@code find} → {@code replacement}).
     */
    record Change(Integer chapter, String block, List<TextSpan> content, String find, String replacement) {
    }

    /** @param note why, for the team (up to 500 characters) */
    record Batch(List<Change> changes, String note) {
    }

    record Sent(int sent, List<Long> ids) {
    }

    /**
     * A правка waiting for the team.
     *
     * @param kind     block (a paragraph), replace (a fragment in the chapter) or chapter (the whole chapter)
     * @param current  the paragraph as it reads now, for a block
     * @param stale    the text it was made against has changed: accepting it does nothing
     * @param occurrences how many times a replacement's fragment is found now
     */
    record Pending(long id, int chapter, String kind, String author, String note, String block, List<TextSpan> current,
            List<TextSpan> proposed, String proposedTitle, String find, String replacement, int occurrences, boolean stale,
            OffsetDateTime createdAt) {
    }

    record Page<T>(List<T> items, String next) {
    }

    /** @param content for a block, the paragraph as the reviewer would have it instead of as proposed */
    record Decision(Long id, boolean accept, List<TextSpan> content) {
    }

    /** @param note the reviewer's word to the authors */
    record Review(List<Decision> decisions, String note) {
    }

    /** @param revision the chapter's new revision, null when nothing was accepted there */
    record ChapterResult(int chapter, Long revision, int accepted, int rejected, int stale) {
    }

    record Reviewed(int accepted, int rejected, int stale, List<ChapterResult> chapters) {
    }

    private final SuggestionService service;
    private final AccessPolicy access;
    private final Chapters chapters;
    private final DSLContext db;
    private final JsonMapper json;

    SuggestionApiController(SuggestionService service, AccessPolicy access, Chapters chapters, DSLContext db, JsonMapper json) {
        this.service = service;
        this.access = access;
        this.chapters = chapters;
        this.db = db;
        this.json = json;
    }

    /** Sends the changes as one batch for the team; all or nothing. Drafts kept on the site stay drafts. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Sent send(@PathVariable String novel, @PathVariable String team, @RequestBody Batch body) {
        Viewer viewer = access.requireSignedIn();
        long editionId = access.editionAt(novel, team);
        List<SuggestionService.Item> items = body.changes() == null ? List.of() : body.changes().stream().map(change -> {
            if (change == null || change.chapter() == null) {
                throw UserFacingException.badRequest("Вкажіть главу кожної правки.");
            }
            return new SuggestionService.Item(change.chapter(), change.block(), spans(change.content()), change.find(), change.replacement());
        }).toList();
        List<Long> ids = service.sendBatch(viewer, editionId, items, body.note());
        return new Sent(ids.size(), ids);
    }

    /** Правки waiting for the team, the oldest first; {@code chapter} narrows them to one chapter. */
    @GetMapping
    Page<Pending> pending(@PathVariable String novel, @PathVariable String team, @RequestParam(required = false) Integer chapter,
            @RequestParam(required = false) String after, @RequestParam(defaultValue = "" + PAGE) int limit) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        int size = Math.max(1, Math.min(limit, 100));
        Long from = id(after);
        var rows = db.select(SUGGESTION.asterisk(), ACCOUNT.NICK, CHAPTER.NUMBER)
                .from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(SUGGESTION.AUTHOR_ID))
                .where(CHAPTER.EDITION_ID.eq(who.editionId()), SUGGESTION.STATE.eq("pending"),
                        chapter == null ? DSL.noCondition() : CHAPTER.NUMBER.eq(chapter),
                        from == null ? DSL.noCondition() : SUGGESTION.ID.gt(from))
                .orderBy(SUGGESTION.ID).limit(size).fetch();
        Map<Integer, CurrentText> texts = new HashMap<>();
        List<Pending> items = rows.map(r -> {
            int number = r.get(CHAPTER.NUMBER);
            CurrentText current = texts.computeIfAbsent(number, n -> chapters.current(who.editionId(), n));
            String kind = r.get(SUGGESTION.KIND);
            Block target = kind.equals("block") ? current.blocks().stream().filter(block -> block.id().equals(r.get(SUGGESTION.BLOCK_ID)))
                    .findFirst().orElse(null) : null;
            int found = kind.equals("replace") ? SuggestionService.occurrences(current.blocks(), r.get(SUGGESTION.FIND_TEXT)) : 0;
            boolean stale = switch (kind) {
                case "block" -> target == null || !target.text().equals(r.get(SUGGESTION.ORIGINAL_TEXT));
                case "replace" -> found == 0;
                default -> r.get(SUGGESTION.BASE_REVISION_ID) != current.revisionId();
            };
            SuggestionService.ChapterDraft whole = kind.equals("chapter")
                    ? json.readValue(r.get(SUGGESTION.PROPOSED).data(), SuggestionService.ChapterDraft.class) : null;
            return new Pending(r.get(SUGGESTION.ID), number, kind, r.get(ACCOUNT.NICK), r.get(SUGGESTION.NOTE), r.get(SUGGESTION.BLOCK_ID),
                    target == null ? null : out(target.content()),
                    kind.equals("block") ? out(json.readValue(r.get(SUGGESTION.PROPOSED).data(), SPANS)) : null,
                    whole == null ? null : whole.title(), r.get(SUGGESTION.FIND_TEXT), r.get(SUGGESTION.REPLACEMENT), found, stale,
                    r.get(SUGGESTION.CREATED_AT));
        });
        return new Page<>(items, items.size() == size ? String.valueOf(items.getLast().id()) : null);
    }

    /** Accepts or rejects правки of any chapters; each chapter's accepted ones become one revision. */
    @PostMapping("/review")
    Reviewed review(@PathVariable String novel, @PathVariable String team, @RequestBody Review body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        List<SuggestionService.Decision> decisions = body.decisions() == null ? List.of() : body.decisions().stream().map(decision -> {
            if (decision == null || decision.id() == null) {
                throw UserFacingException.badRequest("Вкажіть правку кожного рішення.");
            }
            return new SuggestionService.Decision(decision.id(), decision.accept(), decision.content() == null ? null : spans(decision.content()));
        }).toList();
        List<SuggestionService.ChapterReview> done = service.reviewAcross(who.viewer(), who.editionId(), decisions, body.note());
        List<ChapterResult> results = done.stream().map(review -> new ChapterResult(review.chapter(), review.result().revisionId(),
                review.result().accepted(), review.result().rejected(), review.result().stale())).toList();
        return new Reviewed(results.stream().mapToInt(ChapterResult::accepted).sum(), results.stream().mapToInt(ChapterResult::rejected).sum(),
                results.stream().mapToInt(ChapterResult::stale).sum(), results);
    }

    private static List<Span> spans(List<TextSpan> content) {
        return content == null ? null : content.stream()
                .map(span -> new Span(span.text() == null ? "" : span.text(), span.marks() == null ? List.of() : span.marks())).toList();
    }

    private static List<TextSpan> out(List<Span> spans) {
        return spans.stream().map(span -> new TextSpan(span.text(), span.marks() == null ? List.of() : span.marks())).toList();
    }

    private static Long id(String after) {
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
