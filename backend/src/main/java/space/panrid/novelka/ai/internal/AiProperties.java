package space.panrid.novelka.ai.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey        OpenRouter key for model calls
 * @param managementKey OpenRouter management key: only for reading the credits left
 */
@ConfigurationProperties("novelka.ai")
record AiProperties(String baseUrl, String apiKey, String managementKey) {

    AiProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://openrouter.ai/api/v1" : baseUrl.replaceAll("/+$", "");
    }
}
