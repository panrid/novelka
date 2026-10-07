package space.panrid.novelka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.FakeGoogle;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class GoogleSignInTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    FakeGoogle google;

    @Autowired
    TestMailbox mailbox;

    @Test
    void aNewcomerFromGooglePicksANickAndHasNoPasswordUntilTheyChooseOne() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "g." + suffix + "@gmail.test";
        Browser browser = new Browser(port);
        assertThat(read(browser.get("/api/auth/providers")).path("google").asBoolean()).isTrue();

        String landed = returnFromGoogle(browser, "/api/auth/google?next=/library",
                new GoogleSignIn.Identity("sub-" + suffix, email, true, "Марта " + suffix));
        assertThat(landed).isEqualTo("/login/google");
        JsonNode newcomer = read(browser.get("/api/auth/google/newcomer"));
        assertThat(newcomer.path("email").asString()).isEqualTo(email);
        assertThat(newcomer.path("next").asString()).isEqualTo("/library");
        assertThat(newcomer.path("nick").asString()).as("the Google name made into a nick").isEqualTo("g" + suffix);

        String nick = "marta_" + suffix;
        JsonNode me = read(browser.post("/api/auth/google/newcomer", json("nick", nick)));
        assertThat(me.path("nick").asString()).isEqualTo(nick);
        assertThat(me.path("emailVerified").asBoolean()).isTrue();
        assertThat(me.path("google").asBoolean()).isTrue();
        assertThat(me.path("hasPassword").asBoolean()).isFalse();
        assertThat(read(browser.get("/api/me")).path("nick").asString()).isEqualTo(nick);

        assertThat(browser.delete("/api/me/google").status()).as("no other way in").isEqualTo(400);
        assertThat(browser.post("/api/me/email", json("email", "new." + email, "password", "")).status()).isEqualTo(400);

        // Next time Google opens the same account at once.
        Browser later = new Browser(port);
        assertThat(returnFromGoogle(later, "/api/auth/google", new GoogleSignIn.Identity("sub-" + suffix, email, true, "")))
                .isEqualTo("/");
        assertThat(read(later.get("/api/me")).path("nick").asString()).isEqualTo(nick);

        // A password from the reset letter makes Google optional.
        Browser reset = new Browser(port);
        assertThat(reset.post("/api/auth/password-reset", json("email", email)).status()).isEqualTo(202);
        assertThat(reset.post("/api/auth/password-reset/confirm",
                json("token", mailbox.tokenFrom(email, "/reset"), "password", Accounts.PASSWORD)).status()).isEqualTo(200);
        assertThat(read(reset.get("/api/me")).path("hasPassword").asBoolean()).isTrue();
        assertThat(read(reset.delete("/api/me/google")).path("google").asBoolean()).isFalse();
        assertThat(new Browser(port).post("/api/auth/login", json("login", nick, "password", Accounts.PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void googleWithTheAddressOfAnAccountOpensThatAccountAndTiesIt() {
        Accounts.Person person = Accounts.signedIn(port, mailbox);
        Browser browser = new Browser(port);
        String subject = "sub-" + UUID.randomUUID();
        assertThat(returnFromGoogle(browser, "/api/auth/google?next=/n/x/2", new GoogleSignIn.Identity(subject, person.email(), true, "")))
                .isEqualTo("/n/x/2");
        JsonNode me = read(browser.get("/api/me"));
        assertThat(me.path("nick").asString()).isEqualTo(person.nick());
        assertThat(me.path("google").asBoolean()).isTrue();
        assertThat(me.path("hasPassword").asBoolean()).isTrue();
    }

    @Test
    void anUnconfirmedRegistrationOfTheAddressLosesItsPasswordToTheOwnerOfTheMailbox() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "victim." + suffix + "@gmail.test";
        new Browser(port).post("/api/auth/register", json("nick", "squat_" + suffix, "email", email, "password", Accounts.PASSWORD));

        Browser owner = new Browser(port);
        assertThat(returnFromGoogle(owner, "/api/auth/google", new GoogleSignIn.Identity("sub-" + suffix, email, true, "")))
                .isEqualTo("/");
        JsonNode me = read(owner.get("/api/me"));
        assertThat(me.path("emailVerified").asBoolean()).isTrue();
        assertThat(me.path("hasPassword").asBoolean()).isFalse();
        assertThat(new Browser(port).post("/api/auth/login", json("login", email, "password", Accounts.PASSWORD)).status())
                .as("the password chosen by whoever registered the address").isEqualTo(401);
    }

    @Test
    void anAddressGoogleDidNotConfirmOpensNothing() {
        Accounts.Person person = Accounts.signedIn(port, mailbox);
        Browser browser = new Browser(port);
        String landed = returnFromGoogle(browser, "/api/auth/google", new GoogleSignIn.Identity("sub-" + UUID.randomUUID(), person.email(), false, ""));
        assertThat(decoded(landed)).startsWith("/login?next=/&google_error=Google не підтвердив цю пошту");
        assertThat(browser.get("/api/me").status()).isEqualTo(204);
    }

    @Test
    void aReturnWithAStateTheBrowserDidNotStartIsRefused() {
        Browser browser = new Browser(port);
        browser.redirectOf("/api/auth/google");
        String code = google.code(new GoogleSignIn.Identity("sub-" + UUID.randomUUID(), "x@gmail.test", true, ""));
        String landed = browser.redirectOf("/login/oauth2/code/google?code=" + code + "&state=forged");
        assertThat(decoded(landed)).startsWith("/login?google_error=");
        assertThat(browser.get("/api/me").status()).isEqualTo(204);
    }

    @Test
    void settingsTieGoogleToTheAccountButNotOneTiedElsewhere() {
        Accounts.Person first = Accounts.signedIn(port, mailbox);
        String subject = "sub-" + UUID.randomUUID();
        GoogleSignIn.Identity identity = new GoogleSignIn.Identity(subject, "other." + subject + "@gmail.test", true, "");
        assertThat(returnFromGoogle(first.browser(), "/api/auth/google?link=true", identity)).isEqualTo("/me/settings?google=linked");
        assertThat(read(first.browser().get("/api/me")).path("google").asBoolean()).isTrue();

        Accounts.Person second = Accounts.signedIn(port, mailbox);
        String landed = returnFromGoogle(second.browser(), "/api/auth/google?link=true", identity);
        assertThat(decoded(landed)).startsWith("/me/settings?google_error=Цей обліковий запис Google уже прив’язаний");
        assertThat(new Browser(port).redirectOf("/api/auth/google?link=true")).as("only for the signed-in").isNull();
    }

    /** Leaves for «Google», signs in there as {@code identity} and returns; gives where the site then sends the browser. */
    private String returnFromGoogle(Browser browser, String start, GoogleSignIn.Identity identity) {
        String atGoogle = browser.redirectOf(start);
        assertThat(atGoogle).startsWith("https://accounts.google.test/auth?state=")
                .endsWith("redirect_uri=http://127.0.0.1:5173/login/oauth2/code/google");
        String state = atGoogle.substring(atGoogle.indexOf("state=") + 6, atGoogle.indexOf('&'));
        return browser.redirectOf("/login/oauth2/code/google?code=" + google.code(identity) + "&state=" + state);
    }

    private static String decoded(String location) {
        return URLDecoder.decode(location, StandardCharsets.UTF_8);
    }

    private static JsonNode read(Browser.Response response) {
        return JSON.readTree(response.body());
    }
}
