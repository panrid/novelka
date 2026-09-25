package space.panrid.novelka.ai;

import java.util.List;

/**
 * A model on OpenRouter as its catalogue describes it.
 *
 * @param outputs what it answers with: text, image
 */
public record AiModel(String id, String name, double inputPerMillion, double outputPerMillion, int contextLength,
        List<String> outputs) {
}
