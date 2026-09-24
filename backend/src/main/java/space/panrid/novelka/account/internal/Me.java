package space.panrid.novelka.account.internal;

/** The signed-in person's own account, as the web app sees it. Only they receive this. */
record Me(long id, String nick, String email, boolean emailVerified, String role, String bio,
        String avatarUrl, String dmPolicy, boolean showReading, boolean adultConfirmed, boolean showShah) {
}
