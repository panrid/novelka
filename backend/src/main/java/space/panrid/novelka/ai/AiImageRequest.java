package space.panrid.novelka.ai;

/**
 * A picture to draw.
 *
 * @param aspectRatio such as 3:4, 16:9 or 1:1
 * @param estimateMicroUsd what one picture of this model is expected to cost, reserved before the call
 */
public record AiImageRequest(String model, String prompt, String aspectRatio, long estimateMicroUsd, AiTag tag) {
}
