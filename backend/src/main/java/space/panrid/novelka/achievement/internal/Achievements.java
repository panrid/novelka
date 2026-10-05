package space.panrid.novelka.achievement.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.ACCOUNT_LEVEL;
import static space.panrid.novelka.jooq.Tables.ACHIEVEMENT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.COMMENT;
import static space.panrid.novelka.jooq.Tables.EDITION_RATING;
import static space.panrid.novelka.jooq.Tables.PROPOSAL;
import static space.panrid.novelka.jooq.Tables.PROPOSAL_VOTE;
import static space.panrid.novelka.jooq.Tables.READING_PROGRESS;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.SUGGESTION;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import space.panrid.novelka.achievement.AchievementEarned;

/**
 * Counts what a person did on the site, turns it into points and a level, and awards the
 * badges whose goal is reached. Badges are never taken back.
 */
@Component
class Achievements {

    /** People seen this recently are counted again on each sweep. */
    static final Duration ACTIVE = Duration.ofMinutes(15);

    private final DSLContext db;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    Achievements(DSLContext db, ApplicationEventPublisher events, TransactionTemplate transactions) {
        this.db = db;
        this.events = events;
        this.transactions = transactions;
    }

    /**
     * @param read       chapters read: the furthest chapter in each translation
     * @param accepted   suggestions the team accepted
     * @param translated chapters of the person's own translation that readers see
     * @param scouted    proposals of theirs someone else took to translate
     */
    record Stats(int read, int comments, int accepted, int ratings, int votes, int scouted, int translated) {

        int points() {
            return read + 3 * comments + 10 * accepted + 2 * ratings + votes + 15 * scouted + 20 * translated;
        }
    }

    /** Level 1 from the start; each next one needs more: 20, 80, 180, 320… points. */
    static int level(int points) {
        return (int) Math.floor(Math.sqrt(points / 20.0)) + 1;
    }

    static int pointsFor(int level) {
        return 20 * (level - 1) * (level - 1);
    }

    Stats stats(long accountId) {
        Field<Integer> read = DSL.field(DSL.select(DSL.coalesce(DSL.sum(READING_PROGRESS.CHAPTER_NUMBER), 0).cast(Integer.class))
                .from(READING_PROGRESS).where(READING_PROGRESS.ACCOUNT_ID.eq(accountId)));
        Field<Integer> comments = DSL.field(DSL.selectCount().from(COMMENT)
                .where(COMMENT.AUTHOR_ID.eq(accountId), COMMENT.DELETED_AT.isNull(), COMMENT.HIDDEN_AT.isNull()));
        Field<Integer> accepted = DSL.field(DSL.selectCount().from(SUGGESTION)
                .where(SUGGESTION.AUTHOR_ID.eq(accountId), SUGGESTION.STATE.eq("accepted")));
        Field<Integer> ratings = DSL.field(DSL.selectCount().from(EDITION_RATING).where(EDITION_RATING.ACCOUNT_ID.eq(accountId)));
        Field<Integer> votes = DSL.field(DSL.selectCount().from(PROPOSAL_VOTE).where(PROPOSAL_VOTE.ACCOUNT_ID.eq(accountId)));
        Field<Integer> scouted = DSL.field(DSL.selectCount().from(PROPOSAL)
                .where(PROPOSAL.PROPOSED_BY.eq(accountId), PROPOSAL.STATE.eq("taken"), PROPOSAL.TAKEN_BY.ne(accountId)));
        Field<Integer> translated = DSL.field(DSL.select(DSL.countDistinct(REVISION.CHAPTER_ID)).from(REVISION)
                .join(CHAPTER).on(CHAPTER.ID.eq(REVISION.CHAPTER_ID))
                .where(REVISION.AUTHOR_ID.eq(accountId), REVISION.ORIGIN.in("editor", "import"),
                        CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        var row = db.select(read, comments, accepted, ratings, votes, scouted, translated).fetchSingle();
        return new Stats(row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6(), row.value7());
    }

    /** Everyone seen on the site lately. */
    List<Long> recentlyActive() {
        return db.select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.LAST_SEEN_AT.gt(OffsetDateTime.now().minus(ACTIVE))).fetch(ACCOUNT.ID);
    }

    /** Counts these people again; new badges are announced once the change is stored. */
    void sweep(Collection<Long> accountIds) {
        for (long accountId : accountIds) {
            transactions.executeWithoutResult(status -> update(accountId));
        }
    }

    private void update(long accountId) {
        Stats stats = stats(accountId);
        int points = stats.points();
        db.insertInto(ACCOUNT_LEVEL).set(ACCOUNT_LEVEL.ACCOUNT_ID, accountId).set(ACCOUNT_LEVEL.POINTS, points)
                .set(ACCOUNT_LEVEL.LEVEL, level(points))
                .onConflict(ACCOUNT_LEVEL.ACCOUNT_ID).doUpdate()
                .set(ACCOUNT_LEVEL.POINTS, points).set(ACCOUNT_LEVEL.LEVEL, level(points))
                .set(ACCOUNT_LEVEL.UPDATED_AT, DSL.currentOffsetDateTime())
                .execute();
        Set<String> had = Set.copyOf(db.select(ACHIEVEMENT.CODE).from(ACHIEVEMENT).where(ACHIEVEMENT.ACCOUNT_ID.eq(accountId))
                .fetch(ACHIEVEMENT.CODE));
        String nick = null;
        for (Badge badge : Badge.ALL) {
            if (had.contains(badge.code()) || !badge.earned(stats)) {
                continue;
            }
            int added = db.insertInto(ACHIEVEMENT).set(ACHIEVEMENT.ACCOUNT_ID, accountId).set(ACHIEVEMENT.CODE, badge.code())
                    .onConflictDoNothing().execute();
            if (added == 1) {
                if (nick == null) {
                    nick = db.select(ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchSingle(ACCOUNT.NICK);
                }
                events.publishEvent(new AchievementEarned(accountId, nick, badge.code(), badge.title()));
            }
        }
    }

    /** @param earnedAt null while the badge is still ahead */
    record BadgeView(String code, String title, String description, OffsetDateTime earnedAt, int progress, int goal) {
    }

    /** @param nextLevelPoints points the next level needs */
    record Profile(int level, int points, int levelPoints, int nextLevelPoints, List<BadgeView> badges) {
    }

    Profile profile(long accountId) {
        Stats stats = stats(accountId);
        Map<String, OffsetDateTime> earned = db.select(ACHIEVEMENT.CODE, ACHIEVEMENT.EARNED_AT).from(ACHIEVEMENT)
                .where(ACHIEVEMENT.ACCOUNT_ID.eq(accountId)).fetchMap(ACHIEVEMENT.CODE, ACHIEVEMENT.EARNED_AT);
        List<BadgeView> badges = new ArrayList<>();
        for (Badge badge : Badge.ALL) {
            badges.add(new BadgeView(badge.code(), badge.title(), badge.description(), earned.get(badge.code()),
                    Math.min(badge.goal(), badge.count().applyAsInt(stats)), badge.goal()));
        }
        int points = stats.points();
        int level = level(points);
        return new Profile(level, points, pointsFor(level), pointsFor(level + 1), badges);
    }
}
