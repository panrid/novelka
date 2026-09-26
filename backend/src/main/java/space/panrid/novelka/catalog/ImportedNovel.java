package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * A novel from a source site. The original title and author are kept only for the translation
 * prompts; readers see the Ukrainian ones.
 *
 * @param source             the site's provider id, such as {@code syosetu}
 * @param sourceLanguage     the original's language, such as {@code ja} or {@code en}
 * @param sourceChapterCount the last chapter that can be taken from the site
 */
public record ImportedNovel(String source, String sourceLanguage, String sourceKey, String sourceUrl, String titleOriginal,
        String authorOriginal, String title, String author, List<Block> description, int sourceChapterCount, boolean adult, long teamId) {
}
