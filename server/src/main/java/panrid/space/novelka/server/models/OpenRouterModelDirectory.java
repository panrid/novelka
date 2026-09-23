package panrid.space.novelka.server.models;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import panrid.space.novelka.core.support.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Public OpenRouter model list: pricing is USD per token, converted here to USD per million tokens. */
@Component
public final class OpenRouterModelDirectory implements ModelDirectory {
    private final URI url;
    private final HttpClient http;

    @Autowired
    public OpenRouterModelDirectory(@Value("${novelka.openrouter.models-url:https://openrouter.ai/api/v1/models}") String url) {
        this(URI.create(url), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    OpenRouterModelDirectory(URI url, HttpClient http) {
        this.url = url;
        this.http = http;
    }

    @Override
    public String provider() { return "openrouter"; }

    @Override
    public List<ModelInfo> fetch() throws Exception {
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("OpenRouter не надав список моделей (HTTP " + response.statusCode() + ").");
        var data = Json.M.readTree(response.body()).path("data");
        if (!data.isArray()) throw new IllegalStateException("OpenRouter повернув список моделей у неочікуваному форматі.");
        var models = new ArrayList<ModelInfo>();
        for (var node : data) {
            String id = node.path("id").asText("");
            if (id.isBlank()) continue;
            Double input = perMillion(node.path("pricing").path("prompt"));
            Double output = perMillion(node.path("pricing").path("completion"));
            int context = node.path("context_length").asInt(0);
            models.add(new ModelInfo(id, node.path("name").asText(id), context > 0 ? context : null, input, output,
                    limitation(node, input, output) == null, limitation(node, input, output)));
        }
        return models;
    }

    /** Novelka needs text in and out, JSON-schema answers, dictionary tools and a fixed price for budgets. */
    private static String limitation(JsonNode node, Double input, Double output) {
        var parameters = new HashSet<String>();
        node.path("supported_parameters").forEach(item -> parameters.add(item.asText()));
        if (!contains(node.path("architecture").path("input_modalities"), "text")
                || !contains(node.path("architecture").path("output_modalities"), "text")) return "Не працює з текстом.";
        if (!parameters.contains("response_format") && !parameters.contains("structured_outputs")) return "Немає структурованих JSON-відповідей.";
        if (!parameters.contains("tools")) return "Немає інструментів для пошуку в словнику.";
        if (input == null || output == null) return "Немає фіксованої ціни за токен.";
        return null;
    }

    private static boolean contains(JsonNode values, String expected) {
        for (var value : values) if (value.asText().equals(expected)) return true;
        return false;
    }

    private static Double perMillion(JsonNode price) {
        if (price.isMissingNode() || price.isNull()) return null;
        try {
            double value = Double.parseDouble(price.asText());
            return Double.isFinite(value) && value >= 0 ? Math.round(value * 1_000_000 * 1_000_000d) / 1_000_000d : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
