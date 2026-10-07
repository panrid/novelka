package space.panrid.novelka.account.internal;

import tools.jackson.databind.JsonNode;

/**
 * The signed-in person's own account, as the web app sees it. Only they receive this.
 *
 * @param google      a Google account is tied for signing in
 * @param hasPassword false for an account made through Google until a password is chosen
 * @param appearance  how they want the site and the reader to look ({@link Appearance}); empty for the defaults
 */
record Me(long id, String nick, String email, boolean emailVerified, String role, String bio,
        String avatarUrl, String dmPolicy, boolean showReading, boolean adultConfirmed, boolean showShah, boolean studioInMenu,
        boolean google, boolean hasPassword, JsonNode appearance) {
}
