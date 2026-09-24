package space.panrid.novelka.account;

/** Role on the whole site. Roles inside a team are a separate axis (team module). */
public enum SiteRole {
    READER,
    MODERATOR,
    ADMIN,
    OWNER;

    public boolean atLeast(SiteRole other) {
        return compareTo(other) >= 0;
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SiteRole fromCode(String code) {
        return valueOf(code.toUpperCase(java.util.Locale.ROOT));
    }
}
