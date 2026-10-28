package space.panrid.novelka.notification.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_NEWS;
import static space.panrid.novelka.jooq.Tables.CHAPTER_NEWS_RECIPIENT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_SUBSCRIPTION;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jooq.DSLContext;
import org.jooq.InsertValuesStep2;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import space.panrid.novelka.access.Visibility;
import space.panrid.novelka.jooq.tables.records.ChapterNewsRecord;
import space.panrid.novelka.jooq.tables.records.ChapterNewsRecipientRecord;
import space.panrid.novelka.notification.ChapterNewsBatch;
import space.panrid.novelka.notification.ChapterNewsQueue;
import space.panrid.novelka.text.ChaptersPublished;

/**
 * Новина про нові глави (план 21.9). Publishing a chapter only queues one {@code chapter_news}
 * row in its own transaction; this worker then tells the readers who rang the bell, {@link #BATCH}
 * at a time in account order. Each batch is one transaction: its inbox rows, the readers marked
 * as told ({@code chapter_news_recipient}), the Telegram messages queued by listeners of
 * {@link ChapterNewsBatch} and the cursor move together, so a crash or a retry never tells anyone
 * twice. Chapters unpublished, deleted or hidden before the end stop the rest of the batches.
 */
@Component
class ChapterNews implements ChapterNewsQueue {

    static final int BATCH = 500;
    static final String KIND = "new_chapters";
    /** After this many failures in a row the job stops for good (and is logged). */
    static final int MAX_ATTEMPTS = 8;
    private static final List<Integer> BACKOFF_SECONDS = List.of(10, 30, 120, 300, 900, 1800, 3600);
    private static final Duration KEEP_FINISHED = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(ChapterNews.class);

