package space.panrid.novelka.text.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.REVISION;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.text.ChapterFiles;
import space.panrid.novelka.text.Chapters;
import tools.jackson.databind.json.JsonMapper;

@Service
class ChapterService implements Chapters {

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
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        // Row lock on the edition: two uploads at once must not take the same numbers.
        db.execute("SELECT 1 FROM edition WHERE id = ? FOR UPDATE", editionId);
        int next = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), 0)).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class) + 1;
        List<Integer> numbers = new ArrayList<>();
        for (ChapterFiles.ParsedChapter parsed : chapters) {
            long chapterId = db.insertInto(CHAPTER)
                    .set(CHAPTER.EDITION_ID, editionId)
                    .set(CHAPTER.NUMBER, next)
                    .set(CHAPTER.FIRST_PUBLISHED_AT, now)
                    .set(CHAPTER.UPDATED_AT, now)
                    .returning(CHAPTER.ID)
                    .fetchOne(CHAPTER.ID);
            long revisionId = db.insertInto(REVISION)
                    .set(REVISION.CHAPTER_ID, chapterId)
                    .set(REVISION.TITLE, parsed.title())
                    .set(REVISION.BLOCKS, JSONB.valueOf(json.writeValueAsString(parsed.blocks())))
                    .set(REVISION.ORIGIN, origin)
                    .set(REVISION.AUTHOR_ID, authorId)
                    .set(REVISION.CREATED_AT, now)
                    .returning(REVISION.ID)
                    .fetchOne(REVISION.ID);
            db.update(CHAPTER).set(CHAPTER.PUBLISHED_REVISION_ID, revisionId).where(CHAPTER.ID.eq(chapterId)).execute();
            numbers.add(next++);
        }
        int published = db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        catalog.recordPublication(editionId, published, now);
        return numbers;
    }
}
