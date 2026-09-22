package panrid.space.novelka.server.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.support.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public final class OpenRouterCreditsService {
    private static final URI CREDITS_URL = URI.create("https://openrouter.ai/api/v1/credits");
    private final String managementKey;
    private final URI creditsUrl;
    private final HttpClient http;

    @Autowired
    public OpenRouterCreditsService(@Value("${novelka.openrouter.management-key:}") String managementKey) {
        this(managementKey, CREDITS_URL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    OpenRouterCreditsService(String managementKey, URI creditsUrl, HttpClient http) {
        this.managementKey = managementKey;
        this.creditsUrl = creditsUrl;
        this.http = http;
    }

    public OpenRouterCredits read() {
        if (managementKey.isBlank()) return new OpenRouterCredits(false, null, null, null);
        var request = HttpRequest.newBuilder(creditsUrl)
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + managementKey)
                .header("Accept", "application/json")
                .GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "OpenRouter відхилив management key. Перевірте ключ для перегляду балансу на сервері.");
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "OpenRouter не надав баланс (HTTP " + response.statusCode() + "). Спробуйте оновити пізніше.");
            JsonNode data = Json.M.readTree(response.body()).path("data");
            double total = data.path("total_credits").asDouble(Double.NaN);
            double used = data.path("total_usage").asDouble(Double.NaN);
            if (!Double.isFinite(total) || !Double.isFinite(used))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "OpenRouter повернув баланс у неочікуваному форматі.");
            return new OpenRouterCredits(true, total, used, total - used);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Перевірку балансу перервано.");
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не вдалося отримати баланс OpenRouter. Перевірте зʼєднання сервера з OpenRouter.");
        }
    }
}
