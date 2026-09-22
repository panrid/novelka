package panrid.space.novelka.server.settings;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterCreditsServiceTest {
    @Test
    void readsAccountCreditsWithManagementKey() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/credits", exchange -> {
            assertEquals("Bearer test-management-key", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":{\"total_credits\":100.5,\"total_usage\":25.75}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var service = new OpenRouterCreditsService("test-management-key",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/credits"), http);
            var credits = service.read();
            assertTrue(credits.configured());
            assertEquals(100.5, credits.totalCredits());
            assertEquals(25.75, credits.totalUsage());
            assertEquals(74.75, credits.remainingUsd());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingManagementKeyDoesNotCallProvider() {
        var service = new OpenRouterCreditsService("", URI.create("http://127.0.0.1:1/credits"), HttpClient.newHttpClient());
        var credits = service.read();
        assertFalse(credits.configured());
        assertNull(credits.remainingUsd());
    }

    @Test
    void rejectsUnexpectedProviderResponseWithoutReturningItsBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/credits", exchange -> {
            byte[] body = "secret-error-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var service = new OpenRouterCreditsService("test-management-key",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/credits"), http);
            var error = assertThrows(ResponseStatusException.class, service::read);
            assertTrue(error.getReason().contains("management key"));
            assertFalse(error.getReason().contains("secret-error-body"));
        } finally {
            server.stop(0);
        }
    }
}
