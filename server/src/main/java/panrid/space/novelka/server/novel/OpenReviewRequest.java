package panrid.space.novelka.server.novel;

/** {@code open=true} lets every account, including future ones, decide on the novel's corrections. */
public record OpenReviewRequest(boolean open) {
}
