package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/** What the owner changes; null means «leave as is». */
public record EditionChanges(String title, String author, List<Block> description, List<String> tags, String status,
        Boolean adult) {
}
