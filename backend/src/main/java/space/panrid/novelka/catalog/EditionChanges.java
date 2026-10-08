package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * What the owner changes; null means «leave as is».
 *
 * @param pausedUntil an ISO date, or empty to clear it
 * @param facts       the novel's names and the original's state; each null field is left as is, an empty one clears it
 */
public record EditionChanges(String title, String author, List<Block> description, List<String> tags, String status,
        Boolean adult, String pausedUntil, NovelFacts facts) {
}
