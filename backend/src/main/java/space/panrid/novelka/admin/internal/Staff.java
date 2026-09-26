package space.panrid.novelka.admin.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.SHAH_BALANCE;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.SiteRoles;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * People and their site roles. An administrator makes moderators; only the owner makes
 * administrators. Nobody changes their own role, and the owner stays the owner.
 */
@Service
class Staff {

    private final DSLContext db;
    private final SiteRoles roles;
    private final AuditLog audit;

    Staff(DSLContext db, SiteRoles roles, AuditLog audit) {
        this.db = db;
        this.roles = roles;
        this.audit = audit;
    }

    /** @param email only for the owner: administrators do not need people's addresses */
    /** {@code email} and {@code shahs} (free plus held) only for the site owner. */
    record Person(String nick, String role, String email, OffsetDateTime createdAt, OffsetDateTime lastSeenAt, Integer shahs) {
    }

    List<Person> find(Viewer viewer, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        boolean owner = viewer.role() == SiteRole.OWNER;
        var shahs = DSL.coalesce(SHAH_BALANCE.AVAILABLE.plus(SHAH_BALANCE.RESERVED), DSL.zero());
        return db.select(ACCOUNT.NICK, ACCOUNT.SITE_ROLE, ACCOUNT.EMAIL, ACCOUNT.CREATED_AT, ACCOUNT.LAST_SEEN_AT, shahs).from(ACCOUNT)
                .leftJoin(SHAH_BALANCE).on(SHAH_BALANCE.ACCOUNT_ID.eq(ACCOUNT.ID))
                .where(q.isEmpty() ? DSL.noCondition()
                        : owner ? ACCOUNT.NICK_KEY.contains(q).or(ACCOUNT.EMAIL_KEY.contains(q)) : ACCOUNT.NICK_KEY.contains(q))
                // Staff first, then the most recently seen.
                .orderBy(DSL.when(ACCOUNT.SITE_ROLE.eq("reader"), 1).otherwise(0), ACCOUNT.LAST_SEEN_AT.desc().nullsLast())
                .limit(50)
                .fetch(r -> new Person(r.value1(), r.value2(), owner ? r.value3() : null, r.value4(), r.value5(), owner ? r.value6() : null));
    }

    @Transactional
    void setRole(Viewer actor, String nick, String roleCode) {
        SiteRole role;
        try {
            role = SiteRole.fromCode(roleCode);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw UserFacingException.badRequest("Невідома роль.");
        }
        var target = db.select(ACCOUNT.ID, ACCOUNT.SITE_ROLE).from(ACCOUNT)
                .where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT))).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Людини з ніком «%s» немає.".formatted(nick)));
        SiteRole current = SiteRole.fromCode(target.value2());
        if (target.value1() == actor.accountId()) {
            throw UserFacingException.badRequest("Свою роль змінити не можна.");
        }
        if (current == SiteRole.OWNER || role == SiteRole.OWNER) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Власник сайту один, і ця роль тут не змінюється.");
        }
        // Administrators manage readers and moderators; administrators themselves are the owner's.
        SiteRole limit = actor.role() == SiteRole.OWNER ? SiteRole.ADMIN : SiteRole.MODERATOR;
        if (current.atLeast(SiteRole.ADMIN) && actor.role() != SiteRole.OWNER || role.compareTo(limit) > 0) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Цю роль може дати лише власник сайту.");
        }
        roles.setRole(target.value1(), role);
        audit.record(actor.accountId(), "role", "account", target.value1(), Map.of("nick", nick, "from", current.code(), "to", role.code()));
    }
}
