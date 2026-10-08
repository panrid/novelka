package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_READ;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_SUBSCRIPTION;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.web.UserFacingException;

@Service
class LibraryService {

    static final List<String> LISTS = List.of("reading", "planned", "done", "paused", "dropped");

    private final DSLContext db;
    private final Clock clock;

    LibraryService(DSLContext db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Puts the edition into one list, or removes it from the library when {@code list} is null. */
    @Transactional
    void setList(Viewer viewer, long editionId, String list) {
        requireVisible(viewer, editionId);
        if (list == null) {
            db.deleteFrom(LIBRARY_ENTRY)
                    .where(LIBRARY_ENTRY.ACCOUNT_ID.eq(viewer.accountId()).and(LIBRARY_ENTRY.EDITION_ID.eq(editionId)))
                    .execute();
            return;
        }
        if (!LISTS.contains(list)) {
            throw UserFacingException.badRequest("Такого списку немає.");
        }
        OffsetDateTime now = now();
        db.insertInto(LIBRARY_ENTRY)
                .set(LIBRARY_ENTRY.ACCOUNT_ID, viewer.accountId())
                .set(LIBRARY_ENTRY.EDITION_ID, editionId)
                .set(LIBRARY_ENTRY.LIST, list)
                .set(LIBRARY_ENTRY.UPDATED_AT, now)
                .onConflict(LIBRARY_ENTRY.ACCOUNT_ID, LIBRARY_ENTRY.EDITION_ID)
                .doUpdate().set(LIBRARY_ENTRY.LIST, list).set(LIBRARY_ENTRY.UPDATED_AT, now)
                .execute();
    }

    /** The bell on the translation's page: new chapters reach the reader only while it rings. */
    void subscribe(Viewer viewer, long editionId, boolean on) {
        requireVisible(viewer, editionId);
        if (on) {
            db.insertInto(EDITION_SUBSCRIPTION)
                    .set(EDITION_SUBSCRIPTION.ACCOUNT_ID, viewer.accountId())
                    .set(EDITION_SUBSCRIPTION.EDITION_ID, editionId)
                    .set(EDITION_SUBSCRIPTION.CREATED_AT, now())
                    .onConflictDoNothing()
                    .execute();
        } else {
            db.deleteFrom(EDITION_SUBSCRIPTION)
                    .where(EDITION_SUBSCRIPTION.ACCOUNT_ID.eq(viewer.accountId()), EDITION_SUBSCRIPTION.EDITION_ID.eq(editionId))
                    .execute();
        }
    }

    /** Read this far, a chapter counts as read; an earlier one becomes the place again. */
    static final float FINISHED = 0.9f;
    /** Read this far into a later chapter, it becomes the place: opening one is not reading it. */
    static final float STARTED = 0.15f;
    private static final int RANGE_MAX = 10_000;

    /**
     * Remembers where the reader is and which chapters they finished. Reading an edition that is
     * not in the library yet puts it into «Читаю», so it shows up there without an extra tap.
     * A later chapter becomes the place once really begun, an earlier one once read to its end:
     * a look at chapter 17 (a suggestion, a glossary word) must not lose chapter 20, nor a peek
     * at chapter 40 skip twenty chapters.
     */
    @Transactional
    void saveProgress(Viewer viewer, long editionId, int chapterNumber, float position) {
        requireVisible(viewer, editionId);
        boolean published = db.fetchExists(CHAPTER, CHAPTER.EDITION_ID.eq(editionId)
                .and(CHAPTER.NUMBER.eq(chapterNumber)).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        if (!published) {
            throw UserFacingException.notFound("Такої глави немає.");
        }
        float clamped = Math.max(0f, Math.min(1f, position));
        Integer saved = db.select(READING_PROGRESS.CHAPTER_NUMBER).from(READING_PROGRESS)
                .where(READING_PROGRESS.ACCOUNT_ID.eq(viewer.accountId()), READING_PROGRESS.EDITION_ID.eq(editionId))
                .fetchOne(READING_PROGRESS.CHAPTER_NUMBER);
        OffsetDateTime now = now();
        if (clamped >= FINISHED) {
            db.insertInto(CHAPTER_READ).set(CHAPTER_READ.ACCOUNT_ID, viewer.accountId()).set(CHAPTER_READ.EDITION_ID, editionId)
                    .set(CHAPTER_READ.CHAPTER_NUMBER, chapterNumber).set(CHAPTER_READ.READ_AT, now).onConflictDoNothing().execute();
        }
        boolean moves = saved == null || saved == chapterNumber || clamped >= FINISHED || chapterNumber > saved && clamped >= STARTED;
        if (!moves) {
            return;
        }
        db.insertInto(READING_PROGRESS)
                .set(READING_PROGRESS.ACCOUNT_ID, viewer.accountId())
                .set(READING_PROGRESS.EDITION_ID, editionId)
                .set(READING_PROGRESS.CHAPTER_NUMBER, chapterNumber)
                .set(READING_PROGRESS.POSITION, clamped)
                .set(READING_PROGRESS.UPDATED_AT, now)
                .onConflict(READING_PROGRESS.ACCOUNT_ID, READING_PROGRESS.EDITION_ID)
                .doUpdate()
                .set(READING_PROGRESS.CHAPTER_NUMBER, chapterNumber)
                .set(READING_PROGRESS.POSITION, clamped)
                .set(READING_PROGRESS.UPDATED_AT, now)
                .execute();
        db.insertInto(LIBRARY_ENTRY)
                .set(LIBRARY_ENTRY.ACCOUNT_ID, viewer.accountId())
                .set(LIBRARY_ENTRY.EDITION_ID, editionId)
                .set(LIBRARY_ENTRY.LIST, "reading")
                .set(LIBRARY_ENTRY.UPDATED_AT, now)
                .onConflictDoNothing()
                .execute();
    }

    /** «Прочитано» by hand: one chapter, «усі до цієї», or the skipped ones; {@code read=false} unmarks them. */
    @Transactional
    void markRead(Viewer viewer, long editionId, int from, int to, boolean read) {
        requireVisible(viewer, editionId);
        if (from < 1 || to < from || to - from > RANGE_MAX) {
            throw UserFacingException.badRequest("Не ті номери глав.");
        }
        if (!read) {
            db.deleteFrom(CHAPTER_READ).where(CHAPTER_READ.ACCOUNT_ID.eq(viewer.accountId()), CHAPTER_READ.EDITION_ID.eq(editionId),
                    CHAPTER_READ.CHAPTER_NUMBER.between(from, to)).execute();
            return;
        }
        db.insertInto(CHAPTER_READ, CHAPTER_READ.ACCOUNT_ID, CHAPTER_READ.EDITION_ID, CHAPTER_READ.CHAPTER_NUMBER, CHAPTER_READ.READ_AT)
                .select(DSL.select(DSL.val(viewer.accountId()), DSL.val(editionId), CHAPTER.NUMBER, DSL.val(now())).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull(),
                                CHAPTER.NUMBER.between(from, to)))
                .onConflictDoNothing().execute();
    }

    /** «Скинути прогрес»: no place and nothing read; the novel stays in its library list. */
    @Transactional
    void resetProgress(Viewer viewer, long editionId) {
        db.deleteFrom(CHAPTER_READ).where(CHAPTER_READ.ACCOUNT_ID.eq(viewer.accountId()), CHAPTER_READ.EDITION_ID.eq(editionId)).execute();
        db.deleteFrom(READING_PROGRESS)
                .where(READING_PROGRESS.ACCOUNT_ID.eq(viewer.accountId()), READING_PROGRESS.EDITION_ID.eq(editionId)).execute();
    }

    private void requireVisible(Viewer viewer, long editionId) {
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId).and(ReadingQueries.visible(viewer.adultConfirmed())))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
