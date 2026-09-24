package space.panrid.novelka.account;

/** The signed-in person as of this request. */
public record Viewer(long accountId, String nick, SiteRole role, boolean adultConfirmed) {
}
