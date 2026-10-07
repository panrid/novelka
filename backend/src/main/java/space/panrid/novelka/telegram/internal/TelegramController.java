package space.panrid.novelka.telegram.internal;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.telegram.TelegramApi;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
class TelegramController {

    static final String WEBHOOK = "/api/telegram/webhook";

    record Settings(Boolean notifyInbox, Boolean notifyChapters, Boolean notifyMessages) {
    }

    record StartLink(String url) {
    }

    private final TelegramLinks links;
    private final TelegramApi telegram;
    private final TelegramUpdates updates;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    TelegramController(TelegramLinks links, TelegramApi telegram, TelegramUpdates updates, CurrentUser currentUser, JsonMapper json) {
        this.links = links;
        this.telegram = telegram;
        this.updates = updates;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping("/api/me/telegram")
    TelegramLinks.Status status() {
        return links.status(currentUser.requireSignedIn().accountId());
    }

    /** The one-time t.me link that ties the chat where «Start» is pressed. */
    @PostMapping("/api/me/telegram/link")
    StartLink link() {
        long me = currentUser.requireSignedIn().accountId();
        if (!telegram.configured()) {
            throw UserFacingException.notFound("Telegram на сайті не налаштовано.");
        }
        return new StartLink(links.startLink(me));
    }

    @PatchMapping("/api/me/telegram")
    TelegramLinks.Status settings(@RequestBody Settings body) {
        long me = currentUser.requireSignedIn().accountId();
        links.settings(me, body.notifyInbox(), body.notifyChapters(), body.notifyMessages());
        return links.status(me);
    }

    @DeleteMapping("/api/me/telegram")
    TelegramLinks.Status unlink() {
        long me = currentUser.requireSignedIn().accountId();
        links.unlink(me);
        return links.status(me);
    }

    /** Telegram posts what people write to the bot; the secret header proves it is Telegram. */
    @PostMapping(WEBHOOK)
    ResponseEntity<Void> webhook(@RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody String body) {
        if (!telegram.configured() || secret == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), updates.secret().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        JsonNode update = json.readTree(body);
        TelegramApi.Update parsed = BotApi.parse(update);
        if (parsed != null) {
            links.handle(parsed);
        }
        return ResponseEntity.ok().build();
    }
}
