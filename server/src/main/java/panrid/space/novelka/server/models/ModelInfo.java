package panrid.space.novelka.server.models;

/**
 * One provider model. Prices are USD per million tokens and null when the provider does not publish a fixed price.
 * {@code limitation} explains why an unsuitable model cannot run the Novelka pipeline.
 */
public record ModelInfo(String id, String name, Integer contextLength, Double inputUsdM, Double outputUsdM,
        boolean suitable, String limitation) {
}
