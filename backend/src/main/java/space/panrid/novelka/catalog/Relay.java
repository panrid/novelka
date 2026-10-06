package space.panrid.novelka.catalog;

public interface Relay {

    RelayState state(long editionId);

    /** A team asks the owner for permission to continue; one open request per team. */
    long request(long editionId, long teamId, long requesterId, String message);

    void answer(long editionId, long requestId, boolean grant);

    /** Starts the continuing edition, numbered from the chapter after the old one's last. */
    EditionRef continueEdition(long oldEditionId, long teamId, String kind);

    /**
     * A team's own translation of a novel already on the site, no permission needed (етап 17):
     * from the first chapter, or from the chapter after {@code after}'s last one.
     *
     * @param after an edition of the same novel to go on from, or null to start from the beginning
     */
    EditionRef ownEdition(String novelSlug, long teamId, Long after);
}
