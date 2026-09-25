package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * A novel from Syosetu. The Japanese title and author are kept only for the translation
 * prompts; readers see the Ukrainian ones.
 */
public record ImportedNovel(String sourceKey, String sourceUrl, String titleOriginal, String authorOriginal,
        String title, String author, List<Block> description, int sourceChapterCount, boolean adult, long teamId) {
}
