package panrid.space.novelka.server.account;

public enum Role {
    READER, MODERATOR, ADMIN, OWNER;

    public boolean includes(Role required) {
        return ordinal() >= required.ordinal();
    }
}
