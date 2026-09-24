package space.panrid.novelka.catalog;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Writes to the catalog. Reads join the tables directly (see architecture.md). */
public interface Catalog {

    /** A novel entered by hand (own translation or original work) with its first edition. */
    EditionRef createNovel(NewNovel novel);

    /** Called by the text module whenever chapters of an edition are published. */
    void recordPublication(long editionId, int publishedChapters, OffsetDateTime at);

    Optional<EditionData> edition(long editionId);

    /**
     * For a novel the team entered itself (own translation or original work) the novel's own
     * title, author, description and tags change; for an imported novel the edition keeps its
     * own title and description on top of the novel's.
     */
    void updateEdition(long editionId, EditionChanges changes);

    void setCover(long editionId, Long imageId);
}
