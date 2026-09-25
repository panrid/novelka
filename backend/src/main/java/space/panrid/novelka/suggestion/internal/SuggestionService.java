package space.panrid.novelka.suggestion.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION_BATCH;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.jooq.tables.records.SuggestionRecord;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.suggestion.SuggestionsReviewed;
import space.panrid.novelka.text.BlockRules;
import space.panrid.novelka.text.ChangeStats;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels.CurrentText;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Suggestions (рішення 6, 7, 26): anyone proposes a paragraph, a replacement within one
 * chapter, or a whole chapter; drafts go out as a batch; the team accepts them in the
 * reader, and everything accepted in a chapter becomes one new revision.
 */
@Service
class SuggestionService {

    record ChapterDraft(String title, List<Block> blocks) {
    }

    record Decision(long id, boolean accept, List<Span> content) {
    }

    record ReviewResult(Long revisionId, int accepted, int rejected, int stale) {
    }

    static final int NOTE_MAX = 500;
    static final int FIND_MAX = 200;

    private static final TypeReference<List<Span>> SPANS = new TypeReference<>() { };

    private final DSLContext db;
    private final Chapters chapters;
    private final JsonMapper json;
    private final Clock clock;
    private final RateLimiter drafts;

    private final ApplicationEventPublisher events;

    SuggestionService(DSLContext db, Chapters chapters, JsonMapper json, Clock clock, ApplicationEventPublisher events) {
        this.events = events;
        this.db = db;
        this.chapters = chapters;
        this.json = json;
        this.clock = clock;
        this.drafts = new RateLimiter(300, Duration.ofDays(1), clock);
    }

    // ---- drafts ------------------------------------------------------------------------------

    @Transactional
    long draftBlock(Viewer viewer, long editionId, int number, String blockId, List<Span> content, String note) {
        CurrentText text = readable(viewer, editionId, number);
        Block block = text.blocks().stream().filter(b -> b.id().equals(blockId)).findFirst()
                .orElseThrow(() -> UserFacingException.badRequest("Цього абзацу вже немає. Оновіть сторінку."));
        List<Span> proposed = spans(content);
        String proposedText = plain(proposed);
        if (proposedText.isBlank()) {
            throw UserFacingException.badRequest("Абзац не може бути порожнім.");
        }
        if (proposed.equals(block.content())) {
            throw UserFacingException.badRequest("Текст не змінився.");
        }
        return upsert(viewer, text, "block", blockId, block.text(), JSONB.valueOf(json.writeValueAsString(proposed)),
                null, null, note, SUGGESTION.BLOCK_ID.eq(blockId));
    }

    @Transactional
    long draftReplace(Viewer viewer, long editionId, int number, String find, String replacement, String note) {
        CurrentText text = readable(viewer, editionId, number);
        String what = find == null ? "" : find;
        String with = replacement == null ? "" : replacement;
        if (what.isBlank() || what.length() > FIND_MAX || with.length() > FIND_MAX) {
            throw UserFacingException.badRequest("Фрагмент і заміна — до %d символів.".formatted(FIND_MAX));
        }
        if (what.equals(with)) {
            throw UserFacingException.badRequest("Заміна збігається з фрагментом.");
        }
        if (occurrences(text.blocks(), what) == 0) {
            throw UserFacingException.badRequest("У цій главі немає такого фрагмента.");
        }
        return upsert(viewer, text, "replace", null, null, null, what, with, note, SUGGESTION.FIND_TEXT.eq(what));
    }

    @Transactional
    long draftChapter(Viewer viewer, long editionId, int number, String title, List<Block> blocks, String note) {
        CurrentText text = readable(viewer, editionId, number);
        ChapterDraft draft = new ChapterDraft(BlockRules.title(title), BlockRules.normalize(blocks));
        if (draft.blocks().equals(text.blocks()) && draft.title().equals(text.title())) {
            throw UserFacingException.badRequest("Текст не змінився.");
        }
        return upsert(viewer, text, "chapter", null, null, JSONB.valueOf(json.writeValueAsString(draft)), null, null, note,
                SUGGESTION.KIND.eq("chapter"));
    }

