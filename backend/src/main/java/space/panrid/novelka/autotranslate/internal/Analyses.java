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

    /** @param narrator who tells the chapter in the first person (Ukrainian name), or null */
    record Analysis(int number, long sourceChapterId, String title, String label, boolean edited, String narrator,
            String narratorGender) {
    }

    private static Analysis analysis(space.panrid.novelka.jooq.tables.records.ChapterAnalysisRecord r) {
        return new Analysis(r.getNumber(), r.getSourceChapterId(), r.getTitle(), r.getLabel(), r.getEdited(), r.getNarrator(),
                r.getNarratorGender());
    }

    Optional<Analysis> find(long editionId, int number) {
        return db.selectFrom(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.eq(number))
                .fetchOptional(Analyses::analysis);
    }

    List<Analysis> from(long editionId, int firstNumber) {
        return db.selectFrom(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.ge(firstNumber))
                .orderBy(CHAPTER_ANALYSIS.NUMBER)
                .fetch(Analyses::analysis);
    }

    static final int PAGE = 20;

    record Page(List<Analysis> items, int total, int page, boolean hasMore) {
    }

    Page page(long editionId, int page) {
        int total = db.fetchCount(CHAPTER_ANALYSIS, CHAPTER_ANALYSIS.EDITION_ID.eq(editionId));
        int at = Math.max(1, page);
        List<Analysis> items = db.selectFrom(CHAPTER_ANALYSIS).where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId))
                .orderBy(CHAPTER_ANALYSIS.NUMBER).limit(PAGE).offset((at - 1) * PAGE)
                .fetch(Analyses::analysis);
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
        // The novel numbers its chapters in the titles when most of them carry a number; one title
        // that happens to start like one («第二の魔法…») must not leave all the others without numbers.
        List<String> titles = db.select(SOURCE_CHAPTER.TITLE).from(SOURCE_CHAPTER).where(SOURCE_CHAPTER.NOVEL_ID.eq(novelId))
                .fetch(SOURCE_CHAPTER.TITLE);
        long numbered = titles.stream().filter(title -> ChapterLabels.fromJapanese(title).isPresent()).count();
        return numbered * 2 > titles.size() ? "" : null;
    }

    void save(long editionId, int number, long sourceChapterId, String title, String label, String narrator,
            String narratorGender, long jobId) {
        String name = narrator == null || narrator.isBlank() ? null : narrator.strip();
        if (name != null && name.length() > 100) {
            name = name.substring(0, 100);
        }
        String gender = name == null || !Glossary.GENDERS.contains(narratorGender) ? null : narratorGender;
        db.insertInto(CHAPTER_ANALYSIS)
                .set(CHAPTER_ANALYSIS.EDITION_ID, editionId)
                .set(CHAPTER_ANALYSIS.NUMBER, number)
                .set(CHAPTER_ANALYSIS.SOURCE_CHAPTER_ID, sourceChapterId)
                .set(CHAPTER_ANALYSIS.TITLE, title)
                .set(CHAPTER_ANALYSIS.LABEL, label)
                .set(CHAPTER_ANALYSIS.NARRATOR, name)
                .set(CHAPTER_ANALYSIS.NARRATOR_GENDER, gender)
                .set(CHAPTER_ANALYSIS.JOB_ID, jobId)
                .onConflict(CHAPTER_ANALYSIS.EDITION_ID, CHAPTER_ANALYSIS.NUMBER).doUpdate()
                .set(CHAPTER_ANALYSIS.SOURCE_CHAPTER_ID, sourceChapterId)
                .set(CHAPTER_ANALYSIS.TITLE, title)
                .set(CHAPTER_ANALYSIS.LABEL, label)
                .set(CHAPTER_ANALYSIS.NARRATOR, name)
                .set(CHAPTER_ANALYSIS.NARRATOR_GENDER, gender)
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
