package space.panrid.novelka.reading.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_STAT;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Popularity counted ahead (етап 21.5): triggers of V57 add readers and library entries as they
 * come; once an hour the counts are taken again, so readers away for over a month drop out.
 */
@Component
class EditionStats {

    private final DSLContext db;
    private final Clock clock;

    EditionStats(DSLContext db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Counts readers of the last 30 days and library entries of every translation again. */
    int recount() {
        OffsetDateTime monthAgo = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).minusDays(30);
        var readers = DSL.select(DSL.count()).from(READING_PROGRESS)
                .where(READING_PROGRESS.EDITION_ID.eq(EDITION.ID), READING_PROGRESS.UPDATED_AT.gt(monthAgo)).asField();
        var shelved = DSL.select(DSL.count()).from(LIBRARY_ENTRY)
                .where(LIBRARY_ENTRY.EDITION_ID.eq(EDITION.ID), LIBRARY_ENTRY.LIST.in("reading", "planned", "done")).asField();
        return db.insertInto(EDITION_STAT, EDITION_STAT.EDITION_ID, EDITION_STAT.SHELVED, EDITION_STAT.READERS_MONTH)
                .select(DSL.select(EDITION.ID, shelved.cast(Integer.class), readers.cast(Integer.class)).from(EDITION))
                .onConflict(EDITION_STAT.EDITION_ID).doUpdate()
                .set(EDITION_STAT.SHELVED, DSL.excluded(EDITION_STAT.SHELVED))
                .set(EDITION_STAT.READERS_MONTH, DSL.excluded(EDITION_STAT.READERS_MONTH))
                .where(EDITION_STAT.SHELVED.ne(DSL.excluded(EDITION_STAT.SHELVED))
                        .or(EDITION_STAT.READERS_MONTH.ne(DSL.excluded(EDITION_STAT.READERS_MONTH))))
                .execute();
    }
}
