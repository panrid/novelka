package space.panrid.novelka.catalog.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TAKEOVER_REQUEST;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.Relay;
import space.panrid.novelka.catalog.RelayState;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * «Естафета» (рішення 4). An edition is free to continue when its owner marked it
 * abandoned, or has not been on the site for X months, or when no chapter appeared for
 * X months and a request to continue got no answer in 14 days. «Free» is computed, never stored.
 */
@Service
class RelayService implements Relay {

    static final int REQUEST_WAIT_DAYS = 14;

    private final DSLContext db;
    private final SiteSettings settings;
    private final Clock clock;

    RelayService(DSLContext db, SiteSettings settings, Clock clock) {
        this.db = db;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public RelayState state(long editionId) {
        var row = db.select(EDITION.STATUS, EDITION.LAST_PUBLISHED_AT, EDITION.CREATED_AT, EDITION.KIND,
                        DSL.coalesce(ACCOUNT.LAST_SEEN_AT, ACCOUNT.CREATED_AT))
                .from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(EDITION.ID.eq(editionId)).fetchOne();
        if (row == null) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
        int last = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), DSL.val(0))).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()))
                .fetchOne(0, Integer.class);
        String reason = null;
        if (!row.value4().equals("original")) {
            OffsetDateTime now = now();
            OffsetDateTime silence = now.minusMonths(settings.integer(SiteSettings.RELAY_INACTIVE_MONTHS, 3));
            OffsetDateTime lastChapter = row.value2() != null ? row.value2() : row.value3();
            boolean unanswered = db.fetchExists(TAKEOVER_REQUEST, TAKEOVER_REQUEST.EDITION_ID.eq(editionId)
                    .and(TAKEOVER_REQUEST.STATE.eq("open"))
                    .and(TAKEOVER_REQUEST.CREATED_AT.lt(now.minusDays(REQUEST_WAIT_DAYS))));
            if (row.value1().equals("abandoned")) {
                reason = "abandoned";
            } else if (row.value5().isBefore(silence)) {
                reason = "inactive";
            } else if (lastChapter.isBefore(silence) && unanswered) {
                reason = "unanswered";
            }
        }
        List<RelayState.Continuation> continuations = db.select(EDITION.ID, TEAM.HANDLE, DSL.coalesce(TEAM.NAME, ACCOUNT.NICK), EDITION.FIRST_NUMBER)
                .from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(EDITION.CONTINUES_EDITION_ID.eq(editionId).and(EDITION.HIDDEN_AT.isNull()).and(EDITION.CHAPTER_COUNT.gt(0)))
                .orderBy(EDITION.CREATED_AT)
                .fetch(r -> new RelayState.Continuation(r.value1(), r.value2(), r.value3(), r.value4()));
        return new RelayState(reason != null, reason, last, continuations);
    }

    @Override
    @Transactional
    public long request(long editionId, long teamId, long requesterId, String message) {
        if (db.fetchExists(TAKEOVER_REQUEST, TAKEOVER_REQUEST.EDITION_ID.eq(editionId)
                .and(TAKEOVER_REQUEST.TEAM_ID.eq(teamId)).and(TAKEOVER_REQUEST.STATE.eq("open")))) {
            throw UserFacingException.conflict("Ваша команда вже попросила продовжити. Зачекайте відповіді.");
        }
        String text = message == null ? null : message.strip();
        if (text != null && text.length() > 1000) {
            throw UserFacingException.badRequest("Повідомлення — до 1000 символів.");
        }
        return db.insertInto(TAKEOVER_REQUEST)
                .set(TAKEOVER_REQUEST.EDITION_ID, editionId)
                .set(TAKEOVER_REQUEST.TEAM_ID, teamId)
                .set(TAKEOVER_REQUEST.REQUESTED_BY, requesterId)
                .set(TAKEOVER_REQUEST.MESSAGE, text == null || text.isEmpty() ? null : text)
                .returning(TAKEOVER_REQUEST.ID)
                .fetchOne(TAKEOVER_REQUEST.ID);
    }

    @Override
    @Transactional
    public void answer(long editionId, long requestId, boolean grant) {
        int changed = db.update(TAKEOVER_REQUEST)
                .set(TAKEOVER_REQUEST.STATE, grant ? "granted" : "declined")
                .set(TAKEOVER_REQUEST.ANSWERED_AT, now())
                .where(TAKEOVER_REQUEST.ID.eq(requestId).and(TAKEOVER_REQUEST.EDITION_ID.eq(editionId))
                        .and(TAKEOVER_REQUEST.STATE.eq("open")))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Цього запиту вже немає.");
        }
    }

    /**
     * Starts the continuing edition: same novel, the new team, numbering from the chapter
     * after the last one of the old edition. Allowed when the old edition is free, or its
     * owner granted this team's request.
     */
    @Override
    @Transactional
    public EditionRef continueEdition(long oldEditionId, long teamId, String kind) {
        if (!kind.equals("human") && !kind.equals("machine")) {
            throw UserFacingException.badRequest("Оберіть: переклад людини чи машинний.");
        }
        RelayState state = state(oldEditionId);
        boolean granted = db.fetchExists(TAKEOVER_REQUEST, TAKEOVER_REQUEST.EDITION_ID.eq(oldEditionId)
                .and(TAKEOVER_REQUEST.TEAM_ID.eq(teamId)).and(TAKEOVER_REQUEST.STATE.eq("granted")));
        if (!state.free() && !granted) {
            throw new UserFacingException(HttpStatus.FORBIDDEN,
                    "Цей переклад ще не вільний. Попросіть власника або зачекайте.");
        }
        var old = db.select(EDITION.NOVEL_ID, EDITION.TEAM_ID, EDITION.ADULT, NOVEL.SLUG).from(EDITION)
                .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).where(EDITION.ID.eq(oldEditionId)).fetchOne();
        if (old.value2() == teamId) {
            throw UserFacingException.badRequest("Це вже переклад вашої команди.");
        }
        if (db.fetchExists(EDITION, EDITION.NOVEL_ID.eq(old.value1()).and(EDITION.TEAM_ID.eq(teamId)))) {
            throw UserFacingException.conflict("У вашої команди вже є переклад цієї новели.");
        }
        long editionId = db.insertInto(EDITION)
                .set(EDITION.NOVEL_ID, old.value1())
                .set(EDITION.TEAM_ID, teamId)
                .set(EDITION.KIND, kind)
                .set(EDITION.ADULT, old.value3())
                .set(EDITION.CONTINUES_EDITION_ID, oldEditionId)
                .set(EDITION.FIRST_NUMBER, state.lastNumber() + 1)
                .returning(EDITION.ID)
                .fetchOne(EDITION.ID);
        return new EditionRef(old.value1(), editionId, old.value4());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
