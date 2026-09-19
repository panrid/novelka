package panrid.space.novelka.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import panrid.space.novelka.core.model.AiCall;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Work;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpenRouterTest {
    Store store;
    HttpServer server;
    Work work;
    List<Map<String, Object>> calls;

    @BeforeEach
    void setup() throws Exception {
        calls = new ArrayList<>();
        store = mock(Store.class);
        work = new Work("job", "novel", 1, "hash", 1, List.of(), "pending", "");
        doAnswer(
                a -> {
                    AiCall c = a.getArgument(0);
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", c.id());
                    row.put("job_id", c.jobId());
                    row.put("stage", c.stage());
                    row.put("segment", c.segment());
                    row.put("state", c.state());
                    row.put("context", Json.read(c.contextJson()));
                    row.put("estimated_usd", c.estimateUsd());
                    row.put("prompt_version", c.promptVersion());
                    calls.add(row);
                    return null;
                })
                .when(store)
                .start(any());
        when(store.spent(anyString()))
                .thenAnswer(
                        a ->
                                calls.stream()
                                        .mapToDouble(
                                                r ->
                                                        r.get("actual_usd") instanceof Number n
                                                                ? n.doubleValue()
                                                                : ((Number) r.get("estimated_usd")).doubleValue())
                                        .sum());
        when(store.rows(anyString(), any(Object[].class)))
                .thenAnswer(
                        a -> {
                            String sql = a.getArgument(0);
                            Object[] args = Arrays.copyOfRange(a.getArguments(), 1, a.getArguments().length);
                            if (sql.contains("state IN"))
                                return calls.stream()
                                        .filter(r -> List.of("pending", "uncertain").contains(r.get("state")))
                                        .toList();
                            if (sql.contains("context=?")) {
                                return calls.stream()
                                        .filter(r -> r.get("context").equals(Json.read(args[3].toString())))
                                        .reduce((x, y) -> y)
                                        .map(List::of)
                                        .orElse(List.of());
                            }
                            if (sql.contains("actual_usd IS NULL"))
                                return calls.stream()
                                        .filter(r -> !r.containsKey("actual_usd") || r.get("actual_usd") == null)
                                        .toList();
                            return calls;
                        });
        doAnswer(
                a -> {
                    String sql = a.getArgument(0);
                    Object[] args = Arrays.copyOfRange(a.getArguments(), 1, a.getArguments().length);
                    var row = calls.getLast();
                    if (sql.contains("provider=?")) {
                        row.put("state", args[0]);
                        row.put("actual_usd", args[2]);
                        row.put("response", Json.read(args[9].toString()));
                    } else if (sql.contains("state='uncertain'")) row.put("state", "uncertain");
                    return null;
                })
                .when(store)
                .exec(anyString(), any(Object[].class));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    OpenRouter client() {
        return new OpenRouter(
                store,
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
                store.rows(
                        "SELECT actual_usd,state,prompt_version,context FROM ai_calls WHERE job_id=?",
                        work.id());
        assertEquals(1, rows.size());
        assertEquals("complete", rows.getFirst().get("state"));
        assertEquals(0.002, store.spent(work.id()), 0.000001);
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
        assertEquals(2, store.rows("SELECT id FROM ai_calls WHERE job_id=?", work.id()).size());
        assertEquals(
                1,
                store
                        .rows("SELECT id FROM ai_calls WHERE job_id=? AND actual_usd IS NULL", work.id())
                        .size());
    }

    @Test
    void budgetStopsBeforeAnyRequest() throws Exception {
        server.start();
        assertThrows(
                IllegalStateException.class,
                () ->
                        client().generate(work, 0, "analyze", new Glossary(0, List.of()), Map.of(), 0.000001));
        assertTrue(store.rows("SELECT id FROM ai_calls WHERE job_id=?", work.id()).isEmpty());
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
                store.rows("SELECT state FROM ai_calls WHERE job_id=?", work.id()).getFirst().get("state"));
    }
}