    private final DSLContext db;
    private final Inbox inbox;
    private final NotificationListeners listeners;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ChapterNews(DSLContext db, Inbox inbox, NotificationListeners listeners, ApplicationEventPublisher events,
            TransactionTemplate transactions, Clock clock) {
        this.db = db;
        this.inbox = inbox;
        this.listeners = listeners;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * In the publishing transaction (a plain listener runs in it): the job commits with the
     * chapters or not at all. One job per publication, so chapters published together stay one
     * row and one Telegram message, as before.
     */
    @EventListener
    void on(ChaptersPublished published) {
        db.insertInto(CHAPTER_NEWS)
                .set(CHAPTER_NEWS.EDITION_ID, published.editionId())
                .set(CHAPTER_NEWS.FIRST_NUMBER, published.first())
                .set(CHAPTER_NEWS.LAST_NUMBER, published.last())
                .execute();
    }

    @Override
    public boolean runOne() {
        AtomicLong claimed = new AtomicLong();
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> step(claimed)));
        } catch (RuntimeException error) {
            if (claimed.get() == 0) {
                throw error;
            }
            failed(claimed.get(), error);
            return true;
        }
    }

    /** One batch of the oldest ready job, all in the caller's transaction. */
    private boolean step(AtomicLong claimed) {
        ChapterNewsRecord news = db.selectFrom(CHAPTER_NEWS)
                .where(CHAPTER_NEWS.STATE.eq("pending"), CHAPTER_NEWS.NOT_BEFORE.le(DSL.currentOffsetDateTime()))
                .orderBy(CHAPTER_NEWS.ID)
                .limit(1)
                .forUpdate().skipLocked()
                .fetchOne();
        if (news == null) {
            return false;
        }
        claimed.set(news.getId());
        // What of the publication readers can still open: the rest is news no more.
        Record2<Integer, Integer> live = db.select(DSL.min(CHAPTER.NUMBER), DSL.max(CHAPTER.NUMBER))
                .from(CHAPTER).join(EDITION).on(EDITION.ID.eq(CHAPTER.EDITION_ID))
                .where(CHAPTER.EDITION_ID.eq(news.getEditionId()),
                        CHAPTER.NUMBER.between(news.getFirstNumber(), news.getLastNumber()),
                        CHAPTER.PUBLISHED_REVISION_ID.isNotNull(), CHAPTER.DELETED_AT.isNull(),
                        EDITION.DELETED_AT.isNull(), EDITION.HIDDEN_AT.isNull())
                .fetchOne();
        if (live == null || live.value1() == null) {
            finish(news, "cancelled");
            return true;
        }
        List<Long> readers = db.select(EDITION_SUBSCRIPTION.ACCOUNT_ID).from(EDITION_SUBSCRIPTION)
                .where(EDITION_SUBSCRIPTION.EDITION_ID.eq(news.getEditionId()),
                        EDITION_SUBSCRIPTION.ACCOUNT_ID.gt(news.getAfterAccountId()),
                        Visibility.readableByAccount(EDITION_SUBSCRIPTION.EDITION_ID, EDITION_SUBSCRIPTION.ACCOUNT_ID))
                .orderBy(EDITION_SUBSCRIPTION.ACCOUNT_ID)
                .limit(BATCH)
                .fetch(EDITION_SUBSCRIPTION.ACCOUNT_ID);
        if (!readers.isEmpty()) {
            tell(news, readers, live.value1(), live.value2());
            news.setAfterAccountId(readers.getLast());
        }
        if (readers.size() < BATCH) {
            finish(news, "done");
        } else {
            db.update(CHAPTER_NEWS).set(CHAPTER_NEWS.AFTER_ACCOUNT_ID, news.getAfterAccountId()).set(CHAPTER_NEWS.ATTEMPTS, 0)
                    .where(CHAPTER_NEWS.ID.eq(news.getId())).execute();
        }
        return true;
    }

    private void tell(ChapterNewsRecord news, List<Long> readers, int first, int last) {
        // The key that keeps a batch run again from telling anyone twice.
        InsertValuesStep2<ChapterNewsRecipientRecord, Long, Long> told = db.insertInto(CHAPTER_NEWS_RECIPIENT,
                CHAPTER_NEWS_RECIPIENT.NEWS_ID, CHAPTER_NEWS_RECIPIENT.ACCOUNT_ID);
        for (long reader : readers) {
            told = told.values(news.getId(), reader);
        }
        List<Long> fresh = told.onConflictDoNothing().returning(CHAPTER_NEWS_RECIPIENT.ACCOUNT_ID)
                .fetch(CHAPTER_NEWS_RECIPIENT.ACCOUNT_ID);
        if (fresh.isEmpty()) {
            return;
        }
        Map<String, Object> payload = listeners.newChapters(news.getEditionId(), first, last);
        inbox.addGroupedToMany(fresh, KIND, "chapters:" + news.getEditionId(), payload);
        events.publishEvent(new ChapterNewsBatch(news.getId(), news.getEditionId(), first, last, KIND, payload, fresh));
    }

    private void finish(ChapterNewsRecord news, String state) {
        db.update(CHAPTER_NEWS).set(CHAPTER_NEWS.STATE, state).set(CHAPTER_NEWS.AFTER_ACCOUNT_ID, news.getAfterAccountId())
                .set(CHAPTER_NEWS.FINISHED_AT, now()).where(CHAPTER_NEWS.ID.eq(news.getId())).execute();
        // Done: the state alone keeps the job from running again.
        db.deleteFrom(CHAPTER_NEWS_RECIPIENT).where(CHAPTER_NEWS_RECIPIENT.NEWS_ID.eq(news.getId())).execute();
    }

    /** The batch rolled back as a whole: try it again after a pause, or give up after many. */
    private void failed(long newsId, RuntimeException error) {
        log.error("Chapter news {} failed", newsId, error);
        String message = String.valueOf(error.getMessage());
        transactions.executeWithoutResult(status -> {
            ChapterNewsRecord news = db.selectFrom(CHAPTER_NEWS).where(CHAPTER_NEWS.ID.eq(newsId)).forUpdate().fetchOne();
            if (news == null || !news.getState().equals("pending")) {
                return;
            }
            int attempts = news.getAttempts() + 1;
            int pause = BACKOFF_SECONDS.get(Math.min(attempts, BACKOFF_SECONDS.size()) - 1);
            db.update(CHAPTER_NEWS)
                    .set(CHAPTER_NEWS.ATTEMPTS, attempts)
                    .set(CHAPTER_NEWS.LAST_ERROR, message.length() > 500 ? message.substring(0, 500) : message)
                    .set(CHAPTER_NEWS.NOT_BEFORE, now().plusSeconds(pause))
                    .set(CHAPTER_NEWS.STATE, attempts >= MAX_ATTEMPTS ? "failed" : "pending")
                    .set(CHAPTER_NEWS.FINISHED_AT, attempts >= MAX_ATTEMPTS ? now() : null)
                    .where(CHAPTER_NEWS.ID.eq(newsId)).execute();
        });
    }

    /** Finished jobs are kept a month, to look into; then they go. */
    void forgetOld() {
        db.deleteFrom(CHAPTER_NEWS)
                .where(CHAPTER_NEWS.STATE.ne("pending"), CHAPTER_NEWS.FINISHED_AT.lt(now().minus(KEEP_FINISHED)))
                .execute();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** The schedule, on a thread of its own; switched off in tests, which call {@link #drain()} themselves. */
    @Component
    @ConditionalOnProperty(name = "novelka.notifications.chapter-news", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final ChapterNews news;
        private final AtomicBoolean busy = new AtomicBoolean();
        private volatile boolean stopping;
        private final ExecutorService thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "chapter-news");
            worker.setDaemon(true);
            return worker;
        });

        Schedule(ChapterNews news) {
            this.news = news;
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 2_000)
        void tick() {
            if (!busy.compareAndSet(false, true)) {
                return;
            }
            thread.execute(() -> {
                try {
                    while (!stopping && news.runOne()) {
                        // next batch
                    }
                } catch (RuntimeException error) {
                    log.error("Chapter news worker failed", error);
                } finally {
                    busy.set(false);
                }
            });
        }

        /** A deploy: the batch in work commits, the rest waits for the next start. */
        @jakarta.annotation.PreDestroy
        void stop() throws InterruptedException {
            stopping = true;
            thread.shutdown();
            thread.awaitTermination(30, TimeUnit.SECONDS);
        }

        @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
        void forgetOld() {
            try {
                news.forgetOld();
            } catch (RuntimeException error) {
                log.error("Forgetting old chapter news failed", error);
            }
        }
    }
}
