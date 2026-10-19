package space.panrid.novelka.admin.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.SHAH_BALANCE;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.SiteRoles;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.admin.AutotranslateAnswered;
import space.panrid.novelka.admin.AutotranslateRequested;
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
    private final ApplicationEventPublisher events;

    Staff(DSLContext db, SiteRoles roles, AuditLog audit, ApplicationEventPublisher events) {
        this.db = db;
        this.roles = roles;
        this.audit = audit;
        this.events = events;
    }

    /** @param email only for the owner: administrators do not need people's addresses */
    /**
     * {@code email}, {@code shahs} (free plus held) and {@code autotranslate} (the owner let them
     * autotranslate) only for the site owner.
     */
    record Person(String nick, String role, String email, OffsetDateTime createdAt, OffsetDateTime lastSeenAt, Integer shahs,
            Boolean autotranslate) {
    }

    static final int PAGE = 20;

    record People(List<Person> items, int total, int page, boolean hasMore) {
    }

    People find(Viewer viewer, String query, int page) {
        String q = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        boolean owner = viewer.role() == SiteRole.OWNER;
        int at = Math.max(1, page);
        var shahs = DSL.coalesce(SHAH_BALANCE.AVAILABLE.plus(SHAH_BALANCE.RESERVED), DSL.zero());
        org.jooq.Condition where = q.isEmpty() ? DSL.noCondition()
                : owner ? ACCOUNT.NICK_KEY.contains(q).or(ACCOUNT.EMAIL_KEY.contains(q)) : ACCOUNT.NICK_KEY.contains(q);
        int total = db.fetchCount(ACCOUNT, where);
        List<Person> items = db.select(ACCOUNT.NICK, ACCOUNT.SITE_ROLE, ACCOUNT.EMAIL, ACCOUNT.CREATED_AT, ACCOUNT.LAST_SEEN_AT, shahs,
                        ACCOUNT.AUTOTRANSLATE).from(ACCOUNT)
                .leftJoin(SHAH_BALANCE).on(SHAH_BALANCE.ACCOUNT_ID.eq(ACCOUNT.ID))
                .where(where)
                // Staff first, then the most recently seen.
                .orderBy(DSL.when(ACCOUNT.SITE_ROLE.eq("reader"), 1).otherwise(0), ACCOUNT.LAST_SEEN_AT.desc().nullsLast(), ACCOUNT.ID)
                .limit(PAGE).offset((at - 1) * PAGE)
                .fetch(r -> new Person(r.value1(), r.value2(), owner ? r.value3() : null, r.value4(), r.value5(), owner ? r.value6() : null,
                        owner ? r.value7() : null));
        return new People(items, total, at, at * PAGE < total);
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

    /**
     * The site owner lets a person autotranslate (етап 19), or takes it back; they pay with their
     * шаги. Runs already started go on. Letting in answers their request, if they asked.
     */
    @Transactional
    void setAutotranslate(Viewer owner, String nick, boolean allowed) {
        var target = db.select(ACCOUNT.ID, ACCOUNT.SITE_ROLE, ACCOUNT.AUTOTRANSLATE, ACCOUNT.AUTOTRANSLATE_REQUESTED_AT).from(ACCOUNT)
                .where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT))).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Людини з ніком «%s» немає.".formatted(nick)));
        if (SiteRole.fromCode(target.value2()) == SiteRole.OWNER) {
            throw UserFacingException.badRequest("Власнику сайту автопереклад доступний завжди.");
        }
        if (target.value3() == allowed) {
            return;
        }
        db.update(ACCOUNT).set(ACCOUNT.AUTOTRANSLATE, allowed).setNull(ACCOUNT.AUTOTRANSLATE_REQUESTED_AT)
                .where(ACCOUNT.ID.eq(target.value1())).execute();
        audit.record(owner.accountId(), allowed ? "autotranslate_granted" : "autotranslate_revoked", "account", target.value1(),
                Map.of("nick", nick));
        if (allowed && target.value4() != null) {
            events.publishEvent(new AutotranslateAnswered(target.value1(), true));
        }
    }

    /** Who asked for «Автопереклад із сайту» and waits for the owner's answer, the earliest first. */
    record Request(String nick, OffsetDateTime requestedAt, Integer shahs) {
    }

    List<Request> requests() {
        var shahs = DSL.coalesce(SHAH_BALANCE.AVAILABLE.plus(SHAH_BALANCE.RESERVED), DSL.zero());
        return db.select(ACCOUNT.NICK, ACCOUNT.AUTOTRANSLATE_REQUESTED_AT, shahs).from(ACCOUNT)
                .leftJoin(SHAH_BALANCE).on(SHAH_BALANCE.ACCOUNT_ID.eq(ACCOUNT.ID))
                .where(ACCOUNT.AUTOTRANSLATE_REQUESTED_AT.isNotNull(), ACCOUNT.AUTOTRANSLATE.isFalse())
                .orderBy(ACCOUNT.AUTOTRANSLATE_REQUESTED_AT, ACCOUNT.ID)
                .fetch(r -> new Request(r.value1(), r.value2(), r.value3()));
    }

    /**
     * «Попросити доступ» (етап 19): the person asks the site owner for «Автопереклад із сайту». Asking
     * again while the owner has not answered changes nothing and tells nobody twice.
     */
    @Transactional
    void requestAutotranslate(Viewer viewer) {
        var me = db.select(ACCOUNT.NICK, ACCOUNT.AUTOTRANSLATE, ACCOUNT.AUTOTRANSLATE_REQUESTED_AT).from(ACCOUNT)
                .where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle();
        if (viewer.role() == SiteRole.OWNER || me.value2()) {
            throw UserFacingException.badRequest("Автопереклад із сайту вам уже відкрито.");
        }
        if (me.value3() != null) {
            return;
        }
        db.update(ACCOUNT).set(ACCOUNT.AUTOTRANSLATE_REQUESTED_AT, DSL.currentOffsetDateTime())
                .where(ACCOUNT.ID.eq(viewer.accountId())).execute();
        events.publishEvent(new AutotranslateRequested(viewer.accountId(), me.value1()));
    }

    /** The site owner refuses a request; the person hears about it and may ask again later. */
    @Transactional
    void refuseAutotranslate(Viewer owner, String nick) {
        var target = db.select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT)), ACCOUNT.AUTOTRANSLATE_REQUESTED_AT.isNotNull())
                .fetchOptional(ACCOUNT.ID)
                .orElseThrow(() -> UserFacingException.notFound("«%s» доступу не просить.".formatted(nick)));
        db.update(ACCOUNT).setNull(ACCOUNT.AUTOTRANSLATE_REQUESTED_AT).where(ACCOUNT.ID.eq(target)).execute();
        audit.record(owner.accountId(), "autotranslate_refused", "account", target, Map.of("nick", nick));
        events.publishEvent(new AutotranslateAnswered(target, false));
    }
}
