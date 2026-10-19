package space.panrid.novelka.webhook.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.jooq.tables.records.WebhookDeliveryRecord;
import space.panrid.novelka.team.Teams;

/**
 * Who may hear about a translation: the same rules as the site's pages, checked when an event is
 * queued and again right before it is sent (a person may have left the team, the translation may
 * have been hidden meanwhile). Its team hears everything about it; anyone else only what readers
 * see — a translation not hidden, not in the trash, with chapters, and 18+ only after the person
 * confirmed the age. Events for the team ({@link WebhookEvent#team()}) reach only its team.
 */
@Component
class WebhookAccess {

    private final DSLContext db;
    private final Teams teams;

    WebhookAccess(DSLContext db, Teams teams) {
        this.db = db;
        this.teams = teams;
    }

    boolean allowed(long accountId, long editionId, boolean teamOnly) {
        Record edition = db.select(EDITION.TEAM_ID, EDITION.DELETED_AT, EDITION.HIDDEN_AT, EDITION.ADULT, EDITION.CHAPTER_COUNT)
                .from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne();
        if (edition == null || edition.get(EDITION.DELETED_AT) != null) {
            return false;
        }
        if (member(accountId, edition.get(EDITION.TEAM_ID))) {
            return true;
        }
        if (teamOnly || edition.get(EDITION.HIDDEN_AT) != null || edition.get(EDITION.CHAPTER_COUNT) == 0) {
            return false;
        }
        return !edition.get(EDITION.ADULT)
                || db.fetchExists(ACCOUNT, ACCOUNT.ID.eq(accountId).and(ACCOUNT.ADULT_CONFIRMED_AT.isNotNull()));
    }

    boolean member(long accountId, long teamId) {
        return teams.roleOf(teamId, accountId).isPresent();
    }

    /** Right before sending: why this delivery may not go any more, or null when it may. */
    String refusal(long accountId, WebhookDeliveryRecord delivery, WebhookEvent event) {
        if (delivery.getEditionId() == null) {
            return null;
        }
        if (!allowed(accountId, delivery.getEditionId(), event.team())) {
            return "Цей переклад вам більше недоступний.";
        }
        if (event == WebhookEvent.CHAPTER_PUBLISHED && !stillPublished(delivery)) {
            return "Ці глави вже зняли з публікації.";
        }
        return null;
    }

    private boolean stillPublished(WebhookDeliveryRecord delivery) {
        if (delivery.getFirstNumber() == null || delivery.getLastNumber() == null) {
            return true;
        }
        return db.fetchExists(DSL.selectOne().from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(delivery.getEditionId()),
                        CHAPTER.NUMBER.between(delivery.getFirstNumber(), delivery.getLastNumber()),
                        CHAPTER.PUBLISHED_REVISION_ID.isNotNull(), CHAPTER.DELETED_AT.isNull()));
    }
}
