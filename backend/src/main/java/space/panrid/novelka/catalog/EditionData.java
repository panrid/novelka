package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/** An edition as the Studio edits it. */
public record EditionData(long editionId, long novelId, String novelSlug, long teamId, String title, String author,
        List<Block> description, List<String> tags, String kind, String status, boolean adult, Long coverImageId,
        int chapterCount, boolean ownNovel) {
}
