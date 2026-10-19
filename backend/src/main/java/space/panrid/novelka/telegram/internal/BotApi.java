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
    public boolean sendPhoto(long chatId, String photoUrl, String html) {
        Reply reply = call("sendPhoto", Map.of("chat_id", chatId, "photo", photoUrl, "caption", html, "parse_mode", "HTML"),
                Duration.ofSeconds(30));
        if (reply.status() == 403 || reply.status() == 400 && reply.body().contains("chat not found")) {
            return false;
        }
        return reply.status() == 200 || send(chatId, html);
    }

    @Override
    public Delivery deliver(long chatId, String photoUrl, String html) {
        if (photoUrl != null) {
            Reply reply = call("sendPhoto", Map.of("chat_id", chatId, "photo", photoUrl, "caption", html, "parse_mode", "HTML"),
                    Duration.ofSeconds(30));
            Delivery photo = delivery(reply);
            // A picture Telegram cannot fetch: the caption goes as a plain message.
            if (photo.outcome() != Outcome.REFUSED) {
                return photo;
            }
        }
        return delivery(call("sendMessage", Map.of("chat_id", chatId, "text", html, "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true)), Duration.ofSeconds(20)));
    }

    /** 403 and «chat not found»: gone; 429 (with retry_after), 5xx and no answer: later; other 4xx: refused. */
    private Delivery delivery(Reply reply) {
        if (reply.status() == 200) {
            return Delivery.of(Outcome.SENT);
        }
        if (reply.status() == 403 || reply.status() == 400 && reply.body().contains("chat not found")) {
            return Delivery.of(Outcome.GONE);
        }
        if (reply.status() == 429) {
            Duration wait = null;
            try {
                long seconds = json.readTree(reply.body()).path("parameters").path("retry_after").asLong(0);
                wait = seconds > 0 ? Duration.ofSeconds(seconds) : null;
            } catch (RuntimeException unreadable) {
                // no hint: the queue's own pause
            }
            return new Delivery(Outcome.RETRY, wait);
        }
        if (reply.status() == 0 || reply.status() >= 500) {
            return Delivery.of(Outcome.RETRY);
        }
        log.warn("Telegram refused a message: {} {}", reply.status(), reply.body());
        return Delivery.of(Outcome.REFUSED);
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
