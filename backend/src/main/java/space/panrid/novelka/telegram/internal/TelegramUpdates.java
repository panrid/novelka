package space.panrid.novelka.telegram.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.telegram.TelegramApi;

/**
 * How what people write to the bot reaches the site: on start the site points Telegram's
 * webhook at itself, or, in development without HTTPS, asks Telegram itself in a loop.
 */
@Component
class TelegramUpdates {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdates.class);

    private final TelegramApi telegram;
    private final TelegramLinks links;
    private final TelegramProperties properties;
    private final SiteProperties site;
    private volatile boolean running = true;

    TelegramUpdates(TelegramApi telegram, TelegramLinks links, TelegramProperties properties, SiteProperties site) {
        this.telegram = telegram;
        this.links = links;
        this.properties = properties;
        this.site = site;
    }

    /** The webhook's secret, derived from the bot token so every copy of the site agrees on it. */
    String secret() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.botToken().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal("novelka-webhook".getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (!telegram.configured() || properties.mode().equals("off")) {
            return;
        }
        boolean https = site.publicUrl().startsWith("https://");
        String mode = properties.mode().equals("auto") ? (https ? "webhook" : "polling") : properties.mode();
        if (mode.equals("webhook")) {
            Thread.ofVirtual().name("telegram-webhook").start(
                    () -> telegram.setWebhook(site.link(TelegramController.WEBHOOK), secret()));
        } else if (mode.equals("polling")) {
            Thread.ofVirtual().name("telegram-polling").start(this::poll);
        }
    }

    private void poll() {
        telegram.deleteWebhook();
        long offset = 0;
        while (running) {
            try {
                for (TelegramApi.Update update : telegram.updates(offset)) {
                    offset = Math.max(offset, update.updateId() + 1);
                    if (update.chatId() != 0) {
                        links.handle(update);
                    }
                }
            } catch (RuntimeException error) {
                log.warn("Telegram polling: {}", error.toString());
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    @jakarta.annotation.PreDestroy
    void stop() {
        running = false;
    }
}
