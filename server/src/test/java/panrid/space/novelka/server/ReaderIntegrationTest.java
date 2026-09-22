package panrid.space.novelka.server;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.model.Segment;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.service.translation.Pipeline;
import panrid.space.novelka.core.support.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReaderIntegrationTest {
    static EmbeddedPostgres postgres;
    static ConfigurableApplicationContext application;
    static String base;
    static HttpClient client;

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setErrorRedirector(ProcessBuilder.Redirect.INHERIT)
                .setOutputRedirector(ProcessBuilder.Redirect.INHERIT).start();
        var spring = new SpringApplication(NovelkaServer.class);
        spring.setDefaultProperties(Map.of(
                "server.port", "0",
                "novelka.database.url", postgres.getJdbcUrl("postgres", "postgres"),
                "novelka.database.user", "postgres",
                "novelka.database.password", "",
                "spring.main.banner-mode", "off"));
        // Command-line properties take precedence over application.properties defaults.
        application = spring.run("--server.port=0",
                "--novelka.database.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                "--novelka.database.user=postgres", "--novelka.database.password=", "--novelka.worker.enabled=false",
                "--novelka.owner.username=", "--novelka.owner.password=");
        base = "http://127.0.0.1:" + application.getEnvironment().getProperty("local.server.port") + "/api/novels";
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() throws Exception {
        if (client != null) client.close();
        if (application != null) application.close();
        if (postgres != null) postgres.close();
    }

    @Test
    void anonymousVisitorCanOpenWelcomePageAndAssetsButNotManagement() throws Exception {
        String origin = base.substring(0, base.length() - "/api/novels".length());
        for (String path : List.of("/", "/index.html")) {
            var page = client.send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode(), "Anonymous GET " + path);
            assertTrue(page.body().contains("Novelka public shell"));
            assertTrue(page.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        }
        var asset = client.send(HttpRequest.newBuilder(URI.create(origin + "/assets/public-shell.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, asset.statusCode());
        assertTrue(asset.body().contains("public-shell"));
        for (String path : List.of("/api/tasks", "/api/accounts", "/api/settings")) {
            var restricted = client.send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, restricted.statusCode(), "Anonymous GET " + path);
        }
    }

    @Test
    void servesCatalogAliasContentsAndOnlyTranslatedBlocks() throws Exception {
        String id = seed("complete");
        try (var database = database()) {
            database.novels().saveAlias(id, "alias-" + id);
        }
        var catalog = Json.read(get("").body());
        var card = java.util.stream.StreamSupport.stream(catalog.spliterator(), false)
                .filter(node -> node.path("id").asText().equals(id)).findFirst().orElseThrow();
        assertEquals(1, card.path("readyChapters").asInt());
        assertEquals("alias-" + id, card.path("aliases").get(0).asText());
        var detail = get("/alias-" + id);
        assertEquals(200, detail.statusCode());
        assertEquals("Пролог", Json.read(detail.body()).path("chapters").get(0).path("title").asText());
        var chapter = get("/" + id + "/chapters/1");
        assertEquals(200, chapter.statusCode());
        var body = Json.read(chapter.body());
        assertEquals("Пролог", body.path("title").asText());
        assertEquals("<script>alert('text')</script>", body.path("blocks").get(1).path("text").asText());
        assertFalse(body.has("sourceHash"));
        assertFalse(body.has("segments"));
        assertFalse(chapter.body().contains("原文"));
        assertEquals(404, get("/" + id + "/chapters/2").statusCode());
    }

    @Test
    void latestPendingRevisionKeepsLastPublishedTranslationAvailable() throws Exception {
        String id = seed("complete");
        try (var database = database()) {
            new Pipeline(database, null, 6000).create(id, 1, true);
        }
        assertEquals(200, get("/" + id + "/chapters/1").statusCode());
        assertEquals(1, Json.read(get("/" + id).body()).path("chapters").size());
    }

    @Test
    void sourceChangesHideTranslationsButDictionaryReviewKeepsThemAvailable() throws Exception {
        String stale = seed("complete");
        try (var database = database()) {
            database.chapters().save(stale, new Chapter(1, "url", "changed",
                    List.of(new Block("title", "heading", "changed")), ""));
        }
        assertEquals(404, get("/" + stale + "/chapters/1").statusCode());
        String review = seed("needs-review");
        assertEquals(200, get("/" + review + "/chapters/1").statusCode());
    }

    @Test
    void rejectsMissingAndMalformedRequestsWithoutInternalDetails() throws Exception {
        assertEquals(404, get("/missing").statusCode());
        assertEquals(400, get("/missing/chapters/0").statusCode());
        var invalid = get("/missing/chapters/nope");
        assertEquals(400, invalid.statusCode());
        assertFalse(invalid.body().contains("stackTrace"));
        assertFalse(invalid.body().contains("jdbc:"));
        var post = client.send(HttpRequest.newBuilder(URI.create(base))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, post.statusCode());
    }

    private static DatabaseSession database() throws Exception {
        return new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
    }

    private String seed(String state) throws Exception {
        String id = "test-" + UUID.randomUUID();
        try (var database = database()) {
            database.novels().save(new Novel(id, "物語", "作者", "url", 2, false));
            var original = List.of(new Block("title", "heading", "原文"), new Block("p1", "paragraph", "原文"));
            database.chapters().save(id, new Chapter(1, "url", "原文", original, ""));
            var job = new Pipeline(database, null, 6000).create(id, 1, false);
            var translated = List.of(new Block("title", "heading", "Пролог"),
                    new Block("p1", "paragraph", "<script>alert('text')</script>"));
            database.jobs().save(new Work(job.id(), id, 1, job.sourceHash(), 1,
                    List.of(new Segment(original, List.of(), translated, "", "complete")), state, ""));
        }
        return id;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
