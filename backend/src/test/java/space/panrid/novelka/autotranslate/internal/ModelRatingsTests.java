package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.autotranslate.internal.ModelRatings.Rating;

class ModelRatingsTests {

    @Test
    void knownTranslatorsAreRecommended() {
        assertThat(ModelRatings.of("anthropic/claude-sonnet-5")).isEqualTo(Rating.RECOMMENDED);
        assertThat(ModelRatings.of("anthropic/claude-opus-5.5")).isEqualTo(Rating.RECOMMENDED);
        assertThat(ModelRatings.of("google/gemini-3.8-flash")).isEqualTo(Rating.RECOMMENDED);
        assertThat(ModelRatings.of("openai/gpt-4.1-mini")).isEqualTo(Rating.RECOMMENDED);
        assertThat(ModelRatings.of("deepseek/deepseek-v3.2")).isEqualTo(Rating.RECOMMENDED);
        assertThat(ModelRatings.of("mistralai/mistral-large-2512")).isEqualTo(Rating.USUAL);
    }

    @Test
    void codeRolePlayModerationRoutersAndTinyModelsCannotTranslate() {
        assertThat(ModelRatings.of("qwen/qwen3-coder-next")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("openai/gpt-5.3-codex")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("bytedance-seed/seed-2.0-code")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("gryphe/mythomax-l2-13b")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("openai/gpt-oss-safeguard-20b")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("openrouter/auto")).isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("openai/gpt-4.1-mini:batch")).as("batch API only").isEqualTo(Rating.AWFUL);
        assertThat(ModelRatings.of("liquid/lfm-2.5-2.6b:free")).isEqualTo(Rating.AWFUL);
    }

    @Test
    void smallAndFreeModelsAreWeakButTranslatorsStay() {
        assertThat(ModelRatings.of("ibm-granite/granite-4.2-8b")).isEqualTo(Rating.WEAK);
        assertThat(ModelRatings.of("openai/gpt-5.4-nano")).isEqualTo(Rating.WEAK);
        assertThat(ModelRatings.of("qwen/qwen3.8-27b:free")).isEqualTo(Rating.WEAK);
        assertThat(ModelRatings.of("tencent/hy-mt2-7b")).isEqualTo(Rating.USUAL);
        assertThat(ModelRatings.of("qwen/qwen3.6-35b-a3b")).as("a mixture of experts counts by its total").isEqualTo(Rating.USUAL);
    }
}
