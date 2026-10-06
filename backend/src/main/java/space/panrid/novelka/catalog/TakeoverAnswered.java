package space.panrid.novelka.catalog;

/** The owner answered a request to continue their translation. */
public record TakeoverAnswered(long requesterId, String title, String novelSlug, boolean granted) {
}
