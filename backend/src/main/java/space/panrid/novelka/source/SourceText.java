package space.panrid.novelka.source;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * One chapter of the original. Block ids ({@code s1}, {@code s2}…) follow the page, so a
 * translation keeps the same ids and nothing can go missing unnoticed.
 *
 * @param chars characters without spaces; the price in шаги comes from it
 * @param hash  SHA-256 of the blocks: the original changed if it changes
 */
public record SourceText(long id, int number, String title, List<Block> blocks, int chars, String hash) {
}
