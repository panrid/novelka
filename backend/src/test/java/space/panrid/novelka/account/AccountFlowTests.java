package space.panrid.novelka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AccountFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "довгий пароль 42";

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    Browser browser;
    String nick;
    String email;

    @BeforeEach
    void newVisitor() {
        browser = new Browser(port);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        nick = "reader_" + suffix;
        email = "Reader." + suffix + "@example.com";
    }

    @Test
    void registrationNeedsTheEmailConfirmedBeforeSigningIn() {
        assertThat(register(nick, email).status()).isEqualTo(202);
        assertThat(mailbox.to(email)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Підтвердіть пошту на Новелці"));

        Response early = login(nick, PASSWORD);
        assertThat(early.status()).isEqualTo(403);
        assertThat(read(early).path("reason").asString()).isEqualTo("email-not-verified");

        Response verified = browser.post("/api/auth/verify", json("token", mailbox.tokenFrom(email, "/verify")));
        assertThat(verified.status()).isEqualTo(200);
        assertThat(read(verified).path("nick").asString()).isEqualTo(nick);
        assertThat(read(verified).path("emailVerified").asBoolean()).isTrue();

        // The confirmation link signed the person in.
        assertThat(read(browser.get("/api/me")).path("nick").asString()).isEqualTo(nick);

        assertThat(browser.post("/api/auth/logout", "{}").status()).isEqualTo(204);
        assertThat(browser.get("/api/me").status()).isEqualTo(204);
    }

    @Test
    void aConfirmationLinkWorksOnce() {
        register(nick, email);
        String token = mailbox.tokenFrom(email, "/verify");

        assertThat(browser.post("/api/auth/verify", json("token", token)).status()).isEqualTo(200);
        Response again = new Browser(port).post("/api/auth/verify", json("token", token));

        assertThat(again.status()).isEqualTo(400);
        assertThat(read(again).path("detail").asString()).contains("Посилання вже не діє");
    }

    @Test
    void signInAcceptsNickOrEmailInAnyCase() {
        registerVerified(nick, email);

        assertThat(new Browser(port).post("/api/auth/login", json("login", nick.toUpperCase(), "password", PASSWORD))
                .status()).isEqualTo(200);
        assertThat(new Browser(port).post("/api/auth/login", json("login", email.toLowerCase(), "password", PASSWORD))
                .status()).isEqualTo(200);

        Response wrong = new Browser(port).post("/api/auth/login", json("login", nick, "password", "не той пароль"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(read(wrong).path("detail").asString()).isEqualTo("Неправильний нік, пошта або пароль.");
    }

    @Test
    void nicksAndEmailsAreUniqueRegardlessOfCase() {
        register(nick, email);

        Response sameNick = register(nick.toUpperCase(), "other." + email);
        assertThat(sameNick.status()).isEqualTo(409);
        assertThat(read(sameNick).path("detail").asString()).isEqualTo("Цей нік уже зайнятий. Оберіть інший.");

        Response sameEmail = register(nick + "x", email.toUpperCase());
        assertThat(sameEmail.status()).isEqualTo(409);
        assertThat(read(sameEmail).path("detail").asString()).contains("Акаунт із цією поштою вже є");
    }

    @Test
    void rejectsLookalikeNicksAndShortPasswords() {
        Response mixed = register("раnrid", email); // Cyrillic «р» and «а» with Latin «nrid»
        assertThat(mixed.status()).isEqualTo(400);
        assertThat(read(mixed).path("detail").asString()).contains("або латиницею, або кирилицею");

        Response cyrillic = register("Мавка_" + nick.substring(7, 11).replaceAll("[a-z]", "ж"), email);
        assertThat(cyrillic.status()).isEqualTo(202);

        Response shortPassword = browser.post("/api/auth/register",
                json("nick", nick, "email", "x" + email, "password", "коротко"));
        assertThat(shortPassword.status()).isEqualTo(400);
        assertThat(read(shortPassword).path("detail").asString()).contains("щонайменше з 10 символів");
    }

    @Test
    void passwordResetSignsOutOtherSessionsAndOldLinksStopWorking() {
        registerVerified(nick, email);
        Browser phone = new Browser(port);
        assertThat(phone.post("/api/auth/login", json("login", nick, "password", PASSWORD)).status()).isEqualTo(200);

        assertThat(browser.post("/api/auth/password-reset", json("email", "nobody@example.com")).status())
                .isEqualTo(202);
        assertThat(mailbox.to("nobody@example.com")).isEmpty();

        assertThat(browser.post("/api/auth/password-reset", json("email", email)).status()).isEqualTo(202);
        String token = mailbox.tokenFrom(email, "/reset");
        String newPassword = "новий пароль 2026";

        Response reset = browser.post("/api/auth/password-reset/confirm", json("token", token, "password", newPassword));
        assertThat(reset.status()).isEqualTo(200);
        assertThat(read(browser.get("/api/me")).path("nick").asString()).isEqualTo(nick);

        assertThat(phone.get("/api/me").status()).as("the other device is signed out").isEqualTo(204);
        assertThat(login(nick, PASSWORD).status()).isEqualTo(401);
        assertThat(new Browser(port).post("/api/auth/login", json("login", nick, "password", newPassword)).status())
                .isEqualTo(200);
        assertThat(new Browser(port).post("/api/auth/password-reset/confirm",
                json("token", token, "password", "ще один пароль")).status()).isEqualTo(400);
    }

    @Test
    void changesWithoutTheCsrfTokenAreRejected() {
        Response response = browser.postWithoutCsrf("/api/auth/login", json("login", nick, "password", PASSWORD));

        assertThat(response.status()).isEqualTo(403);
        assertThat(read(response).path("detail").asString()).isEqualTo("Сторінка застаріла. Оновіть її й спробуйте ще раз.");
    }

    private Response register(String nick, String email) {
        return browser.post("/api/auth/register", json("nick", nick, "email", email, "password", PASSWORD));
    }

    private Response login(String login, String password) {
        return new Browser(port).post("/api/auth/login", json("login", login, "password", password));
    }

    private void registerVerified(String nick, String email) {
        assertThat(register(nick, email).status()).isEqualTo(202);
        assertThat(new Browser(port).post("/api/auth/verify", json("token", mailbox.tokenFrom(email, "/verify")))
                .status()).isEqualTo(200);
    }

    private static JsonNode read(Response response) {
        return JSON.readTree(response.body());
    }
}
