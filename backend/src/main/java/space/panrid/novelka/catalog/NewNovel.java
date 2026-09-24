package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * @param kind human, machine, mixed or original
 */
public record NewNovel(String title, String author, List<Block> description, List<String> tags, String kind,
        boolean adult, long teamId, Long authorAccountId) {
}
