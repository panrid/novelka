package panrid.space.novelka.server.comment;

/** chapter 0 means the novel's own discussion; replyTo is a comment of the same thread or null. */
public record CommentRequest(Integer chapter, String body, Long replyTo) {
}
