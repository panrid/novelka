package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER_ANALYSIS;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.text.ChapterLabels;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * What analysis decided about a chapter before its translation: the Ukrainian title and the
 * number readers see. The owner may correct both before starting the translation.
 */
@Component
class Analyses {

    private final DSLContext db;

    Analyses(DSLContext db) {
        this.db = db;
    }

    record Analysis(int number, long sourceChapterId, String title, String label, boolean edited) {
    }

    Optional<Analysis> find(long editionId, int number) {
        return db.selectFrom(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.eq(number))
                .fetchOptional(r -> new Analysis(r.getNumber(), r.getSourceChapterId(), r.getTitle(), r.getLabel(), r.getEdited()));
    }

    List<Analysis> from(long editionId, int firstNumber) {
        return db.selectFrom(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.ge(firstNumber))
                .orderBy(CHAPTER_ANALYSIS.NUMBER)
                .fetch(r -> new Analysis(r.getNumber(), r.getSourceChapterId(), r.getTitle(), r.getLabel(), r.getEdited()));
    }

    static final int PAGE = 50;

    record Page(List<Analysis> items, int total, int page, boolean hasMore) {
    }

    Page page(long editionId, int page) {
        int total = db.fetchCount(CHAPTER_ANALYSIS, CHAPTER_ANALYSIS.EDITION_ID.eq(editionId));
        int at = Math.max(1, page);
        List<Analysis> items = db.selectFrom(CHAPTER_ANALYSIS).where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId))
                .orderBy(CHAPTER_ANALYSIS.NUMBER).limit(PAGE).offset((at - 1) * PAGE)
                .fetch(r -> new Analysis(r.getNumber(), r.getSourceChapterId(), r.getTitle(), r.getLabel(), r.getEdited()));
        return new Page(items, total, at, at * PAGE < total);
    }

    int lastAnalyzed(long editionId) {
        return db.select(DSL.coalesce(DSL.max(CHAPTER_ANALYSIS.NUMBER), 0)).from(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class);
    }

    /**
     * Label from the original title: its number; none for a prologue or side story, or when
     * the novel numbers its chapters but this one has no number; otherwise the position.
     */
    String label(long novelId, String originalTitle) {
        Optional<String> number = ChapterLabels.fromJapanese(originalTitle);
        if (number.isPresent()) {
            return number.get();
        }
        if (ChapterLabels.isSpecial(originalTitle)) {
            return "";
        }
        boolean numbered = db.select(SOURCE_CHAPTER.TITLE).from(SOURCE_CHAPTER).where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId))
                .fetch(SOURCE_CHAPTER.TITLE).stream().anyMatch(title -> ChapterLabels.fromJapanese(title).isPresent());
        return numbered ? "" : null;
    }

    void save(long editionId, int number, long sourceChapterId, String title, String label, long jobId) {
        db.insertInto(CHAPTER_ANALYSIS)
                .set(CHAPTER_ANALYSIS.EDITION_ID, editionId)
                .set(CHAPTER_ANALYSIS.NUMBER, number)
                .set(CHAPTER_ANALYSIS.SOURCE_CHAPTER_ID, sourceChapterId)
                .set(CHAPTER_ANALYSIS.TITLE, title)
                .set(CHAPTER_ANALYSIS.LABEL, label)
                .set(CHAPTER_ANALYSIS.JOB_ID, jobId)
                .onConflict(CHAPTER_ANALYSIS.EDITION_ID, CHAPTER_ANALYSIS.NUMBER).doUpdate()
                .set(CHAPTER_ANALYSIS.SOURCE_CHAPTER_ID, sourceChapterId)
                .set(CHAPTER_ANALYSIS.TITLE, title)
                .set(CHAPTER_ANALYSIS.LABEL, label)
                .set(CHAPTER_ANALYSIS.JOB_ID, jobId)
                .set(CHAPTER_ANALYSIS.EDITED, false)
                .set(CHAPTER_ANALYSIS.UPDATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    void edit(long editionId, int number, String title, String label) {
        String name = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        String value = label == null ? null : label.strip().replace(',', '.');
        if (name.length() > 200) {
            throw UserFacingException.badRequest("Назва глави — до 200 символів.");
        }
        if (!ChapterLabels.valid(value)) {
            throw UserFacingException.badRequest("Номер — число, можна з крапкою: 0, 12, 31.1. Порожньо — без номера.");
        }
        int changed = db.update(CHAPTER_ANALYSIS)
                .set(CHAPTER_ANALYSIS.TITLE, name)
                .set(CHAPTER_ANALYSIS.LABEL, value)
                .set(CHAPTER_ANALYSIS.EDITED, true)
                .set(CHAPTER_ANALYSIS.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.eq(number))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Цю главу ще не аналізували.");
        }
    }
}
