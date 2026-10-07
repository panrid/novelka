package space.panrid.novelka.telegram.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The bot. {@code mode} says how its updates arrive: {@code webhook} (Telegram posts them to the
 * site, which needs HTTPS), {@code polling} (the site asks; for development), {@code off}, or
 * {@code auto} — a webhook when the public address is HTTPS, polling otherwise.
 */
@ConfigurationProperties("novelka.telegram")
record TelegramProperties(String botToken, String botUsername, String mode) {

    TelegramProperties {
        botToken = botToken == null ? "" : botToken.strip();
        botUsername = botUsername == null ? "" : botUsername.strip();
        mode = mode == null || mode.isBlank() ? "auto" : mode.strip();
    }

    boolean configured() {
        return !botToken.isEmpty() && !botUsername.isEmpty();
    }

    @Configuration
    @EnableConfigurationProperties(TelegramProperties.class)
    static class Registration {
    }
}
