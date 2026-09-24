package space.panrid.novelka.catalog;

import java.time.OffsetDateTime;

/** Writes to the catalog. Reads join the tables directly (see architecture.md). */
public interface Catalog {

    /** A novel entered by hand (own translation or original work) with its first edition. */
    EditionRef createNovel(NewNovel novel);

    /** Called by the text module whenever chapters of an edition are published. */
    void recordPublication(long editionId, int publishedChapters, OffsetDateTime at);
}
