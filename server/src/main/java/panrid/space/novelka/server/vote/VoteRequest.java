package panrid.space.novelka.server.vote;

/** 1 is up, -1 is down, 0 removes the account's vote. */
public record VoteRequest(int value) {
}
