package space.panrid.novelka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ProfileSettingsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    Person person;

    @BeforeEach
    void signIn() {
        person = Accounts.signedIn(port, mailbox);
    }

    @Test
    void settingsChangeOnlyWhatIsSent() {
        Response response = person.browser().patch("/api/me",
                json("bio", "  Читаю ісекаї й слайс-оф-лайф.  ", "dmPolicy", "nobody", "adultConfirmed", true));

        JsonNode me = read(response);
        assertThat(me.path("bio").asString()).isEqualTo("Читаю ісекаї й слайс-оф-лайф.");
        assertThat(me.path("dmPolicy").asString()).isEqualTo("nobody");
        assertThat(me.path("adultConfirmed").asBoolean()).isTrue();

        JsonNode after = read(person.browser().patch("/api/me", json("showReading", false)));
        assertThat(after.path("bio").asString()).isEqualTo("Читаю ісекаї й слайс-оф-лайф.");
        assertThat(after.path("showReading").asBoolean()).isFalse();

        Response tooLong = person.browser().patch("/api/me", json("bio", "а".repeat(501)));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(read(tooLong).path("detail").asString()).isEqualTo("«Про себе» — до 500 символів.");
    }

    @Test
    void nickChangesAtMostOnceAMonthAndOldLinksFollow() {
        String renamed = "Мавка" + person.nick().substring(5, 9).replaceAll("[^0-9]", "7");

        assertThat(read(person.browser().post("/api/me/nick", json("nick", renamed))).path("nick").asString())
                .isEqualTo(renamed);

        Response again = person.browser().post("/api/me/nick", json("nick", person.nick()));
        assertThat(again.status()).isEqualTo(429);
        assertThat(read(again).path("detail").asString()).startsWith("Нік можна змінювати раз на 30 днів.");

        JsonNode profile = read(new Browser(port).get("/api/users/" + person.nick()));
        assertThat(profile.path("nick").asString()).as("the old nick leads to the renamed person").isEqualTo(renamed);
        assertThat(new Browser(port).get("/api/users/nobody_here").status()).isEqualTo(404);
    }

    @Test
    void aNewEmailReplacesTheOldOneOnlyAfterItsLinkIsOpened() {
        String newEmail = "new." + person.email();

        Response wrongPassword = person.browser().post("/api/me/email", json("email", newEmail, "password", "не той"));
        assertThat(wrongPassword.status()).isEqualTo(400);

        assertThat(person.browser().post("/api/me/email", json("email", newEmail, "password", Accounts.PASSWORD))
                .status()).isEqualTo(202);
        assertThat(signIn(person.email())).as("the old address still works").isEqualTo(200);

        String token = mailbox.tokenFrom(newEmail, "/confirm-email");
        assertThat(read(new Browser(port).post("/api/auth/confirm-email", json("token", token)))
                .path("email").asString()).isEqualTo(newEmail);

        assertThat(signIn(newEmail)).isEqualTo(200);
        assertThat(signIn(person.email())).isEqualTo(401);
        assertThat(mailbox.to(person.email())).anySatisfy(mail ->
                assertThat(mail.subject()).isEqualTo("Пошту акаунта на Новелці змінено"));
    }

    @Test
    void changingThePasswordKeepsThisDeviceAndSignsOutOthers() {
        Browser phone = new Browser(port);
        phone.post("/api/auth/login", json("login", person.nick(), "password", Accounts.PASSWORD));
        String newPassword = "інший довгий пароль";

        Response changed = person.browser().post("/api/me/password",
                json("currentPassword", Accounts.PASSWORD, "newPassword", newPassword));

        assertThat(changed.status()).isEqualTo(200);
        assertThat(person.browser().get("/api/me").status()).isEqualTo(200);
        assertThat(phone.get("/api/me").status()).isEqualTo(204);
        assertThat(mailbox.to(person.email())).anySatisfy(mail ->
                assertThat(mail.subject()).isEqualTo("Пароль на Новелці змінено"));
    }

    @Test
    void anAvatarIsCroppedSquareResizedAndServed() throws IOException {
        Response uploaded = person.browser().upload("/api/me/avatar", "file", "photo.png", "image/png",
                png(600, 400, Color.ORANGE));

        assertThat(uploaded.status()).isEqualTo(200);
        String url = read(uploaded).path("avatarUrl").asString();
        assertThat(url).startsWith("/media/").endsWith("-256.jpg");

        Browser.BinaryResponse file = new Browser(port).download(url);
        assertThat(file.status()).isEqualTo(200);
        assertThat(file.contentType()).isEqualTo("image/jpeg");
        BufferedImage served = ImageIO.read(new ByteArrayInputStream(file.body()));
        assertThat(served.getWidth()).isEqualTo(256);
        assertThat(served.getHeight()).isEqualTo(256);

        assertThat(read(person.browser().delete("/api/me/avatar")).path("avatarUrl").isNull()).isTrue();
    }

    @Test
    void somethingThatIsNotAPictureIsRefusedPolitely() {
        Response response = person.browser().upload("/api/me/avatar", "file", "virus.png", "image/png",
                "<?php echo 'hi'; ?>".getBytes());

        assertThat(response.status()).isEqualTo(400);
        assertThat(read(response).path("detail").asString()).isEqualTo("Це не схоже на картинку. Підходять JPEG, PNG і WebP.");
    }

    @Test
    void guestsCannotChangeSettings() {
        Response response = new Browser(port).patch("/api/me", json("bio", "привіт"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(read(response).path("detail").asString()).isEqualTo("Увійдіть, щоб продовжити.");
    }

    private int signIn(String login) {
        return new Browser(port).post("/api/auth/login", json("login", login, "password", Accounts.PASSWORD)).status();
    }

    static byte[] png(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static JsonNode read(Response response) {
        return JSON.readTree(response.body());
    }
}
