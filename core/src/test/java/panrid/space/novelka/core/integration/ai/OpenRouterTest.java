package panrid.space.novelka.core.integration.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import panrid.space.novelka.core.model.AiCall;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.repository.AiCallRepository;
import panrid.space.novelka.core.support.Json;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpenRouterTest {
    AiCallRepository store;
    HttpServer server;
    Work work;
    List<Map<String, Object>> calls;

    @BeforeEach
    void setup() throws Exception {
        calls = new ArrayList<>();
        store = mock(AiCallRepository.class);
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
        when(store.hasUncertain(anyString(), anyString(), anyInt()))
                .thenAnswer(a -> calls.stream().anyMatch(row -> List.of("pending", "uncertain").contains(row.get("state"))));
        when(store.previous(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(a -> calls.stream()
                        .filter(row -> row.get("context").equals(Json.read(a.getArgument(3))))
                        .reduce((first, last) -> last).map(List::of).orElse(List.of()));
        doAnswer(a -> {
            var row = calls.stream().filter(value -> value.get("id").equals(a.getArgument(0))).findFirst().orElseThrow();
            com.fasterxml.jackson.databind.JsonNode body = a.getArgument(3);
            row.put("state", a.getArgument(1));
            row.put("actual_usd", body.path("usage").path("cost").isNumber()
                    ? body.path("usage").path("cost").doubleValue() : null);
            row.put("response", body);
            return null;
        }).when(store).finish(anyString(), anyString(), anyLong(), any());
        doAnswer(a -> {
            calls.stream().filter(row -> row.get("id").equals(a.getArgument(0)))
                    .forEach(row -> row.put("state", "uncertain"));
            return null;
        }).when(store).uncertain(anyString(), anyLong());
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
        var rows = this.calls;
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
        assertEquals(2, this.calls.size());
        assertEquals(
                1,
                this.calls.stream().filter(row -> row.get("actual_usd") == null).count());
    }

    @Test
    void budgetStopsBeforeAnyRequest() throws Exception {
        server.start();
        assertThrows(
                IllegalStateException.class,
                () ->
                        client().generate(work, 0, "analyze", new Glossary(0, List.of()), Map.of(), 0.000001));
        assertTrue(this.calls.isEmpty());
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
                this.calls.getFirst().get("state"));
    }
}
