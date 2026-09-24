package space.panrid.novelka.team;

import java.util.Optional;

public interface Teams {

    /** The account's personal team, created on first use with the owner's nick as its handle. */
    long personalTeam(long ownerAccountId);

    Optional<TeamInfo> find(long teamId);
}
