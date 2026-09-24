package space.panrid.novelka.team;

/**
 * Role inside a team (рішення 3, 6, 13). Independent of the site role.
 */
public enum TeamRole {
    /** Creates editions, manages members, fills the team budget. */
    OWNER,
    /** Adds chapters, edits text, inserts pictures, runs autotranslation. */
    TRANSLATOR,
    /** Edits text directly and approves suggestions. */
    EDITOR;

    public boolean editsText() {
        return true;
    }

    public boolean translates() {
        return this == OWNER || this == TRANSLATOR;
    }

    public boolean manages() {
        return this == OWNER;
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
