package space.panrid.novelka.ai;

import java.util.Map;

/**
 * @param schema    JSON schema of the answer (strict structured output), or null for text
 * @param maxTokens upper bound of the answer; the reserve before the call is counted from it
 * @param attempt   bumped by the caller when a paid answer turned out unusable, so the same
 *                  request is sent again instead of the saved answer being reused
 */
public record AiRequest(String model, String system, String user, String schemaName, Map<String, Object> schema,
        int maxTokens, AiPrice price, AiTag tag, int attempt) {
}