    private long upsert(Viewer viewer, CurrentText text, String kind, String blockId, String original, JSONB proposed,
            String find, String replacement, String note, org.jooq.Condition sameTarget) {
        if (!drafts.tryAcquire(Long.toString(viewer.accountId()))) {
            throw UserFacingException.tooManyRequests();
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > NOTE_MAX) {
            throw UserFacingException.badRequest("Пояснення — до %d символів.".formatted(NOTE_MAX));
        }
        OffsetDateTime now = now();
        var existing = db.select(SUGGESTION.ID).from(SUGGESTION)
                .where(SUGGESTION.AUTHOR_ID.eq(viewer.accountId()).and(SUGGESTION.CHAPTER_ID.eq(text.chapterId()))
                        .and(SUGGESTION.STATE.eq("draft")).and(SUGGESTION.KIND.eq(kind)).and(sameTarget))
                .fetchOptional(SUGGESTION.ID);
        if (existing.isPresent()) {
            db.update(SUGGESTION)
                    .set(SUGGESTION.BASE_REVISION_ID, text.revisionId())
                    .set(SUGGESTION.ORIGINAL_TEXT, original)
                    .set(SUGGESTION.PROPOSED, proposed)
                    .set(SUGGESTION.REPLACEMENT, replacement)
                    .set(SUGGESTION.NOTE, cleanNote)
                    .set(SUGGESTION.UPDATED_AT, now)
                    .where(SUGGESTION.ID.eq(existing.get()))
                    .execute();
            return existing.get();
        }
        return db.insertInto(SUGGESTION)
                .set(SUGGESTION.CHAPTER_ID, text.chapterId())
                .set(SUGGESTION.BASE_REVISION_ID, text.revisionId())
                .set(SUGGESTION.AUTHOR_ID, viewer.accountId())
                .set(SUGGESTION.KIND, kind)
                .set(SUGGESTION.BLOCK_ID, blockId)
                .set(SUGGESTION.ORIGINAL_TEXT, original)
                .set(SUGGESTION.PROPOSED, proposed)
                .set(SUGGESTION.FIND_TEXT, find)
                .set(SUGGESTION.REPLACEMENT, replacement)
                .set(SUGGESTION.NOTE, cleanNote)
                .set(SUGGESTION.CREATED_AT, now)
                .set(SUGGESTION.UPDATED_AT, now)
                .returning(SUGGESTION.ID)
                .fetchOne(SUGGESTION.ID);
    }

