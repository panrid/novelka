package panrid.space.novelka.server.account;

public enum Role {
    READER, EDITOR, ADMIN, OWNER;

    public boolean includes(Role required) {
        return ordinal() >= required.ordinal();
    }
}
