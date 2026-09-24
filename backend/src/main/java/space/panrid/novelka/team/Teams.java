package space.panrid.novelka.team;

import java.util.List;
import java.util.Optional;

public interface Teams {

    /** The account's personal team, created on first use with the owner's nick as its handle. */
    long personalTeam(long ownerAccountId);

    Optional<TeamInfo> find(long teamId);

    Optional<TeamInfo> findByHandle(String handle);

    /** The account's role in the team, if any. */
    Optional<TeamRole> roleOf(long teamId, long accountId);

    /** Teams the account owns or belongs to, the personal one first. */
    List<MyTeam> teamsOf(long accountId);
}
