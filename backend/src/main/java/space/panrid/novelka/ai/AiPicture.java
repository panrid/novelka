package space.panrid.novelka.ai;

/** A drawn picture: its bytes as the model sent them, and what it cost. */
public record AiPicture(long callId, byte[] content, String mime, long costMicroUsd) {
}
