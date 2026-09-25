package space.panrid.novelka.ai;

import java.util.List;

/**
 * A model on OpenRouter as its catalogue describes it.
 *
 * @param outputs    what it answers with: text, image
 * @param parameters request parameters it accepts (temperature, structured_outputs…); empty when unknown
 */
public record AiModel(String id, String name, double inputPerMillion, double outputPerMillion, int contextLength,
        List<String> outputs, List<String> parameters) {

    /** Unknown counts as yes: the request then goes as before and OpenRouter decides. */
    public boolean accepts(String parameter) {
        return parameters.isEmpty() || parameters.contains(parameter);
    }
}
