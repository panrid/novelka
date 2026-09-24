package panrid.space.novelka.server.chat;

/** replyTo is an earlier chat message or null. */
public record ChatMessageRequest(String body, Long replyTo) {
}
