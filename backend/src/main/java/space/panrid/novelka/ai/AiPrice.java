package space.panrid.novelka.ai;

/** Dollars per million tokens, used only for the reserve before a call; the real cost comes from OpenRouter. */
public record AiPrice(double inputPerMillion, double outputPerMillion) {
}
