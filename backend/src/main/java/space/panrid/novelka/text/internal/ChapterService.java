package space.panrid.novelka.text.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CONTRIBUTION;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITOR_DRAFT;
import static space.panrid.novelka.jooq.Tables.REVISION;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.jooq.tables.records.ChapterRecord;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.BlockRules;
import space.panrid.novelka.text.ChangeStats;
import space.panrid.novelka.text.ChapterFiles;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels.Contribution;
import space.panrid.novelka.text.EditorModels.Draft;
import space.panrid.novelka.text.EditorModels.EditorState;
import space.panrid.novelka.text.EditorModels.RevisionInfo;
import space.panrid.novelka.text.EditorModels.RevisionText;
import space.panrid.novelka.text.EditorModels.StudioChapter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class ChapterService implements Chapters {

    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;
    private final Catalog catalog;
    private final Clock clock;

    ChapterService(DSLContext db, JsonMapper json, Catalog catalog, Clock clock) {
        this.db = db;
        this.json = json;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<Integer> publishNew(long editionId, List<ChapterFiles.ParsedChapter> chapters, String origin, Long authorId) {
        OffsetDateTime now = now();
        int next = nextNumber(editionId);
        List<Integer> numbers = new ArrayList<>();
        for (ChapterFiles.ParsedChapter parsed : chapters) {
            long chapterId = db.insertInto(CHAPTER)
                    .set(CHAPTER.EDITION_ID, editionId)
                    .set(CHAPTER.NUMBER, next)
                    .set(CHAPTER.FIRST_PUBLISHED_AT, now)
                    .set(CHAPTER.UPDATED_AT, now)
                    .returning(CHAPTER.ID)
                    .fetchOne(CHAPTER.ID);
            long revisionId = insertRevision(chapterId, null, parsed.title(), parsed.blocks(), origin, authorId, now);
            if (authorId != null) {
                ChangeStats stats = ChangeStats.between(List.of(), parsed.blocks());
                recordContribution(revisionId, authorId, stats);
            }
            db.update(CHAPTER).set(CHAPTER.PUBLISHED_REVISION_ID, revisionId).where(CHAPTER.ID.eq(chapterId)).execute();
            numbers.add(next++);
        }
        refreshCounters(editionId, now);
        return numbers;
    }

    @Override
    @Transactional
    public int createChapter(long editionId) {
        int number = nextNumber(editionId);
        db.insertInto(CHAPTER).set(CHAPTER.EDITION_ID, editionId).set(CHAPTER.NUMBER, number)
                .set(CHAPTER.UPDATED_AT, now()).execute();
        return number;
    }

    @Override
    public EditorState editorState(long editionId, int number, long accountId) {
        ChapterRecord chapter = chapter(editionId, number);
        Record published = chapter.getPublishedRevisionId() == null ? null
                : db.select(REVISION.TITLE, REVISION.BLOCKS).from(REVISION)
                        .where(REVISION.ID.eq(chapter.getPublishedRevisionId())).fetchOne();
        Draft draft = db.selectFrom(EDITOR_DRAFT)
                .where(EDITOR_DRAFT.CHAPTER_ID.eq(chapter.getId()).and(EDITOR_DRAFT.ACCOUNT_ID.eq(accountId)))
                .fetchOptional(r -> new Draft(r.getTitle(), blocks(r.getBlocks()), r.getBaseRevisionId(), r.getUpdatedAt()))
                .orElse(null);
        return new EditorState(chapter.getId(), number,
                published == null ? "" : published.get(REVISION.TITLE),
                published == null ? List.of() : blocks(published.get(REVISION.BLOCKS)),
                chapter.getPublishedRevisionId(), chapter.getPublishedRevisionId() != null, draft);
    }

    @Override
    @Transactional
    public void saveDraft(long editionId, int number, long accountId, String title, List<Block> blocks, Long baseRevisionId) {
        ChapterRecord chapter = chapter(editionId, number);
        // A draft may be half-written: only sizes and structure are checked, not emptiness.
        List<Block> kept = blocks == null ? List.of() : blocks.stream().limit(BlockRules.MAX_BLOCKS).toList();
        String json = this.json.writeValueAsString(kept);
        if (json.length() > 2 * BlockRules.MAX_CHAPTER_CHARS + 200_000) {
            throw UserFacingException.badRequest("Чернетка завелика.");
        }
        OffsetDateTime now = now();
        String safeTitle = title == null ? "" : title.strip();
        db.insertInto(EDITOR_DRAFT)
                .set(EDITOR_DRAFT.CHAPTER_ID, chapter.getId())
                .set(EDITOR_DRAFT.ACCOUNT_ID, accountId)
                .set(EDITOR_DRAFT.BASE_REVISION_ID, baseRevisionId)
                .set(EDITOR_DRAFT.TITLE, safeTitle.length() > BlockRules.TITLE_MAX ? safeTitle.substring(0, BlockRules.TITLE_MAX) : safeTitle)
                .set(EDITOR_DRAFT.BLOCKS, JSONB.valueOf(json))
                .set(EDITOR_DRAFT.UPDATED_AT, now)
                .onConflict(EDITOR_DRAFT.CHAPTER_ID, EDITOR_DRAFT.ACCOUNT_ID)
                .doUpdate()
                .set(EDITOR_DRAFT.BASE_REVISION_ID, baseRevisionId)
                .set(EDITOR_DRAFT.TITLE, DSL.excluded(EDITOR_DRAFT.TITLE))
                .set(EDITOR_DRAFT.BLOCKS, DSL.excluded(EDITOR_DRAFT.BLOCKS))
                .set(EDITOR_DRAFT.UPDATED_AT, now)
                .execute();
    }

    @Override
    @Transactional
    public void discardDraft(long editionId, int number, long accountId) {
        ChapterRecord chapter = chapter(editionId, number);
        db.deleteFrom(EDITOR_DRAFT)
                .where(EDITOR_DRAFT.CHAPTER_ID.eq(chapter.getId()).and(EDITOR_DRAFT.ACCOUNT_ID.eq(accountId)))
                .execute();
    }

    @Override
    @Transactional
    public long publish(long editionId, int number, long accountId, String rawTitle, List<Block> rawBlocks,
            Long baseRevisionId, boolean mayAddPictures) {
        String title = BlockRules.title(rawTitle);
        List<Block> blocks = BlockRules.normalize(rawBlocks);
        // Lock the chapter row: two people pressing «Опублікувати» at once are serialised here.
        ChapterRecord chapter = db.selectFrom(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.NUMBER.eq(number)))
                .forUpdate().fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої глави немає."));
        Long current = chapter.getPublishedRevisionId();
        if (current != null && !current.equals(baseRevisionId)) {
            throw new UserFacingException(HttpStatus.CONFLICT,
                    "Поки ви редагували, главу оновив хтось інший. Ваші зміни збережено в чернетці: відкрийте главу ще раз і перенесіть їх.",
                    "chapter-changed");
        }
        List<Block> previous = current == null ? List.of()
                : blocks(db.select(REVISION.BLOCKS).from(REVISION).where(REVISION.ID.eq(current)).fetchOne(REVISION.BLOCKS));
        Set<Long> newPictures = new HashSet<>(BlockRules.images(blocks));
        newPictures.removeAll(BlockRules.images(previous));
        if (!newPictures.isEmpty() && !mayAddPictures) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Додавати картинки можуть лише власник і перекладачі.");
        }
        if (!newPictures.isEmpty()) {
            requirePicturesExist(newPictures);
        }
        OffsetDateTime now = now();
        long revisionId = insertRevision(chapter.getId(), current, title, blocks, "editor", accountId, now);
        recordContribution(revisionId, accountId, ChangeStats.between(previous, blocks));
        db.update(CHAPTER)
                .set(CHAPTER.PUBLISHED_REVISION_ID, revisionId)
                .set(CHAPTER.FIRST_PUBLISHED_AT, DSL.coalesce(CHAPTER.FIRST_PUBLISHED_AT, DSL.val(now)))
                .set(CHAPTER.UPDATED_AT, now)
                .where(CHAPTER.ID.eq(chapter.getId()))
                .execute();
        db.deleteFrom(EDITOR_DRAFT)
                .where(EDITOR_DRAFT.CHAPTER_ID.eq(chapter.getId()).and(EDITOR_DRAFT.ACCOUNT_ID.eq(accountId)))
                .execute();
        refreshCounters(editionId, current == null ? now : null);
        return revisionId;
    }

    @Override
    public List<StudioChapter> studioChapters(long editionId, long accountId, int page, int size) {
        var hasDraft = DSL.exists(DSL.selectOne().from(EDITOR_DRAFT)
                .where(EDITOR_DRAFT.CHAPTER_ID.eq(CHAPTER.ID).and(EDITOR_DRAFT.ACCOUNT_ID.eq(accountId))));
        return db.select(CHAPTER.NUMBER, REVISION.TITLE, CHAPTER.PUBLISHED_REVISION_ID, DSL.field(hasDraft), CHAPTER.UPDATED_AT)
                .from(CHAPTER).leftJoin(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId))
                .orderBy(CHAPTER.NUMBER.desc())
                .limit(size).offset((page - 1) * size)
                .fetch(r -> new StudioChapter(r.value1(), r.value2() == null ? "" : r.value2(), r.value3() != null,
                        r.value4(), r.value5()));
    }

    @Override
    public List<RevisionInfo> revisions(long editionId, int number) {
        ChapterRecord chapter = chapter(editionId, number);
        return db.select(REVISION.ID, ACCOUNT.NICK, REVISION.ORIGIN, REVISION.CREATED_AT,
                        DSL.coalesce(DSL.sum(CONTRIBUTION.BLOCKS_CHANGED), DSL.zero()),
                        DSL.coalesce(DSL.sum(CONTRIBUTION.CHARS_CHANGED), DSL.zero()))
                .from(REVISION)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(REVISION.AUTHOR_ID))
                .leftJoin(CONTRIBUTION).on(CONTRIBUTION.REVISION_ID.eq(REVISION.ID))
                .where(REVISION.CHAPTER_ID.eq(chapter.getId()))
                .groupBy(REVISION.ID, ACCOUNT.NICK, REVISION.ORIGIN, REVISION.CREATED_AT)
                .orderBy(REVISION.CREATED_AT.desc(), REVISION.ID.desc())
                .limit(200)
                .fetch(r -> new RevisionInfo(r.value1(), r.value2(), r.value3(), r.value4(), r.value5().intValue(),
                        r.value6().intValue(), r.value1().equals(chapter.getPublishedRevisionId())));
    }

    @Override
    public RevisionText revision(long editionId, int number, long revisionId) {
        ChapterRecord chapter = chapter(editionId, number);
        var revision = db.selectFrom(REVISION)
                .where(REVISION.ID.eq(revisionId).and(REVISION.CHAPTER_ID.eq(chapter.getId())))
                .fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої версії глави немає."));
        var parent = revision.getParentId() == null ? null : db.selectFrom(REVISION).where(REVISION.ID.eq(revision.getParentId())).fetchOne();
        return new RevisionText(revision.getId(), revision.getTitle(), blocks(revision.getBlocks()),
                parent == null ? null : parent.getTitle(), parent == null ? List.of() : blocks(parent.getBlocks()));
    }

    @Override
    public List<Contribution> contributions(long editionId) {
        return db.select(ACCOUNT.NICK, DSL.countDistinct(CONTRIBUTION.REVISION_ID),
                        DSL.sum(CONTRIBUTION.BLOCKS_CHANGED), DSL.sum(CONTRIBUTION.CHARS_CHANGED))
                .from(CONTRIBUTION)
                .join(REVISION).on(REVISION.ID.eq(CONTRIBUTION.REVISION_ID))
                .join(CHAPTER).on(CHAPTER.ID.eq(REVISION.CHAPTER_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(CONTRIBUTION.ACCOUNT_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId))
                .groupBy(ACCOUNT.NICK)
                .orderBy(DSL.sum(CONTRIBUTION.CHARS_CHANGED).desc())
                .fetch(r -> new Contribution(r.value1(), r.value2(), r.value3().intValue(), r.value4().intValue()));
    }

    // ---- helpers --------------------------------------------------------------------------

    /** The next free number; a continuation (естафета) starts where the previous team stopped. */
    private int nextNumber(long editionId) {
        db.execute("SELECT 1 FROM edition WHERE id = ? FOR UPDATE", editionId);
        int last = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), 0)).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class);
        int first = db.select(EDITION.FIRST_NUMBER).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne(EDITION.FIRST_NUMBER);
        return Math.max(last + 1, first);
    }

    private ChapterRecord chapter(long editionId, int number) {
        return db.selectFrom(CHAPTER).where(CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.NUMBER.eq(number)))
                .fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої глави немає."));
    }

    private long insertRevision(long chapterId, Long parentId, String title, List<Block> blocks, String origin,
            Long authorId, OffsetDateTime at) {
        return db.insertInto(REVISION)
                .set(REVISION.CHAPTER_ID, chapterId)
                .set(REVISION.PARENT_ID, parentId)
                .set(REVISION.TITLE, title)
                .set(REVISION.BLOCKS, JSONB.valueOf(json.writeValueAsString(blocks)))
                .set(REVISION.ORIGIN, origin)
                .set(REVISION.AUTHOR_ID, authorId)
                .set(REVISION.CREATED_AT, at)
                .returning(REVISION.ID)
                .fetchOne(REVISION.ID);
    }

    private void recordContribution(long revisionId, long accountId, ChangeStats stats) {
        db.insertInto(CONTRIBUTION)
                .set(CONTRIBUTION.REVISION_ID, revisionId)
                .set(CONTRIBUTION.ACCOUNT_ID, accountId)
                .set(CONTRIBUTION.BLOCKS_CHANGED, stats.blocksChanged())
                .set(CONTRIBUTION.CHARS_CHANGED, stats.charsChanged())
                .execute();
    }

    private void requirePicturesExist(Set<Long> ids) {
        int found = db.fetchCount(space.panrid.novelka.jooq.Tables.IMAGE,
                space.panrid.novelka.jooq.Tables.IMAGE.ID.in(ids).and(space.panrid.novelka.jooq.Tables.IMAGE.HIDDEN_AT.isNull()));
        if (found != ids.size()) {
            throw UserFacingException.badRequest("Однієї з картинок немає. Завантажте її ще раз.");
        }
    }

    /** Published chapter count always; «last published» only when a chapter appears for the first time. */
    private void refreshCounters(long editionId, OffsetDateTime newChapterAt) {
        int published = db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        OffsetDateTime last = newChapterAt != null ? newChapterAt
                : db.select(EDITION.LAST_PUBLISHED_AT).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne(EDITION.LAST_PUBLISHED_AT);
        catalog.recordPublication(editionId, published, last);
    }

    private List<Block> blocks(JSONB stored) {
        return stored == null ? List.of() : json.readValue(stored.data(), BLOCKS);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
