package space.panrid.novelka.suggestion;

/** A team decided on one person's suggestions in a chapter. */
public record SuggestionsReviewed(long editionId, int chapterNumber, long authorId, int accepted, int rejected) {
}
