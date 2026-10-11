package space.panrid.novelka.autotranslate;

/**
 * The original has chapters the translation can take: the weekly look at the source site (or
 * «Перевірити зараз» in the Studio) found that it grew from {@code first - 1} to {@code last}.
 */
public record SourceChaptersFound(long editionId, int first, int last) {
}
