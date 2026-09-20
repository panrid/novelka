package panrid.space.novelka.core.integration.ai;

import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.service.translation.Pipeline;
import panrid.space.novelka.core.support.Json;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterIntegrationTest {
    static EmbeddedPostgres postgres;
    DatabaseSession store;
    JdbcSession inspection;
    HttpServer server;
    Work work;

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setErrorRedirector(ProcessBuilder.Redirect.INHERIT)
                .setOutputRedirector(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) postgres.close();
    }

    @BeforeEach
    void setup() throws Exception {
        store = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
        inspection = new JdbcSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
        String n = UUID.randomUUID().toString();
        store.novels().save(new Novel(n, "title", "author", "url", 1, true));
        store.chapters().save(n, new Chapter(1, "url", "title", List.of(new Block("title", "heading", "題")), "html"));
        work = new Pipeline(store, null, 6000).create(n, 1, false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop(0);
        store.close();
        inspection.close();
    }

    OpenRouter client() {
        return new OpenRouter(
                store.calls(),
                "test-secret",
                "openai/gpt-4o-mini",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
    }

    String response(String content) {
        return Json.write(
                Map.of(
                        "id",
                        "gen-test",
                        "provider",
                        "mock",
                        "usage",
                        Map.of("prompt_tokens", 100, "completion_tokens", 20, "cost", 0.002),
                        "choices",
                        List.of(
                                Map.of(
                                        "finish_reason",
                                        "stop",
                                        "message",
                                        Map.of("role", "assistant", "content", content)))));
    }

    @Test
    void persistsCostsAndReusesPaidResponse() throws Exception {
        var calls = new AtomicInteger();
        server.createContext(
                "/chat",
                x -> {
                    calls.incrementAndGet();
                    var bytes =
                            response("{\"entries\":[]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    x.sendResponseHeaders(200, bytes.length);
                    x.getResponseBody().write(bytes);
                    x.close();
                });
        server.start();
        var ai = client();
        var g = new Glossary(0, List.of());
        ai.generate(work, 0, "analyze", g, Map.of("source", "text"), 0);
        ai.generate(work, 0, "analyze", g, Map.of("source", "text"), 0);
        assertEquals(1, calls.get());
        var rows =
                inspection.rows(
                        "SELECT actual_usd,state,prompt_version,context FROM ai_calls WHERE job_id=?",
                        work.id());
        assertEquals(1, rows.size());
        assertEquals("complete", rows.getFirst().get("state"));
        assertEquals(0.002, store.calls().spent(work.id()), 0.000001);
    }

    @Test
    void dictionaryToolResponseIsAudited() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(
                "/chat",
                x -> {
                    String body;
                    if (calls.getAndIncrement() == 0) {
                        body =
                                Json.write(
                                        Map.of(
                                                "choices",
                                                List.of(
                                                        Map.of(
                                                                "finish_reason",
                                                                "tool_calls",
                                                                "message",
                                                                Map.of(
                                                                        "role",
                                                                        "assistant",
                                                                        "tool_calls",
                                                                        List.of(
                                                                                Map.of(
                                                                                        "id",
                                                                                        "t1",
                                                                                        "type",
                                                                                        "function",
                                                                                        "function",
                                                                                        Map.of(
                                                                                                "name",
                                                                                                "dictionary_search",
                                                                                                "arguments",
                                                                                                "{\"query\":\"Акі\"}"))))))));
                    } else {
                        var request =
                                new String(
                                        x.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        assertTrue(request.contains("Акі"));
                        body = response("{\"entries\":[]}");
                    }
                    byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    x.sendResponseHeaders(200, bytes.length);
                    x.getResponseBody().write(bytes);
                    x.close();
                });
        server.start();
        var e =
                new Entry(
                        "アキ", "character", "アキ", "", "Акі", List.of(), "unknown", "", "unknown", 1, false);
        client()
                .generate(
                        work, 0, "analyze", new Glossary(1, List.of(e)), Map.of("source", "unnamed person"), 0);
        assertEquals(2, calls.get());
        assertEquals(2, inspection.rows("SELECT id FROM ai_calls WHERE job_id=?", work.id()).size());
        assertEquals(
                1,
                inspection.rows("SELECT id FROM ai_calls WHERE job_id=? AND actual_usd IS NULL", work.id())
                        .size());
    }

    @Test
    void budgetStopsBeforeAnyRequest() throws Exception {
        server.start();
        assertThrows(
                IllegalStateException.class,
                () ->
                        client().generate(work, 0, "analyze", new Glossary(0, List.of()), Map.of(), 0.000001));
        assertTrue(inspection.rows("SELECT id FROM ai_calls WHERE job_id=?", work.id()).isEmpty());
    }

    @Test
    void serverFailureRequiresExplicitRetryAuthorization() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(
                "/chat",
                x -> {
                    calls.incrementAndGet();
                    byte[] body = "{\"error\":{\"message\":\"failed\"}}".getBytes();
                    x.sendResponseHeaders(503, body.length);
                    x.getResponseBody().write(body);
                    x.close();
                });
        server.start();
        var ai = client();
        for (int i = 0; i < 2; i++)
            assertThrows(
                    IllegalStateException.class,
                    () -> ai.generate(work, 0, "analyze", new Glossary(0, List.of()), Map.of(), 0));
        assertEquals(1, calls.get());
        assertEquals(
                "uncertain",
                inspection.rows("SELECT state FROM ai_calls WHERE job_id=?", work.id()).getFirst().get("state"));
    }
}
