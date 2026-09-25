package space.panrid.novelka.catalog;

public interface Relay {

    RelayState state(long editionId);

    /** A team asks the owner for permission to continue; one open request per team. */
    long request(long editionId, long teamId, long requesterId, String message);

    void answer(long editionId, long requestId, boolean grant);

    /** Starts the continuing edition, numbered from the chapter after the old one's last. */
    EditionRef continueEdition(long oldEditionId, long teamId, String kind);
}
