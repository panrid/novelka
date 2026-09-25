package space.panrid.novelka.messaging;

/** Hiding a reported message from everyone in its conversation. */
public interface MessageModeration {

    void hide(long messageId, long moderatorId, String reason);

    void restore(long messageId);
}
