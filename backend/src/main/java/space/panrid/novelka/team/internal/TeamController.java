package space.panrid.novelka.team.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Handles;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.TeamRole;

@RestController
class TeamController {

    record TeamRequest(String name, String handle) {
    }

    record MemberRequest(String nick, String role) {
    }

    record Person(String nick, String avatarUrl, String role) {
    }

    record TeamEdition(String novelSlug, String title, String coverUrl, int chapterCount, String kind, String status) {
    }

    record TeamPage(String handle, String name, boolean personal, List<Person> members, List<TeamEdition> editions,
            String viewerRole) {
    }

    record MyTeamView(String handle, String name, String role) {
    }

    static final int MAX_OWNED_TEAMS = 10;

    private final TeamService teams;
    private final CurrentUser currentUser;
    private final DSLContext db;
    private final Images images;

    TeamController(TeamService teams, CurrentUser currentUser, DSLContext db, Images images) {
        this.teams = teams;
        this.currentUser = currentUser;
        this.db = db;
        this.images = images;
    }

    @GetMapping("/api/me/teams")
    List<MyTeamView> mine() {
        Viewer viewer = currentUser.requireSignedIn();
        return teams.teamsOf(viewer.accountId()).stream()
                .map(team -> new MyTeamView(team.team().handle(), team.team().name(), team.role().code()))
                .toList();
    }

    @GetMapping("/api/teams/{handle}")
    TeamPage page(@PathVariable String handle) {
        TeamInfo team = teams.findByHandle(handle).orElseThrow(TeamController::noTeam);
        Optional<Viewer> viewer = currentUser.viewer();
        boolean adult = viewer.map(Viewer::adultConfirmed).orElse(false);

        List<Person> members = new ArrayList<>();
        var owner = db.select(ACCOUNT.NICK, ACCOUNT.AVATAR_IMAGE_ID, TEAM.PERSONAL).from(TEAM)
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID)).where(TEAM.ID.eq(team.id())).fetchOne();
        var rows = db.select(ACCOUNT.NICK, ACCOUNT.AVATAR_IMAGE_ID, TEAM_MEMBER.ROLE).from(TEAM_MEMBER)
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM_MEMBER.ACCOUNT_ID))
                .where(TEAM_MEMBER.TEAM_ID.eq(team.id()))
                .orderBy(DSL.case_(TEAM_MEMBER.ROLE).when("translator", 0).otherwise(1), ACCOUNT.NICK)
                .fetch();
        List<Long> avatarIds = new ArrayList<>(rows.map(r -> r.value2()));
        avatarIds.add(owner.value2());
        Map<Long, StoredImage> avatars = images.findAll(avatarIds);
        members.add(new Person(owner.value1(), avatar(avatars, owner.value2()), "owner"));
        rows.forEach(r -> members.add(new Person(r.value1(), avatar(avatars, r.value2()), r.value3())));

        var visible = EDITION.TEAM_ID.eq(team.id()).and(EDITION.HIDDEN_AT.isNull()).and(EDITION.CHAPTER_COUNT.gt(0));
        var editionRows = db.select(NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), EDITION.COVER_IMAGE_ID,
                        EDITION.CHAPTER_COUNT, EDITION.KIND, EDITION.STATUS)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(adult ? visible : visible.and(EDITION.ADULT.isFalse()))
                .orderBy(EDITION.LAST_PUBLISHED_AT.desc().nullsLast())
                .fetch();
        Map<Long, StoredImage> covers = images.findAll(editionRows.map(r -> r.value3()));
        List<TeamEdition> editions = editionRows.map(r -> new TeamEdition(r.value1(), r.value2(),
                r.value3() == null || !covers.containsKey(r.value3()) ? null : covers.get(r.value3()).url(480),
                r.value4(), r.value5(), r.value6()));

        String viewerRole = viewer.flatMap(v -> teams.roleOf(team.id(), v.accountId())).map(TeamRole::code).orElse(null);
        return new TeamPage(team.handle(), team.name(), owner.value3(), members, editions, viewerRole);
    }

    @PostMapping("/api/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    MyTeamView create(@RequestBody TeamRequest body) {
        Viewer viewer = currentUser.requireSignedIn();
        long owned = teams.teamsOf(viewer.accountId()).stream().filter(team -> team.role() == TeamRole.OWNER).count();
        if (owned > MAX_OWNED_TEAMS) {
            throw UserFacingException.badRequest("Можна мати до %d власних команд.".formatted(MAX_OWNED_TEAMS));
        }
        TeamInfo team = teams.create(viewer.accountId(), body.name(), body.handle());
        return new MyTeamView(team.handle(), team.name(), TeamRole.OWNER.code());
    }

    @PatchMapping("/api/teams/{handle}")
    MyTeamView rename(@PathVariable String handle, @RequestBody TeamRequest body) {
        TeamInfo team = requireOwner(handle);
        TeamInfo renamed = teams.rename(team.id(), body.name(), body.handle());
        return new MyTeamView(renamed.handle(), renamed.name(), TeamRole.OWNER.code());
    }

    @PostMapping("/api/teams/{handle}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addMember(@PathVariable String handle, @RequestBody MemberRequest body) {
        TeamInfo team = requireOwner(handle);
        teams.addMember(team.id(), currentUser.requireSignedIn().accountId(), body.nick(), role(body.role()));
    }

    @PatchMapping("/api/teams/{handle}/members/{nick}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setRole(@PathVariable String handle, @PathVariable String nick, @RequestBody MemberRequest body) {
        TeamInfo team = requireOwner(handle);
        teams.setRole(team.id(), accountId(nick), role(body.role()));
    }

    /** The owner removes anyone; a member may leave on their own. */
    @DeleteMapping("/api/teams/{handle}/members/{nick}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable String handle, @PathVariable String nick) {
        Viewer viewer = currentUser.requireSignedIn();
        TeamInfo team = teams.findByHandle(handle).orElseThrow(TeamController::noTeam);
        long accountId = accountId(nick);
        boolean owner = teams.roleOf(team.id(), viewer.accountId()).filter(TeamRole::manages).isPresent();
        if (!owner && accountId != viewer.accountId()) {
            throw forbidden();
        }
        teams.removeMember(team.id(), accountId);
    }

    private TeamInfo requireOwner(String handle) {
        Viewer viewer = currentUser.requireSignedIn();
        TeamInfo team = teams.findByHandle(handle).orElseThrow(TeamController::noTeam);
        teams.roleOf(team.id(), viewer.accountId()).filter(TeamRole::manages).orElseThrow(TeamController::forbidden);
        return team;
    }

    private long accountId(String nick) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(Handles.key(nick)))
                .fetchOptional(ACCOUNT.ID)
                .orElseThrow(() -> UserFacingException.notFound("Користувача з таким ніком немає."));
    }

    private static TeamRole role(String code) {
        try {
            TeamRole role = TeamRole.valueOf(code == null ? "" : code.toUpperCase(Locale.ROOT));
            if (role != TeamRole.OWNER) {
                return role;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the message below
        }
        throw UserFacingException.badRequest("Роль може бути «перекладач» або «редактор».");
    }

    private static String avatar(Map<Long, StoredImage> avatars, Long id) {
        return id == null || !avatars.containsKey(id) ? null : avatars.get(id).url(96);
    }

    private static UserFacingException noTeam() {
        return UserFacingException.notFound("Такої команди немає.");
    }

    private static UserFacingException forbidden() {
        return new UserFacingException(HttpStatus.FORBIDDEN, "Це може зробити лише власник команди.");
    }
}
