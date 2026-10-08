package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * An edition as the Studio edits it.
 *
 * @param pausedUntil  the day a paused translation means to go on; null if not said
 * @param source       where the novel comes from: {@code syosetu}, {@code manual} or {@code original}
 */
public record EditionData(long editionId, long novelId, String novelSlug, long teamId, String title, String author,
        List<Block> description, List<String> tags, String kind, String status, boolean adult, Long coverImageId,
        int chapterCount, boolean ownNovel, java.time.LocalDate pausedUntil, String source, NovelFacts facts) {
}
