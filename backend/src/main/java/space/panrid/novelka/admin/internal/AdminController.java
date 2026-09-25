package space.panrid.novelka.admin.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.AUDIT_LOG;
import static space.panrid.novelka.jooq.Tables.JOB;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api")
class AdminController {

    private final AccessPolicy access;
    private final CurrentUser currentUser;
    private final Moderation moderation;
    private final Staff staff;
    private final SiteSettings settings;
    private final AuditLog audit;
    private final DSLContext db;
    private final JsonMapper json;

    AdminController(AccessPolicy access, CurrentUser currentUser, Moderation moderation, Staff staff, SiteSettings settings,
            AuditLog audit, DSLContext db, JsonMapper json) {
        this.access = access;
        this.currentUser = currentUser;
        this.moderation = moderation;
        this.staff = staff;
        this.settings = settings;
        this.audit = audit;
        this.db = db;
        this.json = json;
    }

    // ---- anyone signed in reports -----------------------------------------------------------

    record Report(String target, long targetId, String reason) {
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Boolean> report(@RequestBody Report body) {
        moderation.report(currentUser.requireSignedIn(), body.target(), body.targetId(), body.reason());
        return Map.of("received", true);
    }

    // ---- moderators ------------------------------------------------------------------------------

    record Overview(int openReports, int activeJobs, int failedJobs, String role) {
    }

    @GetMapping("/admin/overview")
    Overview overview() {
        Viewer viewer = access.requireSiteRole(SiteRole.MODERATOR);
        return new Overview(moderation.openCount(), db.fetchCount(JOB, JOB.STATE.in("queued", "running")),
                db.fetchCount(JOB, JOB.STATE.eq("failed")), viewer.role().code());
    }

    @GetMapping("/admin/reports")
    List<Moderation.Reported> reports() {
        access.requireSiteRole(SiteRole.MODERATOR);
        return moderation.open();
    }

    /** @param action «hide» or «dismiss» */
    record Decision(String action, String reason) {
    }

    @PostMapping("/admin/reports/{target}/{targetId}")
    void decide(@PathVariable String target, @PathVariable long targetId, @RequestBody Decision body) {
        Viewer moderator = access.requireSiteRole(SiteRole.MODERATOR);
        if (!"hide".equals(body.action()) && !"dismiss".equals(body.action())) {
            throw UserFacingException.badRequest("Оберіть: приховати чи відхилити скаргу.");
        }
        moderation.decide(moderator, target, targetId, "hide".equals(body.action()), body.reason());
    }

    @GetMapping("/admin/hidden")
    List<Moderation.Hidden> hidden() {
        access.requireSiteRole(SiteRole.MODERATOR);
        return moderation.hidden();
    }

    record Reason(String reason) {
    }

    /** Hiding without a report (a translation that breaks the rules, say). */
    @PostMapping("/admin/hidden/{target}/{targetId}")
    void hide(@PathVariable String target, @PathVariable long targetId, @RequestBody Reason body) {
        moderation.hide(access.requireSiteRole(SiteRole.MODERATOR), target, targetId, body.reason());
    }

    @PostMapping("/admin/hidden/{target}/{targetId}/restore")
    void restore(@PathVariable String target, @PathVariable long targetId) {
        moderation.restore(access.requireSiteRole(SiteRole.MODERATOR), target, targetId);
    }

    // ---- administrators --------------------------------------------------------------------------

    @GetMapping("/admin/users")
    List<Staff.Person> users(@RequestParam(defaultValue = "") String q) {
        return staff.find(access.requireSiteRole(SiteRole.ADMIN), q);
    }

    record Role(String role) {
    }

    @PutMapping("/admin/users/{nick}/role")
    void role(@PathVariable String nick, @RequestBody Role body) {
        staff.setRole(access.requireSiteRole(SiteRole.ADMIN), nick, body.role());
    }

    // ---- the owner -------------------------------------------------------------------------------

    record SiteSettingsView(int relayInactiveMonths, boolean registrationOpen, boolean adultEnabled) {
    }

    @GetMapping("/admin/settings")
    SiteSettingsView settings() {
        access.requireSiteRole(SiteRole.OWNER);
        return current();
    }

    @PutMapping("/admin/settings")
    SiteSettingsView saveSettings(@RequestBody SiteSettingsView body) {
        Viewer owner = access.requireSiteRole(SiteRole.OWNER);
        if (body.relayInactiveMonths() < 1 || body.relayInactiveMonths() > 24) {
            throw UserFacingException.badRequest("Строк естафети — від 1 до 24 місяців.");
        }
        SiteSettingsView before = current();
        settings.put(SiteSettings.RELAY_INACTIVE_MONTHS, String.valueOf(body.relayInactiveMonths()), owner.accountId());
        settings.put(SiteSettings.REGISTRATION_OPEN, String.valueOf(body.registrationOpen()), owner.accountId());
        settings.put(SiteSettings.ADULT_ENABLED, String.valueOf(body.adultEnabled()), owner.accountId());
        audit.record(owner.accountId(), "settings", "site", null, Map.of("before", before, "after", body));
        return current();
    }

    private SiteSettingsView current() {
        return new SiteSettingsView(settings.integer(SiteSettings.RELAY_INACTIVE_MONTHS, 3),
                settings.flag(SiteSettings.REGISTRATION_OPEN, true), settings.flag(SiteSettings.ADULT_ENABLED, true));
    }

    record Entry(long id, String actor, String action, String targetType, Long targetId, Map<String, Object> details,
            OffsetDateTime createdAt) {
    }

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    @GetMapping("/admin/audit")
    List<Entry> audit(@RequestParam(required = false) Long before) {
        access.requireSiteRole(SiteRole.OWNER);
        return db.select(AUDIT_LOG.ID, ACCOUNT.NICK, AUDIT_LOG.ACTION, AUDIT_LOG.TARGET_TYPE, AUDIT_LOG.TARGET_ID, AUDIT_LOG.DETAILS,
                        AUDIT_LOG.CREATED_AT)
                .from(AUDIT_LOG).join(ACCOUNT).on(ACCOUNT.ID.eq(AUDIT_LOG.ACTOR_ID))
                .where(before == null ? DSL.noCondition() : AUDIT_LOG.ID.lt(before))
                .orderBy(AUDIT_LOG.ID.desc()).limit(50)
                .fetch(r -> new Entry(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), json.readValue(r.value6().data(), MAP),
                        r.value7()));
    }
}
