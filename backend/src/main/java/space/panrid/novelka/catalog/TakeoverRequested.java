package space.panrid.novelka.catalog;

/**
 * A team asked to continue a translation (естафета); the owner hears about it on the site.
 *
 * @param message what they wrote, or null
 */
public record TakeoverRequested(long editionId, long ownerId, String title, String teamName, String requesterNick,
        String message) {
}
