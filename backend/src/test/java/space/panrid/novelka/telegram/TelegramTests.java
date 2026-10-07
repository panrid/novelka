package space.panrid.novelka.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;
import static space.panrid.novelka.support.Eventually.eventually;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.FakeTelegram;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class TelegramTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong UPDATES = new AtomicLong();

    @LocalServerPort
    int port;

    @Autowired
    FakeTelegram telegram;

    @Autowired
    TestMailbox mailbox;

    @Test
    void theSettingsLinkTiesTheChatWhereStartIsPressedOnce() {
        Person person = Accounts.signedIn(port, mailbox);
        long chat = newChat();
        JsonNode before = read(person.browser().get("/api/me/telegram"));
        assertThat(before.path("available").asBoolean()).isTrue();
        assertThat(before.path("linked").asBoolean()).isFalse();

        String url = read(person.browser().post("/api/me/telegram/link", "{}")).path("url").asString();
        assertThat(url).startsWith("https://t.me/novelka_test_bot?start=");
        String code = url.substring(url.indexOf("start=") + 6);
        assertThat(write(chat, "/start " + code)).isEqualTo(200);

        JsonNode after = read(person.browser().get("/api/me/telegram"));
        assertThat(after.path("linked").asBoolean()).isTrue();
        assertThat(after.path("username").asString()).isEqualTo("reader" + chat);
        assertThat(telegram.to(chat)).last().asString().contains("Готово").contains(person.nick());

        long other = newChat();
        write(other, "/start " + code);
        assertThat(telegram.to(other)).last().asString().contains("Посилання вже не діє");
        write(other, "привіт");
        assertThat(telegram.to(other)).last().asString().contains("Налаштування → Telegram");
    }

    @Test
    void onlyTelegramMayPostToTheWebhook() throws IOException, InterruptedException {
        HttpResponse<String> forged = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/telegram/webhook"))
                .header("Content-Type", "application/json").header("X-Telegram-Bot-Api-Secret-Token", "guess")
                .POST(HttpRequest.BodyPublishers.ofString(update(1, "/stop"))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(forged.statusCode()).isEqualTo(403);
    }

    @Test
    void messagesPingOncePerUnreadStreakAndOnlyWhatThePersonChose() {
        Person anna = Accounts.signedIn(port, mailbox);
        Person bohdan = Accounts.signedIn(port, mailbox);
        long chat = tie(bohdan);

        long conversation = read(anna.browser().post("/api/conversations/direct", json("nick", bohdan.nick()))).path("id").asLong();
        anna.browser().post("/api/conversations/" + conversation + "/messages", json("body", "Привіт <b>!"));
        List<String> got = eventually(() -> telegram.to(chat), sent -> sent.size() >= 2);
        assertThat(got.get(1)).contains(anna.nick()).contains("Привіт &lt;b&gt;!").contains("/inbox/messages/" + conversation);

        anna.browser().post("/api/conversations/" + conversation + "/messages", json("body", "Ти тут?"));
        // A chat mention goes to the inbox: one more message, and the second line above never arrives.
        anna.browser().post("/api/chat", json("body", "Глянь, @" + bohdan.nick()));
        got = eventually(() -> telegram.to(chat), sent -> sent.size() >= 3);
        assertThat(got).hasSize(3);
        assertThat(got.get(2)).contains(anna.nick() + " згадує вас у чаті").contains("/inbox/chat");

        bohdan.browser().patch("/api/me/telegram", json("notifyInbox", false));
        anna.browser().post("/api/chat", json("body", "Ще раз, @" + bohdan.nick()));
        // Reading the conversation starts a new streak; this message is the marker that the mention was skipped.
        JsonNode lines = read(bohdan.browser().get("/api/conversations/" + conversation)).path("lines");
        bohdan.browser().post("/api/conversations/" + conversation + "/read",
                json("upTo", lines.get(lines.size() - 1).path("id").asLong()));
        anna.browser().post("/api/conversations/" + conversation + "/messages", json("body", "Останнє"));
        got = eventually(() -> telegram.to(chat), sent -> sent.size() >= 4);
        assertThat(got).hasSize(4);
        assertThat(got.get(3)).contains("Останнє");
    }

    @Test
    void newsAboutTheAccountAndTheResetLinkComeToo() {
        Person person = Accounts.signedIn(port, mailbox);
        long chat = tie(person);
        String nick = "renamed" + ThreadLocalRandom.current().nextInt(100_000);
        assertThat(person.browser().post("/api/me/nick", json("nick", nick)).status()).isEqualTo(200);
        eventually(() -> telegram.to(chat), sent -> sent.stream().anyMatch(text -> text.contains("тепер «" + nick + "»")));

        new Browser(port).post("/api/auth/password-reset", json("email", person.email()));
        String token = mailbox.tokenFrom(person.email(), "/reset");
        eventually(() -> telegram.to(chat), sent -> sent.stream().anyMatch(text -> text.contains("/reset?token=" + token)));
    }

    @Test
    void aBlockedBotOrStopUntiesTheChat() {
        Person person = Accounts.signedIn(port, mailbox);
        long chat = tie(person);
        write(chat, "/stop");
        assertThat(read(person.browser().get("/api/me/telegram")).path("linked").asBoolean()).isFalse();
        assertThat(telegram.to(chat)).last().asString().contains("більше не приходитимуть");

        long again = tie(person);
        telegram.block(again);
        person.browser().post("/api/me/nick", json("nick", "blocked" + ThreadLocalRandom.current().nextInt(100_000)));
        eventually(() -> read(person.browser().get("/api/me/telegram")).path("linked").asBoolean(), linked -> !linked);

        long third = tie(person);
        assertThat(read(person.browser().delete("/api/me/telegram")).path("linked").asBoolean()).isFalse();
        assertThat(telegram.to(third)).last().asString().contains("більше не приходитимуть");
    }

    /** Ties a new chat to the person the way they would: the link from the settings, then «Start». */
    private long tie(Person person) {
        long chat = newChat();
        String url = read(person.browser().post("/api/me/telegram/link", "{}")).path("url").asString();
        assertThat(write(chat, "/start " + url.substring(url.indexOf("start=") + 6))).isEqualTo(200);
        return chat;
    }

    /** Someone writes to the bot from {@code chat}; Telegram posts it to the webhook. */
    private int write(long chat, String text) {
        try {
            return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/telegram/webhook"))
                    .header("Content-Type", "application/json").header("X-Telegram-Bot-Api-Secret-Token", secret())
                    .POST(HttpRequest.BodyPublishers.ofString(update(chat, text))).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode();
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String update(long chat, String text) {
        return JSON.writeValueAsString(java.util.Map.of("update_id", UPDATES.incrementAndGet(), "message", java.util.Map.of(
                "chat", java.util.Map.of("id", chat), "from", java.util.Map.of("username", "reader" + chat), "text", text)));
    }

    private static long newChat() {
        return ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
    }

    /** As the site derives it from the bot token of application-test.yaml. */
    private static String secret() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-token".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal("novelka-webhook".getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JsonNode read(Browser.Response response) {
        return JSON.readTree(response.body());
    }
}
