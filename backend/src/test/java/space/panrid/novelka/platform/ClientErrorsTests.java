package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.IntegrationTest;

/**
 * План, етап 23, п. 4: errors in readers' browsers reach the server's log without personal data,
 * and a browser that sends too much is cut off quietly.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ClientErrorsTests {

    @LocalServerPort
    int port;

    @Test
    void aBrowsersErrorIsLoggedWithoutTheQuery(CapturedOutput output) {
        Browser guest = new Browser(port);
        String report = """
                {"kind":"render","message":"TypeError: x is undefined","stack":"at Reader (index-a1b2.js:1:2)",
                 "url":"/reset-password?token=SECRET-TOKEN-42#top","version":"index-a1b2.js"}""";
        assertThat(guest.postWithoutCsrf("/api/client-errors", report).status()).as("no CSRF token from a failing page").isEqualTo(204);
        assertThat(output.getAll()).contains("Browser render: TypeError: x is undefined | page /reset-password | build index-a1b2.js")
                .contains("at Reader (index-a1b2.js:1:2)").doesNotContain("SECRET-TOKEN-42");

        for (int i = 0; i < 25; i++) {
            assertThat(guest.postWithoutCsrf("/api/client-errors", """
                    {"kind":"error","message":"Flood %d","url":"/"}""".formatted(i)).status()).isEqualTo(204);
        }
        assertThat(output.getAll()).contains("Flood 0").doesNotContain("Flood 24");
    }
}
