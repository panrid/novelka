package space.panrid.novelka.ai;

/**
 * @param reused  the saved answer to an identical earlier request; nothing was paid now
 * @param cut     the model stopped at the token limit, so the answer is probably incomplete
 */
public record AiAnswer(long callId, String content, long costMicroUsd, boolean reused, boolean cut) {
}
