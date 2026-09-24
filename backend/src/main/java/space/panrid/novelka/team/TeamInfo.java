package space.panrid.novelka.team;

/** How a team is shown: its name (or the owner's nick) and its handle for $mentions and links. */
public record TeamInfo(long id, String handle, String name) {
}
