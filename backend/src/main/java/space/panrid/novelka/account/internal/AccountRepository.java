package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.NICK_CHANGE;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;

@Repository
public class AccountRepository {

    private final DSLContext db;

    AccountRepository(DSLContext db) {
        this.db = db;
    }

    public Optional<Viewer> viewer(long id) {
        return db.select(ACCOUNT.ID, ACCOUNT.NICK, ACCOUNT.SITE_ROLE, ACCOUNT.ADULT_CONFIRMED_AT)
                .from(ACCOUNT)
                .where(ACCOUNT.ID.eq(id))
                .fetchOptional(r -> new Viewer(r.value1(), r.value2(), SiteRole.fromCode(r.value3()), r.value4() != null));
    }

    Optional<AccountRow> byId(long id) {
        return db.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOptional(AccountRepository::row);
    }

    /** Sign-in accepts either the nick or the email, in any letter case. */
    Optional<AccountRow> byLogin(String login) {
        String key = AccountRules.key(login);
        return db.selectFrom(ACCOUNT)
                .where(ACCOUNT.NICK_KEY.eq(key).or(ACCOUNT.EMAIL_KEY.eq(key)))
                .fetchOptional(AccountRepository::row);
    }

    Optional<AccountRow> byEmail(String email) {
        return db.selectFrom(ACCOUNT).where(ACCOUNT.EMAIL_KEY.eq(AccountRules.key(email)))
                .fetchOptional(AccountRepository::row);
    }

    boolean nickTaken(String nick) {
        return db.fetchExists(ACCOUNT, ACCOUNT.NICK_KEY.eq(AccountRules.key(nick)));
    }

    boolean emailTaken(String email) {
        return db.fetchExists(ACCOUNT, ACCOUNT.EMAIL_KEY.eq(AccountRules.key(email)));
    }

    boolean ownerExists() {
        return db.fetchExists(ACCOUNT, ACCOUNT.SITE_ROLE.eq(SiteRole.OWNER.code()));
    }

    long insert(String nick, String email, String passwordHash, SiteRole role, OffsetDateTime verifiedAt) {
        return db.insertInto(ACCOUNT)
                .set(ACCOUNT.NICK, nick)
                .set(ACCOUNT.NICK_KEY, AccountRules.key(nick))
                .set(ACCOUNT.EMAIL, email)
                .set(ACCOUNT.EMAIL_KEY, AccountRules.key(email))
                .set(ACCOUNT.PASSWORD_HASH, passwordHash)
                .set(ACCOUNT.SITE_ROLE, role.code())
                .set(ACCOUNT.EMAIL_VERIFIED_AT, verifiedAt)
                .returning(ACCOUNT.ID)
                .fetchOne(ACCOUNT.ID);
    }

    void markEmailVerified(long id, OffsetDateTime at) {
        db.update(ACCOUNT).set(ACCOUNT.EMAIL_VERIFIED_AT, at)
                .where(ACCOUNT.ID.eq(id).and(ACCOUNT.EMAIL_VERIFIED_AT.isNull()))
                .execute();
    }

    void updatePassword(long id, String passwordHash) {
        db.update(ACCOUNT).set(ACCOUNT.PASSWORD_HASH, passwordHash).where(ACCOUNT.ID.eq(id)).execute();
    }

    /** Only called with an address already confirmed from the letter. */
    void updateEmail(long id, String email) {
        db.update(ACCOUNT)
                .set(ACCOUNT.EMAIL, email)
                .set(ACCOUNT.EMAIL_KEY, AccountRules.key(email))
                .where(ACCOUNT.ID.eq(id))
                .execute();
    }

    void changeNick(long id, String oldNick, String newNick, OffsetDateTime at) {
        db.update(ACCOUNT)
                .set(ACCOUNT.NICK, newNick)
                .set(ACCOUNT.NICK_KEY, AccountRules.key(newNick))
                .set(ACCOUNT.NICK_CHANGED_AT, at)
                .where(ACCOUNT.ID.eq(id))
                .execute();
        db.insertInto(NICK_CHANGE)
                .set(NICK_CHANGE.ACCOUNT_ID, id)
                .set(NICK_CHANGE.OLD_NICK, oldNick)
                .set(NICK_CHANGE.NEW_NICK, newNick)
                .set(NICK_CHANGE.CHANGED_AT, at)
                .execute();
    }

    void updateSettings(long id, String bio, String dmPolicy, Boolean showReading, OffsetDateTime adultConfirmedAt,
            boolean adultChanged, Boolean showShah, Boolean studioInMenu) {
        Map<Field<?>, Object> changes = new HashMap<>();
        if (bio != null) {
            changes.put(ACCOUNT.BIO, bio);
        }
        if (dmPolicy != null) {
            changes.put(ACCOUNT.DM_POLICY, dmPolicy);
        }
        if (showReading != null) {
            changes.put(ACCOUNT.SHOW_READING, showReading);
        }
        if (adultChanged) {
            changes.put(ACCOUNT.ADULT_CONFIRMED_AT, adultConfirmedAt);
        }
        if (showShah != null) {
            changes.put(ACCOUNT.SHOW_SHAH, showShah);
        }
        if (studioInMenu != null) {
            changes.put(ACCOUNT.STUDIO_IN_MENU, studioInMenu);
        }
        if (!changes.isEmpty()) {
            db.update(ACCOUNT).set(changes).where(ACCOUNT.ID.eq(id)).execute();
        }
    }

    void setAvatar(long id, Long imageId) {
        db.update(ACCOUNT).set(ACCOUNT.AVATAR_IMAGE_ID, imageId).where(ACCOUNT.ID.eq(id)).execute();
    }

    /** Current account for a nick, following renames: old links to /u/{old} keep working. */
    Optional<Long> idByNickOrFormerNick(String nick) {
        String key = AccountRules.key(nick);
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(key)).fetchOptional(r -> r.value1())
                .or(() -> db.select(NICK_CHANGE.ACCOUNT_ID).from(NICK_CHANGE)
                        .where(org.jooq.impl.DSL.lower(NICK_CHANGE.OLD_NICK).eq(key))
                        .orderBy(NICK_CHANGE.CHANGED_AT.desc())
                        .limit(1)
                        .fetchOptional(r -> r.value1()));
    }

    /** Written at most every few minutes per account; used by the «естафета» inactivity rule. */
    void touchLastSeen(long id, OffsetDateTime at, OffsetDateTime ifBefore) {
        db.update(ACCOUNT).set(ACCOUNT.LAST_SEEN_AT, at)
                .where(ACCOUNT.ID.eq(id)
                        .and(ACCOUNT.LAST_SEEN_AT.isNull().or(ACCOUNT.LAST_SEEN_AT.lt(ifBefore))))
                .execute();
    }

    private static AccountRow row(Record r) {
        return new AccountRow(r.get(ACCOUNT.ID), r.get(ACCOUNT.NICK), r.get(ACCOUNT.EMAIL),
                r.get(ACCOUNT.EMAIL_VERIFIED_AT), r.get(ACCOUNT.PASSWORD_HASH),
                SiteRole.fromCode(r.get(ACCOUNT.SITE_ROLE)), r.get(ACCOUNT.NICK_CHANGED_AT));
    }
}
