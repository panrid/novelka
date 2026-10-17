package space.panrid.novelka.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** OpenRouter's refusals told apart: a flagged text is not a bad key. */
class AiErrorsTests {

    @Test
    void aFlaggedTextIsModerationAndOtherRefusalsSayWhatOpenRouterSaid() {
        String flagged = "{\"error\":{\"code\":403,\"message\":\"anthropic/claude requires moderation on OpenRouter. Your input was flagged for \\\"violence\\\".\","
                + "\"metadata\":{\"reasons\":[\"violence\"]}}}";
        assertThat(AiService.moderated(flagged)).isTrue();
        assertThat(AiService.moderated("{\"error\":{\"code\":403,\"message\":\"Key limit exceeded\"}}")).isFalse();
        assertThat(AiService.openRouterMessage("{\"error\":{\"code\":403,\"message\":\"Key limit exceeded\"}}")).isEqualTo("Key limit exceeded");
        assertThat(AiService.openRouterMessage("<html>Forbidden</html>")).isNull();
    }
}
