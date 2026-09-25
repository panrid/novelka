package space.panrid.novelka.illustration.internal;

/**
 * @param model            the model that draws
 * @param microUsdPerImage what one picture is expected to cost; shown before drawing and reserved
 * @param style            added to every description, so a novel's pictures look alike
 * @param promptModel      the text model that turns a fragment into a description for the artist
 */
record IllustrationSettings(String model, long microUsdPerImage, String style, String promptModel,
        double promptInputPerMillion, double promptOutputPerMillion) {

    static IllustrationSettings defaults() {
        return new IllustrationSettings("google/gemini-2.5-flash-image", 40_000,
                "detailed Japanese light novel illustration, soft colours, expressive characters, no text or letters in the picture",
                "openai/gpt-4.1-mini", 0.40, 1.60);
    }
}
