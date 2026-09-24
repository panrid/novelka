package panrid.space.novelka.server.correction;

/** Submits the author's drafts of a novel; with {@code chapter} only that chapter's drafts. */
public record SubmitRequest(String novelId, Integer chapter) {
}
