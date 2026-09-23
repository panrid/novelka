package panrid.space.novelka.server.comment;

/** chapter 0 means the novel's own discussion. */
public record CommentRequest(Integer chapter, String body) {
}
