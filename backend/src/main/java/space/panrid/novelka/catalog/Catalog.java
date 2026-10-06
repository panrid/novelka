package space.panrid.novelka.catalog;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Writes to the catalog. Reads join the tables directly (see architecture.md). */
public interface Catalog {

    /** A novel entered by hand (own translation or original work) with its first edition. */
    EditionRef createNovel(NewNovel novel);

    /**
     * A novel from Syosetu with a machine-translated edition for the team. A novel already
     * imported is reused (its chapter count refreshed), and so is the team's edition of it.
     */
    EditionRef importNovel(ImportedNovel novel);

    /** Novel already imported from this source, if any. */
    Optional<Long> novelBySource(String sourceKey);

    /**
     * A new address for the edition's novel; the old one keeps working (етап 17).
     *
     * @return the address as stored: lower case, a–z, digits and hyphens
     */
    String changeSlug(long editionId, String slug);

    /** The novel's address now, for an address it had before. */
    Optional<String> currentSlug(String oldSlug);

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

    /** An administrator hides a translation from the catalog and its readers (the team still sees it). */
    void hideEdition(long editionId, long adminId, String reason);

    void restoreEdition(long editionId);
}
