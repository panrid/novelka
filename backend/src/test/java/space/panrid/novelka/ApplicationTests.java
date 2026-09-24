package space.panrid.novelka;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import space.panrid.novelka.jooq.Tables;
import space.panrid.novelka.support.IntegrationTest;

@IntegrationTest
@AutoConfigureTestRestTemplate
class ApplicationTests {

    @Autowired
    TestRestTemplate http;

    @Autowired
    DSLContext db;

    @Test
    void migrationsAreAppliedAndJooqSeesTheSchema() {
        assertThat(db.fetchCount(Tables.EVENT_PUBLICATION)).isZero();
    }

    @Test
    void healthReportsOk() {
        ResponseEntity<String> response = http.getForEntity("/api/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void pagePathsServeTheWebApp() {
        ResponseEntity<String> response = http.getForEntity("/n/mag-vody/12", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.TEXT_HTML));
        assertThat(response.getBody()).contains("test shell");
    }

    @Test
    void unknownApiPathsAreNotSwallowedByTheWebApp() {
        ResponseEntity<String> response = http.getForEntity("/api/nothing-here", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Такої сторінки немає.").doesNotContain("test shell");
    }

    @Test
    void responsesCarrySecurityHeaders() {
        ResponseEntity<String> response = http.getForEntity("/", String.class);

        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
