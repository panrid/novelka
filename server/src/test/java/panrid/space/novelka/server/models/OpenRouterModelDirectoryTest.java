package panrid.space.novelka.server.models;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterModelDirectoryTest {
    @Test
    void convertsPricesAndMarksModelsThePipelineCannotUse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            byte[] body = ModelFixtures.MODELS.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var models = new OpenRouterModelDirectory(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models"), http).fetch();
            assertEquals(4, models.size());
            var good = models.getFirst();
            assertTrue(good.suitable());
            assertEquals(0.15, good.inputUsdM());
            assertEquals(0.6, good.outputUsdM());
            assertEquals(128000, good.contextLength());
            assertEquals("Немає інструментів для пошуку в словнику.", models.get(1).limitation());
            assertNull(models.get(2).inputUsdM(), "negative provider price means variable, not free");
            assertEquals("Немає фіксованої ціни за токен.", models.get(2).limitation());
            assertEquals("Не працює з текстом.", models.get(3).limitation());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerErrorIsReported() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var directory = new OpenRouterModelDirectory(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models"), http);
            var error = assertThrows(IllegalStateException.class, directory::fetch);
            assertTrue(error.getMessage().contains("503"));
        } finally {
            server.stop(0);
        }
    }
}
