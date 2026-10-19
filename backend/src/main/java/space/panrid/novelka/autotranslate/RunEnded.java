package space.panrid.novelka.autotranslate;

/**
 * An autotranslation run is over, published in the transaction that ends it.
 *
 * @param state done (every chapter translated), failed (stopped by an error, may be continued)
 *              or cancelled (stopped by a person)
 * @param error what stopped it, for a failed run; null otherwise
 */
public record RunEnded(long jobId, long editionId, String state, int first, int last, String error) {
}
