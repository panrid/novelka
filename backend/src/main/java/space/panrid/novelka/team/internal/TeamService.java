package space.panrid.novelka.team.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.Locale;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.Teams;

@Service
class TeamService implements Teams {

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
                .set(TEAM.HANDLE_KEY, handle.toLowerCase(Locale.ROOT))
                .set(TEAM.OWNER_ID, ownerAccountId)
                .set(TEAM.PERSONAL, true)
                .returning(TEAM.ID)
                .fetchOne(TEAM.ID);
    }

    @Override
    public Optional<TeamInfo> find(long teamId) {
        return db.select(TEAM.ID, TEAM.HANDLE, DSL.coalesce(TEAM.NAME, ACCOUNT.NICK))
                .from(TEAM).join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID))
                .where(TEAM.ID.eq(teamId))
                .fetchOptional(r -> new TeamInfo(r.value1(), r.value2(), r.value3()));
    }

    /** The nick itself if free among team handles, otherwise nick-2, nick-3… */
    private String freeHandle(String nick) {
        String candidate = nick;
        for (int n = 2; db.fetchExists(TEAM, TEAM.HANDLE_KEY.eq(candidate.toLowerCase(Locale.ROOT))); n++) {
            candidate = nick + "-" + n;
        }
        return candidate;
    }
}
