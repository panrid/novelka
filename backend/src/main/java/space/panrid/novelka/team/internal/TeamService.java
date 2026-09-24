package space.panrid.novelka.team.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.platform.text.Handles;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.MyTeam;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.TeamRole;
import space.panrid.novelka.team.Teams;

@Service
class TeamService implements Teams {

    static final int NAME_MAX = 60;
    static final int MAX_MEMBERS = 50;

    private final DSLContext db;

    TeamService(DSLContext db) {
        this.db = db;
    }

    @Override
    @Transactional
    public long personalTeam(long ownerAccountId) {
        Optional<Long> existing = db.select(TEAM.ID).from(TEAM)
                .where(TEAM.OWNER_ID.eq(ownerAccountId).and(TEAM.PERSONAL))
                .fetchOptional(TEAM.ID);
        if (existing.isPresent()) {
            return existing.get();
        }
        String nick = db.select(ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.eq(ownerAccountId)).fetchOne(ACCOUNT.NICK);
        String handle = freeHandle(nick);
        return db.insertInto(TEAM)
                .set(TEAM.HANDLE, handle)
                .set(TEAM.HANDLE_KEY, Handles.key(handle))
                .set(TEAM.OWNER_ID, ownerAccountId)
                .set(TEAM.PERSONAL, true)
                .returning(TEAM.ID)
                .fetchOne(TEAM.ID);
    }

    @Override
    public Optional<TeamInfo> find(long teamId) {
        return infos().where(TEAM.ID.eq(teamId)).fetchOptional(TeamService::info);
    }

    @Override
    public Optional<TeamInfo> findByHandle(String handle) {
        return infos().where(TEAM.HANDLE_KEY.eq(Handles.key(handle))).fetchOptional(TeamService::info);
    }

    @Override
    public Optional<TeamRole> roleOf(long teamId, long accountId) {
        if (db.fetchExists(TEAM, TEAM.ID.eq(teamId).and(TEAM.OWNER_ID.eq(accountId)))) {
            return Optional.of(TeamRole.OWNER);
        }
        return db.select(TEAM_MEMBER.ROLE).from(TEAM_MEMBER)
                .where(TEAM_MEMBER.TEAM_ID.eq(teamId).and(TEAM_MEMBER.ACCOUNT_ID.eq(accountId)))
                .fetchOptional(r -> TeamRole.valueOf(r.value1().toUpperCase(java.util.Locale.ROOT)));
    }

