package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
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

    /**
     * Remembers where the reader is. Reading an edition that is not in the library yet
     * puts it into «Читаю», so it shows up there without an extra tap.
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
        OffsetDateTime now = now();
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

    private void requireVisible(Viewer viewer, long editionId) {
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId).and(ReadingQueries.visible(viewer.adultConfirmed())))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
