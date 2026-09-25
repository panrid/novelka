package space.panrid.novelka.text;

/** Chapters readers can open for the first time: positions {@code first}..{@code last}. */
public record ChaptersPublished(long editionId, int first, int last) {
}
