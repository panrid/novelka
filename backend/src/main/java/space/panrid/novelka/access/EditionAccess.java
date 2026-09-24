package space.panrid.novelka.access;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.team.TeamRole;

/** Who is acting on which edition, and in what role in its team. */
public record EditionAccess(Viewer viewer, long editionId, long teamId, TeamRole role) {
}
