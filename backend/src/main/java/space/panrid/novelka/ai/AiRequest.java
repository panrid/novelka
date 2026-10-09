package space.panrid.novelka.ai;

import java.util.Map;

/**
 * @param schema    JSON schema of the answer (strict structured output), or null for text
 * @param maxTokens upper bound of the answer; the reserve before the call is counted from it
 * @param attempt   bumped by the caller when a paid answer turned out unusable, so the same
 *                  request is sent again instead of the saved answer being reused
 * @param thinking  how long a model that thinks before answering may think; ignored by the others
 */
public record AiRequest(String model, String system, String user, String schemaName, Map<String, Object> schema,
        int maxTokens, AiPrice price, AiTag tag, int attempt, Thinking thinking) {

    /**
     * NORMAL: as the model likes. BRIEF and OFF are for a model that spent every token on
     * thinking and gave no answer (DeepSeek V4 Flash counting «！！！！！！！！！» on 2026-10-09).
     */
    public enum Thinking { NORMAL, BRIEF, OFF }

    public AiRequest(String model, String system, String user, String schemaName, Map<String, Object> schema,
            int maxTokens, AiPrice price, AiTag tag, int attempt) {
        this(model, system, user, schemaName, schema, maxTokens, price, tag, attempt, Thinking.NORMAL);
    }
}
