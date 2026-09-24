package panrid.space.novelka.server.library;

/** New shelf for a novel; an empty status removes it from the library. */
public record LibraryRequest(String status) {
}
