package space.panrid.novelka.support;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.util.UUID;

/** Creates confirmed, signed-in accounts for tests. */
public final class Accounts {

    public static final String PASSWORD = "довгий пароль 42";

    public record Person(String nick, String email, Browser browser) {
    }

    private Accounts() {
    }

    public static Person signedIn(int port, TestMailbox mailbox) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nick = "user_" + suffix;
        String email = "user." + suffix + "@example.com";
        Browser browser = new Browser(port);
        assertThat(browser.post("/api/auth/register", json("nick", nick, "email", email, "password", PASSWORD))
                .status()).isEqualTo(202);
        assertThat(browser.post("/api/auth/verify", json("token", mailbox.tokenFrom(email, "/verify"))).status())
                .isEqualTo(200);
        return new Person(nick, email, browser);
    }
}
