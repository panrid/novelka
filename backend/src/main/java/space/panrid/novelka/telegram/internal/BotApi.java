package space.panrid.novelka.telegram.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import space.panrid.novelka.telegram.TelegramApi;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** https://core.telegram.org/bots/api over plain HTTPS. */
@Component
class BotApi implements TelegramApi {

    private static final Logger log = LoggerFactory.getLogger(BotApi.class);

    private final TelegramProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    BotApi(TelegramProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public String botUsername() {
        return properties.botUsername().replaceFirst("^@", "");
    }

    @Override
    public boolean send(long chatId, String html) {
        Reply reply = call("sendMessage", Map.of("chat_id", chatId, "text", html, "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true)), Duration.ofSeconds(20));
        // 403: blocked by the person; 400 «chat not found»: the chat is gone. Anything else may pass.
        return !(reply.status() == 403 || reply.status() == 400 && reply.body().contains("chat not found"));
    }

    @Override
    public void setWebhook(String url, String secret) {
        Reply reply = call("setWebhook", Map.of("url", url, "secret_token", secret, "allowed_updates", List.of("message")),
                Duration.ofSeconds(20));
        if (reply.status() != 200) {
            log.warn("Telegram setWebhook answered {}: {}", reply.status(), reply.body());
        }
    }

    @Override
    public void deleteWebhook() {
        call("deleteWebhook", Map.of(), Duration.ofSeconds(20));
    }

    @Override
    public List<Update> updates(long offset) {
        Reply reply = call("getUpdates", Map.of("offset", offset, "timeout", 25, "allowed_updates", List.of("message")),
                Duration.ofSeconds(40));
        List<Update> updates = new ArrayList<>();
        if (reply.status() != 200) {
            return updates;
        }
        for (JsonNode update : json.readTree(reply.body()).path("result")) {
            Update parsed = parse(update);
            updates.add(parsed != null ? parsed : new Update(update.path("update_id").asLong(), 0, "", ""));
        }
        return updates;
    }

    /** One update as Telegram posts it; null when it is not a text message. */
    static Update parse(JsonNode update) {
        JsonNode message = update.path("message");
        if (!message.path("text").isString()) {
            return null;
        }
        return new Update(update.path("update_id").asLong(), message.path("chat").path("id").asLong(),
                message.path("text").asString(), message.path("from").path("username").asString(""));
    }

    private record Reply(int status, String body) {
    }

    private Reply call(String method, Map<String, Object> body, Duration timeout) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                            URI.create("https://api.telegram.org/bot" + properties.botToken() + "/" + method))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        } catch (IOException error) {
            log.warn("Telegram {} failed: {}", method, error.toString());
            return new Reply(0, "");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Reply(0, "");
        }
    }
}
