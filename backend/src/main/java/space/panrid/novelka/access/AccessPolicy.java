package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.EDITION;

import java.util.Optional;
import java.util.function.Predicate;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamRole;
import space.panrid.novelka.team.Teams;

/**
 * Site-wide and team checks (рішення 3, 6, 13): everyone in the team edits text; the owner
 * and translators add chapters and pictures; only the owner changes the novel's data.
 */
@Component
public class AccessPolicy {

    private final CurrentUser currentUser;
    private final Teams teams;
    private final DSLContext db;

    AccessPolicy(CurrentUser currentUser, Teams teams, DSLContext db) {
        this.currentUser = currentUser;
        this.teams = teams;
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
}
