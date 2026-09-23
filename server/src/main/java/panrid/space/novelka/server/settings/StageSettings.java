package panrid.space.novelka.server.settings;

/** Manual prices stay the fallback; with catalogPricing a task uses the provider catalog price when it is complete. */
public record StageSettings(String stage, String model, double inputUsdM, double outputUsdM, boolean catalogPricing) {
}
