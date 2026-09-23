package panrid.space.novelka.server.vote;

/** Score is the sum of votes; mine is the viewer's own vote (0 when none or anonymous). */
public record VoteSummary(long score, int mine) {
}
