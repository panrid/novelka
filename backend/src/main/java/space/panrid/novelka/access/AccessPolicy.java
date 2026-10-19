package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SHAH_BALANCE;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.Optional;
import java.util.function.Predicate;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.AiVisibility;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamRole;
import space.panrid.novelka.team.Teams;

/**
 * Site-wide and team checks (рішення 3, 6, 13): everyone in the team edits text; the owner
 * and translators add chapters and pictures; only the owner changes the novel's data.
 */
@Component
public class AccessPolicy implements AiVisibility {

    private final CurrentUser currentUser;
    private final Teams teams;
    private final SiteSettings settings;
    private final DSLContext db;

    AccessPolicy(CurrentUser currentUser, Teams teams, SiteSettings settings, DSLContext db) {
        this.currentUser = currentUser;
        this.teams = teams;
        this.settings = settings;
        this.db = db;
    }

    /** A member of the edition's team in any role: may edit text and approve suggestions. */
    public EditionAccess requireTextEditor(long editionId) {
        return requireRole(editionId, role -> true);
    }

    /** Owner or translator: adds chapters, imports files, inserts pictures. */
    public EditionAccess requireTranslator(long editionId) {
        return requireRole(editionId, TeamRole::translates);
    }

    /** The team owner: title, description, cover, status, access. */
    public EditionAccess requireEditionOwner(long editionId) {
        return requireRole(editionId, TeamRole::manages);
    }

    /**
     * The translation an API address names (етап 22.3): the novel's slug and its team's handle, as
     * the public API names it. Only the address is resolved here; the rights are asked of the
     * {@code require…} checks after it, so a translation in the bin still says so.
     */
    public long editionAt(String novelSlug, String teamHandle) {
        return db.select(EDITION.ID).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(NOVEL.SLUG.eq(novelSlug), TEAM.HANDLE_KEY.eq(space.panrid.novelka.platform.text.Handles.key(teamHandle)))
                .orderBy(EDITION.DELETED_AT.asc().nullsFirst(), EDITION.ID.desc()).limit(1)
                .fetchOptional(EDITION.ID).orElseThrow(() -> UserFacingException.notFound("Такого перекладу немає."));
    }

    /** The viewer's role in the edition's team, without failing. */
    public Optional<EditionAccess> editionRole(long editionId) {
        Optional<Viewer> viewer = currentUser.viewer();
        Long teamId = db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne(EDITION.TEAM_ID);
        if (viewer.isEmpty() || teamId == null) {
            return Optional.empty();
        }
        return teams.roleOf(teamId, viewer.get().accountId())
                .map(role -> new EditionAccess(viewer.get(), editionId, teamId, role));
    }

    /** Someone who may publish into this team: owner or translator. */
    public TeamRole requireTeamTranslator(long teamId) {
        Viewer viewer = requireSignedIn();
        return teams.roleOf(teamId, viewer.accountId()).filter(TeamRole::translates)
                .orElseThrow(() -> new UserFacingException(HttpStatus.FORBIDDEN,
                        "Публікувати в цій команді можуть лише власник і перекладачі."));
    }

    private EditionAccess requireRole(long editionId, Predicate<TeamRole> allowed) {
        requireSignedIn();
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
        if (db.fetchExists(EDITION, EDITION.ID.eq(editionId), EDITION.DELETED_AT.isNotNull())) {
            throw UserFacingException.notFound("Цей переклад у кошику. Відновіть його в Студії → «Кошик».");
        }
        return editionRole(editionId).filter(access -> allowed.test(access.role()))
                .orElseThrow(() -> new UserFacingException(HttpStatus.FORBIDDEN, "Це вам недоступно."));
    }

    /** The signed-in person, or 401 «Увійдіть, щоб продовжити». */
    public Viewer requireSignedIn() {
        return currentUser.requireSignedIn();
    }

    /** The signed-in person whose site role is at least {@code role}, or 403. */
    public Viewer requireSiteRole(SiteRole role) {
        Viewer viewer = requireSignedIn();
        if (!viewer.role().atLeast(role)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Це вам недоступно.");
        }
        return viewer;
    }

    /**
     * Whether the person sees anything of AI (етап 19, п. 7): while the site owner keeps it hidden
     * from those without шаги, only the owner and people with шаги on the account or held for
     * their runs do. The balance is read from its table: the ledger module itself asks this one.
     */
    @Override
    public boolean seesAi(long accountId, SiteRole role) {
        if (role == SiteRole.OWNER || !settings.flag(SiteSettings.AI_HIDDEN_WITHOUT_SHAHS, true)) {
            return true;
        }
        return db.fetchExists(SHAH_BALANCE, SHAH_BALANCE.ACCOUNT_ID.eq(accountId)
                .and(SHAH_BALANCE.AVAILABLE.plus(SHAH_BALANCE.RESERVED).gt(0)));
    }

    /** The signed-in person who sees AI, or 403. */
    public Viewer requireAi() {
        Viewer viewer = requireSignedIn();
        if (!seesAi(viewer.accountId(), viewer.role())) {
            throw new UserFacingException(HttpStatus.FORBIDDEN,
                    "Інструменти ШІ поки відкриті лише тим, у кого є шаги. Шаги нараховує власник сайту.");
        }
        return viewer;
    }

    /**
     * «Автопереклад із сайту» (етап 19): the site owner, and those the owner let in; they pay with
     * their шаги. Nobody who does not see AI.
     */
    public boolean mayAutotranslate(Viewer viewer) {
        return mayHaveAutotranslate(viewer) && seesAi(viewer.accountId(), viewer.role());
    }

    /** Let in to translate from the original's site, whether or not AI is hidden from them now. */
    public boolean mayHaveAutotranslate(Viewer viewer) {
        return viewer.role() == SiteRole.OWNER
                || db.fetchExists(ACCOUNT, ACCOUNT.ID.eq(viewer.accountId()).and(ACCOUNT.AUTOTRANSLATE));
    }

    /** The signed-in person who may translate from the original's site, or 403. */
    public Viewer requireAutotranslate() {
        Viewer viewer = requireSignedIn();
        if (!mayHaveAutotranslate(viewer)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN,
                    "Автопереклад із сайту вам поки недоступний. Попросіть доступ у «Новому перекладі».");
        }
        return requireAi();
    }

    /**
     * Pasting the original (етап 19) is for everyone signed in: it is not AI. «Перекласти з ШІ» from
     * it is for those who see AI ({@link #requireAi()}).
     */
    public Viewer requireOriginals() {
        return requireSignedIn();
    }
}
