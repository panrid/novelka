package panrid.space.novelka.server.moderation;

/** Why a message or novel is hidden; shown to readers next to the collapsed content. Optional, up to 300 characters. */
public record HideRequest(String reason) {
    public String normalized() {
        String value = reason == null ? "" : reason.strip();
        if (value.length() > 300) throw new IllegalArgumentException("Причина приховування — до 300 символів.");
        return value;
    }
}
