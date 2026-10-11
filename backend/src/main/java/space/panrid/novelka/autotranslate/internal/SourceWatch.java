package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_WATCH;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import space.panrid.novelka.autotranslate.SourceChaptersFound;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.SourceNovel;
import space.panrid.novelka.source.Sources;

/**
 * Once a week every novel taken from a source site is looked up there again: when its original
 * grew, the people who rang the Studio's bell for a translation of it hear that chapters wait.
 * Only the table of contents is read; chapters are downloaded when a run translates them.
 */
@Component
class SourceWatch {

    private static final Logger log = LoggerFactory.getLogger(SourceWatch.class);
    static final Duration EVERY = Duration.ofDays(7);
    /** Novels looked up in one go, so a long list does not knock on the source sites all at once. */
    private static final int AT_ONCE = 5;

    record Checked(int before, int now) {

        int found() {
            return Math.max(0, now - before);
        }
    }

    private final DSLContext db;
    private final Sources sources;
    private final Catalog catalog;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    SourceWatch(DSLContext db, Sources sources, Catalog catalog, ApplicationEventPublisher events, TransactionTemplate transactions,
            Clock clock) {
        this.db = db;
        this.sources = sources;
        this.catalog = catalog;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Every hour a few novels whose week is up; the week of each runs from its own last look. */
    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    void weekly() {
        OffsetDateTime due = now().minus(EVERY);
        List<Long> novels = db.select(NOVEL.ID).from(NOVEL)
                .where(NOVEL.SOURCE_KEY.isNotNull(), NOVEL.SOURCE.notIn("manual", "original"),
                        NOVEL.SOURCE_CHECKED_AT.isNull().or(NOVEL.SOURCE_CHECKED_AT.lt(due)),
                        DSL.exists(DSL.selectOne().from(EDITION).where(EDITION.NOVEL_ID.eq(NOVEL.ID))))
                .orderBy(NOVEL.SOURCE_CHECKED_AT.asc().nullsFirst()).limit(AT_ONCE).fetch(NOVEL.ID);
        for (long novel : novels) {
            try {
                check(novel);
            } catch (RuntimeException failed) {
                // A site that is down is asked again next week; the others go on.
                log.warn("Source of novel {} not checked: {}", novel, failed.getMessage());
                db.update(NOVEL).set(NOVEL.SOURCE_CHECKED_AT, now()).where(NOVEL.ID.eq(novel)).execute();
            }
        }
    }

    /** Reads the novel's table of contents on its site (slow: no transaction around it) and tells who waits. */
    Checked check(long novelId) {
        var row = db.select(NOVEL.SOURCE, NOVEL.SOURCE_KEY, NOVEL.SOURCE_URL, NOVEL.SOURCE_CHAPTER_COUNT).from(NOVEL)
                .where(NOVEL.ID.eq(novelId), NOVEL.SOURCE_KEY.isNotNull()).fetchOptional()
                .orElseThrow(() -> UserFacingException.badRequest("Новела не з сайту-джерела."));
        int before = row.value4() == null ? 0 : row.value4();
        SourceNovel novel = sources.novel(new SourceLink(row.value1(), row.value2(), row.value3(), false));
        int now = novel.lastAvailable();
        transactions.executeWithoutResult(status -> {
            sources.keep(novelId, novel);
            catalog.updateSource(novelId, Math.max(now, before), novel.completed());
            db.update(NOVEL).set(NOVEL.SOURCE_CHECKED_AT, now()).where(NOVEL.ID.eq(novelId)).execute();
            if (now > before) {
                for (long edition : db.select(EDITION.ID).from(EDITION).where(EDITION.NOVEL_ID.eq(novelId)).fetch(EDITION.ID)) {
                    events.publishEvent(new SourceChaptersFound(edition, before + 1, now));
                }
            }
        });
        return new Checked(before, Math.max(now, before));
    }

    boolean watching(long accountId, long editionId) {
        return db.fetchExists(SOURCE_WATCH, SOURCE_WATCH.ACCOUNT_ID.eq(accountId).and(SOURCE_WATCH.EDITION_ID.eq(editionId)));
    }

    void watch(long accountId, long editionId, boolean on) {
        if (on) {
            db.insertInto(SOURCE_WATCH).set(SOURCE_WATCH.ACCOUNT_ID, accountId).set(SOURCE_WATCH.EDITION_ID, editionId)
                    .onConflictDoNothing().execute();
        } else {
            db.deleteFrom(SOURCE_WATCH).where(SOURCE_WATCH.ACCOUNT_ID.eq(accountId), SOURCE_WATCH.EDITION_ID.eq(editionId)).execute();
        }
    }

    OffsetDateTime checkedAt(long editionId) {
        return db.select(NOVEL.SOURCE_CHECKED_AT).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchOne(NOVEL.SOURCE_CHECKED_AT);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