    @Override
    public List<MyTeam> teamsOf(long accountId) {
        personalTeam(accountId);
        List<MyTeam> result = new ArrayList<>();
        infos().where(TEAM.OWNER_ID.eq(accountId)).orderBy(TEAM.PERSONAL.desc(), TEAM.CREATED_AT)
                .forEach(r -> result.add(new MyTeam(info(r), TeamRole.OWNER)));
        db.select(TEAM.ID, TEAM.HANDLE, DSL.coalesce(TEAM.NAME, ACCOUNT.NICK), TEAM_MEMBER.ROLE)
                .from(TEAM_MEMBER).join(TEAM).on(TEAM.ID.eq(TEAM_MEMBER.TEAM_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(TEAM_MEMBER.ACCOUNT_ID.eq(accountId))
                .orderBy(TEAM_MEMBER.ADDED_AT)
                .forEach(r -> result.add(new MyTeam(new TeamInfo(r.value1(), r.value2(), r.value3()),
                        TeamRole.valueOf(r.value4().toUpperCase(java.util.Locale.ROOT)))));
        return result;
    }

    /** A new named team; the creator becomes its owner. */
    @Transactional
    TeamInfo create(long ownerId, String rawName, String rawHandle) {
        String name = name(rawName);
        String handle = Handles.check(rawHandle, "Адреса команди");
        requireFreeName(name, null);
        requireFreeHandle(handle, null);
        long id = db.insertInto(TEAM)
                .set(TEAM.NAME, name)
                .set(TEAM.NAME_KEY, Handles.key(name))
                .set(TEAM.HANDLE, handle)
                .set(TEAM.HANDLE_KEY, Handles.key(handle))
                .set(TEAM.OWNER_ID, ownerId)
                .returning(TEAM.ID)
                .fetchOne(TEAM.ID);
        return find(id).orElseThrow();
    }

    /** Name and handle; an empty name on the personal team means «show my nick». */
    @Transactional
    TeamInfo rename(long teamId, String rawName, String rawHandle) {
        boolean personal = db.fetchExists(TEAM, TEAM.ID.eq(teamId).and(TEAM.PERSONAL));
        String name = rawName == null || rawName.isBlank() ? null : name(rawName);
        if (name == null && !personal) {
            throw UserFacingException.badRequest("Вкажіть назву команди.");
        }
        String handle = Handles.check(rawHandle, "Адреса команди");
        if (name != null) {
            requireFreeName(name, teamId);
        }
        requireFreeHandle(handle, teamId);
        db.update(TEAM)
                .set(TEAM.NAME, name)
                .set(TEAM.NAME_KEY, name == null ? null : Handles.key(name))
                .set(TEAM.HANDLE, handle)
                .set(TEAM.HANDLE_KEY, Handles.key(handle))
                .where(TEAM.ID.eq(teamId))
                .execute();
        return find(teamId).orElseThrow();
    }

    @Transactional
    void addMember(long teamId, long actorId, String nick, TeamRole role) {
        if (role == TeamRole.OWNER) {
            throw UserFacingException.badRequest("Власник у команди один.");
        }
        long accountId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(Handles.key(nick)))
                .fetchOptional(ACCOUNT.ID)
                .orElseThrow(() -> UserFacingException.notFound("Користувача з таким ніком немає."));
        if (roleOf(teamId, accountId).isPresent()) {
            throw UserFacingException.conflict("Ця людина вже в команді.");
        }
        if (db.fetchCount(TEAM_MEMBER, TEAM_MEMBER.TEAM_ID.eq(teamId)) >= MAX_MEMBERS) {
            throw UserFacingException.badRequest("У команді може бути до %d людей.".formatted(MAX_MEMBERS));
        }
        db.insertInto(TEAM_MEMBER)
                .set(TEAM_MEMBER.TEAM_ID, teamId)
                .set(TEAM_MEMBER.ACCOUNT_ID, accountId)
                .set(TEAM_MEMBER.ROLE, role.code())
                .set(TEAM_MEMBER.ADDED_BY, actorId)
                .execute();
    }

    @Transactional
    void setRole(long teamId, long accountId, TeamRole role) {
        if (role == TeamRole.OWNER) {
            throw UserFacingException.badRequest("Власник у команди один.");
        }
        int changed = db.update(TEAM_MEMBER).set(TEAM_MEMBER.ROLE, role.code())
                .where(TEAM_MEMBER.TEAM_ID.eq(teamId).and(TEAM_MEMBER.ACCOUNT_ID.eq(accountId)))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Цієї людини немає в команді.");
        }
    }

    @Transactional
    void removeMember(long teamId, long accountId) {
        db.deleteFrom(TEAM_MEMBER)
                .where(TEAM_MEMBER.TEAM_ID.eq(teamId).and(TEAM_MEMBER.ACCOUNT_ID.eq(accountId)))
                .execute();
    }

    private SelectOnConditionStep<Record3<Long, String, String>> infos() {
        return db.select(TEAM.ID, TEAM.HANDLE, DSL.coalesce(TEAM.NAME, ACCOUNT.NICK))
                .from(TEAM).join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID));
    }

    private static TeamInfo info(Record3<Long, String, String> r) {
        return new TeamInfo(r.value1(), r.value2(), r.value3());
    }

    private static String name(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw UserFacingException.badRequest("Назва команди — від 1 до %d символів.".formatted(NAME_MAX));
        }
        return name;
    }

    private void requireFreeName(String name, Long except) {
        var same = TEAM.NAME_KEY.eq(Handles.key(name));
        if (db.fetchExists(TEAM, except == null ? same : same.and(TEAM.ID.ne(except)))) {
            throw new UserFacingException(HttpStatus.CONFLICT, "Команда з такою назвою вже є.");
        }
    }

    private void requireFreeHandle(String handle, Long except) {
        var same = TEAM.HANDLE_KEY.eq(Handles.key(handle));
        if (db.fetchExists(TEAM, except == null ? same : same.and(TEAM.ID.ne(except)))) {
            throw new UserFacingException(HttpStatus.CONFLICT, "Ця адреса команди вже зайнята.");
        }
    }

    /** The nick itself if free among team handles, otherwise nick-2, nick-3… */
    private String freeHandle(String nick) {
        String candidate = nick;
        for (int n = 2; db.fetchExists(TEAM, TEAM.HANDLE_KEY.eq(Handles.key(candidate))); n++) {
            candidate = nick + "-" + n;
        }
        return candidate;
    }
}
