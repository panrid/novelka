package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.AI_CALL;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;

/**
 * План, етап 23, п. 5: Prometheus reads the app's metrics on a port of its own, which Caddy never
 * forwards; the site's own port does not give them away.
 */
@IntegrationTest
class MonitoringTests {

    @LocalServerPort
    int port;

    @Value("${local.management.port}")
    int managementPort;

    @Autowired
    DSLContext db;

    @Test
    void metricsAreOnTheirOwnPortOnly() {
        new Browser(port).get("/api/health");

        Response metrics = scrape();
        assertThat(metrics.status()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("http_server_requests_seconds_count", "http_server_requests_seconds_bucket", "jvm_memory_used_bytes",
                        "hikaricp_connections_active")
                .contains("novelka_autotranslate_jobs{state=\"queued\"}", "novelka_autotranslate_steps{state=\"ready\"}",
                        "novelka_autotranslate_chapters_done_last_hour", "novelka_autotranslate_idle_seconds",
                        "novelka_ai_spent_today_usd", "novelka_ai_calls_last_hour{state=\"failed\"}",
                        "novelka_mail_sent_total{result=\"sent\"}");

        Response site = new Browser(port).get("/actuator/prometheus");
        assertThat(site.body()).doesNotContain("jvm_memory_used_bytes", "novelka_ai_spent_today_usd");
    }

    @Test
    void todaysSpendOnModelsCountsPaidAndPossiblyPaidCalls() {
        double before = value(scrape().body(), "novelka_ai_spent_today_usd");
        call("complete", 1_500_000L, 9_000_000L);
        call("uncertain", null, 250_000L);
        call("failed", 0L, 7_000_000L);

        double after = value(scrape().body(), "novelka_ai_spent_today_usd");
        assertThat(after - before).as("1.5 $ paid + 0.25 $ maybe paid; a failed call costs nothing").isBetween(1.749, 1.751);
    }

    /** As Prometheus asks: the text format first. */
    private Response scrape() {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/prometheus"))
                            .header("Accept", "text/plain;version=0.0.4;q=1,*/*;q=0.1").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private void call(String state, Long actualMicroUsd, long estimatedMicroUsd) {
        db.insertInto(AI_CALL)
                .set(AI_CALL.STAGE, "monitoring-test")
                .set(AI_CALL.MODEL, "test/model")
                .set(AI_CALL.REQUEST_HASH, UUID.randomUUID().toString())
                .set(AI_CALL.REQUEST, JSONB.valueOf("{}"))
                .set(AI_CALL.STATE, state)
                .set(AI_CALL.COST_ESTIMATED_MUSD, estimatedMicroUsd)
                .set(AI_CALL.COST_ACTUAL_MUSD, actualMicroUsd)
                .execute();
    }

    private static double value(String metrics, String name) {
        Matcher line = Pattern.compile("(?m)^" + name + "(?:\\{[^}]*\\})? (\\S+)$").matcher(metrics);
        assertThat(line.find()).as(name + " in\n" + metrics).isTrue();
        return Double.parseDouble(line.group(1));
    }
}
