package space.panrid.novelka.ai.internal;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.AiTransport;

@Component
@EnableConfigurationProperties(AiProperties.class)
class OpenRouterTransport implements AiTransport {

    private final AiProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    OpenRouterTransport(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    @Override
    public Reply chat(String body) throws NotSent, Lost {
        if (!configured()) {
            throw new NotSent("no key");
        }
        return send(HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMinutes(6))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("X-Title", "Novelka")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    @Override
    public Reply credits() throws NotSent, Lost {
        String key = properties.managementKey() == null || properties.managementKey().isBlank()
                ? properties.apiKey() : properties.managementKey();
        if (key == null || key.isBlank()) {
            return new Reply(401, "");
        }
        return send(HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/credits"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + key)
                .GET().build());
    }

    private Reply send(HttpRequest request) throws NotSent, Lost {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        } catch (ConnectException | HttpConnectTimeoutException | UnresolvedAddressException error) {
            throw new NotSent(error.getClass().getSimpleName());
        } catch (IOException error) {
            throw new Lost(error.getClass().getSimpleName());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new Lost("interrupted");
        }
    }
}