    /** A draft or a sent suggestion the author takes back before it is reviewed. */
    @Transactional
    void withdraw(Viewer viewer, long suggestionId) {
        int changed = db.update(SUGGESTION)
                .set(SUGGESTION.STATE, "withdrawn")
                .set(SUGGESTION.UPDATED_AT, now())
                .where(SUGGESTION.ID.eq(suggestionId).and(SUGGESTION.AUTHOR_ID.eq(viewer.accountId()))
                        .and(SUGGESTION.STATE.in("draft", "pending")))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Цієї правки вже немає або її перевірили.");
        }
    }

    /** Sends all the author's drafts in this edition as one batch. */
    @Transactional
    int submit(Viewer viewer, long editionId) {
        var draftIds = db.select(SUGGESTION.ID).from(SUGGESTION).join(CHAPTER).on(CHAPTER.ID.eq(SUGGESTION.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId).and(SUGGESTION.AUTHOR_ID.eq(viewer.accountId()))
                        .and(SUGGESTION.STATE.eq("draft")))
                .fetch(SUGGESTION.ID);
        if (draftIds.isEmpty()) {
            throw UserFacingException.badRequest("Немає ненадісланих правок.");
        }
        long batch = db.insertInto(SUGGESTION_BATCH)
                .set(SUGGESTION_BATCH.EDITION_ID, editionId)
                .set(SUGGESTION_BATCH.AUTHOR_ID, viewer.accountId())
                .returning(SUGGESTION_BATCH.ID)
                .fetchOne(SUGGESTION_BATCH.ID);
        db.update(SUGGESTION).set(SUGGESTION.STATE, "pending").set(SUGGESTION.BATCH_ID, batch).set(SUGGESTION.UPDATED_AT, now())
                .where(SUGGESTION.ID.in(draftIds)).execute();
        return draftIds.size();
    }

    int occurrences(Viewer viewer, long editionId, int number, String find) {
        return find == null || find.isBlank() ? 0 : occurrences(readable(viewer, editionId, number).blocks(), find);
    }

    // ---- review ------------------------------------------------------------------------------

    /**
     * Applies the reviewer's decisions for one chapter: accepted suggestions in order
     * (a whole chapter first, then paragraphs, then replacements), outdated ones marked
     * stale, the rest rejected; everything accepted becomes one revision.
     */
    @Transactional
    ReviewResult review(Viewer reviewer, long editionId, int number, List<Decision> decisions, String note) {
        CurrentText current = chapters.current(editionId, number);
        Map<Long, Decision> byId = new LinkedHashMap<>();
        decisions.forEach(decision -> byId.put(decision.id(), decision));
        List<SuggestionRecord> pending = db.selectFrom(SUGGESTION)
                .where(SUGGESTION.CHAPTER_ID.eq(current.chapterId()).and(SUGGESTION.STATE.eq("pending"))
                        .and(SUGGESTION.ID.in(byId.keySet())))
                .orderBy(SUGGESTION.CREATED_AT)
                .fetch();
        if (pending.size() != byId.size()) {
            throw UserFacingException.conflict("Частину правок уже перевірили або відкликали. Відкрийте перевірку ще раз.");
        }
        pending.sort((a, b) -> Integer.compare(order(a.getKind()), order(b.getKind())));

        String title = current.title();
        List<Block> blocks = current.blocks();
        Map<Long, ChangeStats> credits = new HashMap<>();
        List<Long> accepted = new ArrayList<>();
        List<Long> rejected = new ArrayList<>();
        List<Long> stale = new ArrayList<>();
        for (SuggestionRecord suggestion : pending) {
            Decision decision = byId.get(suggestion.getId());
            if (!decision.accept()) {
                rejected.add(suggestion.getId());
                continue;
            }
            List<Block> before = blocks;
            List<Block> after = apply(suggestion, decision, blocks, current.revisionId());
            if (after == null) {
                stale.add(suggestion.getId());
                continue;
            }
            if (suggestion.getKind().equals("chapter")) {
                title = json.readValue(suggestion.getProposed().data(), ChapterDraft.class).title();
            }
            blocks = after;
            accepted.add(suggestion.getId());
            ChangeStats stats = ChangeStats.between(before, after);
            credits.merge(suggestion.getAuthorId(), stats,
                    (a, b) -> new ChangeStats(a.blocksChanged() + b.blocksChanged(), a.charsChanged() + b.charsChanged()));
        }
        Long revisionId = accepted.isEmpty() ? null
                : chapters.publishFromSuggestions(editionId, number, title, blocks, current.revisionId(), reviewer.accountId(), credits);
        OffsetDateTime now = now();
        String reviewNote = note == null || note.isBlank() ? null : note.strip();
        mark(accepted, "accepted", reviewer, now, reviewNote, revisionId);
        mark(rejected, "rejected", reviewer, now, reviewNote, null);
        mark(stale, "stale", reviewer, now, reviewNote, null);
        Map<Long, int[]> byAuthor = new LinkedHashMap<>();
        for (SuggestionRecord suggestion : pending) {
            int[] counts = byAuthor.computeIfAbsent(suggestion.getAuthorId(), id -> new int[2]);
            if (accepted.contains(suggestion.getId())) {
                counts[0]++;
            } else if (rejected.contains(suggestion.getId())) {
                counts[1]++;
            }
        }
        byAuthor.forEach((author, counts) -> {
            if (author != reviewer.accountId() && counts[0] + counts[1] > 0) {
                events.publishEvent(new SuggestionsReviewed(editionId, number, author, counts[0], counts[1]));
            }
        });
        return new ReviewResult(revisionId, accepted.size(), rejected.size(), stale.size());
    }

    /** The chapter after one suggestion, or null if it no longer fits the text. */
    private List<Block> apply(SuggestionRecord suggestion, Decision decision, List<Block> blocks, long currentRevision) {
        switch (suggestion.getKind()) {
            case "chapter": {
                if (suggestion.getBaseRevisionId() != currentRevision) {
                    return null;
                }
                return json.readValue(suggestion.getProposed().data(), ChapterDraft.class).blocks();
            }
            case "block": {
                List<Span> proposed = decision.content() != null ? spans(decision.content())
                        : json.readValue(suggestion.getProposed().data(), SPANS);
                List<Block> out = new ArrayList<>(blocks.size());
                boolean found = false;
                for (Block block : blocks) {
                    if (block.id().equals(suggestion.getBlockId()) && block.text().equals(suggestion.getOriginalText())) {
                        out.add(new Block(block.id(), block.type(), proposed, null, null));
                        found = true;
                    } else {
                        out.add(block);
                    }
                }
                return found ? out : null;
            }
            case "replace": {
                if (occurrences(blocks, suggestion.getFindText()) == 0) {
                    return null;
                }
                return replaceAll(blocks, suggestion.getFindText(), suggestion.getReplacement());
            }
            default:
                return null;
        }
    }

    /** Replacement works inside runs of the same formatting; a match across a mark boundary is left alone. */
    static List<Block> replaceAll(List<Block> blocks, String find, String replacement) {
        return blocks.stream().map(block -> block.content().isEmpty() ? block
                : new Block(block.id(), block.type(), block.content().stream()
                        .map(span -> new Span(span.text().replace(find, replacement), span.marks())).toList(),
                        block.imageId(), block.sourceUrl())).toList();
    }

    static int occurrences(List<Block> blocks, String find) {
        int count = 0;
        for (Block block : blocks) {
            for (Span span : block.content()) {
                for (int at = span.text().indexOf(find); at >= 0; at = span.text().indexOf(find, at + find.length())) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** The published text of a chapter the viewer may read. */
    CurrentText readable(Viewer viewer, long editionId, int number) {
        var edition = EDITION.ID.eq(editionId).and(EDITION.HIDDEN_AT.isNull());
        if (!db.fetchExists(EDITION, viewer.adultConfirmed() ? edition : edition.and(EDITION.ADULT.isFalse()))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
        return chapters.current(editionId, number);
    }

    private void mark(List<Long> ids, String state, Viewer reviewer, OffsetDateTime at, String note, Long revisionId) {
        if (ids.isEmpty()) {
            return;
        }
        db.update(SUGGESTION)
                .set(SUGGESTION.STATE, state)
                .set(SUGGESTION.REVIEWER_ID, reviewer.accountId())
                .set(SUGGESTION.REVIEWED_AT, at)
                .set(SUGGESTION.REVIEW_NOTE, note)
                .set(SUGGESTION.APPLIED_REVISION_ID, revisionId)
                .set(SUGGESTION.UPDATED_AT, at)
                .where(SUGGESTION.ID.in(ids))
                .execute();
    }

    private static int order(String kind) {
        return switch (kind) {
            case "chapter" -> 0;
            case "block" -> 1;
            default -> 2;
        };
    }

    private static List<Span> spans(List<Span> content) {
        if (content == null || content.isEmpty()) {
            throw UserFacingException.badRequest("Абзац не може бути порожнім.");
        }
        List<Block> checked = BlockRules.normalize(List.of(Block.paragraph("x", content)));
        return checked.getFirst().content();
    }

    private static String plain(List<Span> spans) {
        StringBuilder out = new StringBuilder();
        spans.forEach(span -> out.append(span.text()));
        return out.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
