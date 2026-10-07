package space.panrid.novelka.catalog;

import java.util.List;

import space.panrid.novelka.platform.text.Block;

/**
 * @param kind      human, machine, mixed or original
 * @param sourceUrl where the original is, when the team knows it (a proposal's link); null otherwise
 */
public record NewNovel(String title, String author, List<Block> description, List<String> tags, String kind,
        boolean adult, long teamId, Long authorAccountId, String sourceUrl) {

    public NewNovel(String title, String author, List<Block> description, List<String> tags, String kind,
            boolean adult, long teamId, Long authorAccountId) {
        this(title, author, description, tags, kind, adult, teamId, authorAccountId, null);
    }
}
