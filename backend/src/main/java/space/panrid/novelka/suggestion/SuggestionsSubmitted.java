package space.panrid.novelka.suggestion;

/** A reader sent their drafted suggestions in a translation to its team. */
public record SuggestionsSubmitted(long editionId, long authorId, int count) {
}
