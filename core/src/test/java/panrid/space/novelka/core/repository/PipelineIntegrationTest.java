package panrid.space.novelka.core.repository;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import panrid.space.novelka.core.integration.ai.AiClient;
import panrid.space.novelka.core.model.AiCall;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {
    static EmbeddedPostgres postgres;
    DatabaseSession store;
    JdbcSession inspection;
    String novel;

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
        novel = "n" + Math.abs(UUID.randomUUID().getLeastSignificantBits()) + "a";
        store.novels().save(new Novel(novel, "題", "作者", "https://ncode.syosetu.com/" + novel + "/", 2, false));
        store.chapters().save(novel,
                new Chapter(
                        1,
                        "url",
                        "題",
                        List.of(new Block("title", "heading", "題"), new Block("p1", "paragraph", "彼の名前はアキです。")),
                        "html"));
    }

    @AfterEach
    void close() throws Exception {
        store.close();
        inspection.close();
    }

    @Test
    void rollsBackChangesAcrossRepositoriesAndNestedServices() throws Exception {
        String alias = "rollback-" + UUID.randomUUID();
        var original = store.chapters().chapter(novel, 1);
        assertThrows(IllegalStateException.class, () -> store.transaction(() -> {
            store.novels().saveAlias(novel, alias);
            store.chapters().save(novel, new Chapter(1, "url", "changed",
                    List.of(new Block("title", "heading", "changed")), "html"));
            new Pipeline(store, null, 6000).create(novel, 1, false);
            store.glossaryService().update(novel, new Glossary(1, List.of()));
            throw new IllegalStateException("Abort the entire operation");
        }));

        assertTrue(store.novels().aliases(novel).isEmpty());
        assertEquals(original, store.chapters().chapter(novel, 1));
        assertNull(store.jobs().latest(novel, 1));
        assertEquals(0, store.glossaries().glossary(novel).revision());
        assertTrue(inspection.rows("SELECT revision FROM glossary_versions WHERE novel_id=?", novel).isEmpty());
        assertEquals(1, inspection.rows("SELECT data FROM chapter_versions WHERE novel_id=?", novel).size());
        // The same connection remains usable after the rollback.
        store.novels().saveAlias(novel, alias);
        assertEquals(novel, store.novels().resolveNovel(alias));
    }

    @Test
    void reportsCatalogStatusAndUnknownCostsWithoutMixingNovels() throws Exception {
        String alias = "report-" + UUID.randomUUID();
        store.novels().saveAlias(novel, alias);
        var catalog = store.novels().list().stream()
                .filter(row -> novel.equals(row.get("id"))).findFirst().orElseThrow();
        assertEquals("題", catalog.get("title"));
        assertEquals(alias, Json.read(catalog.get("aliases").toString()).get(0).asText());
        assertTrue(store.chapters().exists(novel, 1));
        assertFalse(store.chapters().exists(novel, 2));
        assertEquals(1, store.chapters().list(novel).size());

        var work = new Pipeline(store, null, 6000).create(novel, 1, false);
        assertEquals(work.id(), store.jobs().status(novel).getFirst().get("id"));
        String call = "report-call-" + UUID.randomUUID();
        store.calls().start(new AiCall(call, work.id(), "translate", 0, "test", "v1", 0,
                "{}", 0.012, "pending"));
        var summary = store.calls().costs(novel, false).getFirst();
        assertEquals(1, ((Number) summary.get("unknown_cost_calls")).intValue());
        assertNull(summary.get("known_actual_usd"));
        assertEquals(0.012, ((Number) summary.get("estimated_usd")).doubleValue(), 0.000001);
        assertEquals(call, store.calls().costs(novel, true).getFirst().get("id"));
        assertTrue(store.calls().costs("missing", false).isEmpty());
        assertTrue(store.calls().costs(null, true).stream().anyMatch(row -> call.equals(row.get("id"))));
        assertTrue(store.calls().costs(null, false).stream().anyMatch(row -> novel.equals(row.get("novel_id"))));
    }

    @Test
    void storesResolvesListsAndRemovesNovelAliases() throws Exception {
        store.novels().saveAlias(novel, " Водний-Маг ");
        store.novels().saveAlias(novel, "water");

        assertEquals(novel, store.novels().resolveNovel("ВОДНИЙ-МАГ"));
        assertEquals(novel, store.novels().resolveNovel(novel));
        assertEquals(2, store.novels().aliases(novel).size());
        assertTrue(store.novels().removeAlias("water"));
        assertFalse(store.novels().removeAlias("water"));
        assertThrows(IllegalArgumentException.class, () -> store.novels().resolveNovel("water"));
    }

    @Test
    void upgradesV1WithoutLosingMetadataAndDoesNotReapplyV2() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        String baseUrl = postgres.getJdbcUrl("postgres", "postgres");
        try (var connection = java.sql.DriverManager.getConnection(baseUrl, "postgres", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema);
            statement.execute("CREATE TABLE schema_versions(version integer PRIMARY KEY)");
            try (var resource = DatabaseSession.class.getResourceAsStream("/db/V1.sql")) {
                assertNotNull(resource);
                for (String sql : new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            statement.execute("INSERT INTO schema_versions VALUES(1)");
            try (var insert = connection.prepareStatement("INSERT INTO novels VALUES(?,?::jsonb)")) {
                insert.setString(1, novel);
                insert.setString(2, Json.write(store.novels().novel(novel)));
                insert.executeUpdate();
            }
        }
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try (var upgraded = new DatabaseSession(url, "postgres", "");
                var schemaInspection = new JdbcSession(url, "postgres", "")) {
            assertEquals(store.novels().novel(novel), upgraded.novels().novel(novel));
            upgraded.novels().saveAlias(novel, "upgraded");
            assertEquals(19, schemaInspection.rows("SELECT version FROM schema_versions").size());
        }
        try (var reopened = new DatabaseSession(url, "postgres", "");
                var schemaInspection = new JdbcSession(url, "postgres", "")) {
            assertEquals(novel, reopened.novels().resolveNovel("UPGRADED"));
            assertEquals(19, schemaInspection.rows("SELECT version FROM schema_versions").size());
        }
    }

    @Test
    void aliasConflictsDoNotRetargetExistingNames() throws Exception {
        String alias = "alias-" + UUID.randomUUID();
        String other = "novel-" + UUID.randomUUID();
        store.novels().save(new Novel(other, "Other", "Author", "url", 1, true));
        store.novels().saveAlias(novel, alias);
        store.novels().saveAlias(novel, alias.toUpperCase(java.util.Locale.ROOT));
        assertEquals(1, store.novels().aliases(novel).size());
        assertThrows(IllegalArgumentException.class, () -> store.novels().saveAlias(other, alias));
        assertThrows(IllegalArgumentException.class, () -> store.novels().saveAlias(novel, other));
        assertThrows(IllegalArgumentException.class,
                () -> store.novels().save(new Novel(alias, "New", "Author", "url", 1, true)));
        assertEquals(novel, store.novels().resolveNovel(alias));
    }

    @Test
    void conflictingAliasRollsBackMetadataInImportTransaction() throws Exception {
        String alias = "alias-" + UUID.randomUUID();
        String other = "novel-" + UUID.randomUUID();
        store.novels().saveAlias(novel, alias);
        assertThrows(IllegalArgumentException.class, () -> store.transaction(() -> {
            store.novels().save(new Novel(other, "Other", "Author", "url", 1, true));
            store.novels().saveAlias(other, alias);
            return null;
        }));
        assertTrue(inspection.rows("SELECT id FROM novels WHERE id=?", other).isEmpty());
        assertEquals(novel, store.novels().resolveNovel(alias));
    }

    @Test
    void concurrentAliasRegistrationIsIdempotent() throws Exception {
        String alias = "concurrent-" + UUID.randomUUID();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.Callable<Void> registration = () -> {
                try (var other = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
                    ready.countDown();
                    if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test timed out");
                    }
                    other.novels().saveAlias(novel, alias);
                    return null;
                }
            };
            var first = executor.submit(registration);
            var second = executor.submit(registration);
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            second.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(novel, store.novels().resolveNovel(alias));
        assertEquals(1, store.novels().aliases(novel).size());
    }

    @Test
    void resumesFromSavedDraftAndKeepsRevisions() throws Exception {
        AtomicInteger analysis = new AtomicInteger(),
                translate = new AtomicInteger(),
                edit = new AtomicInteger();
        AiClient ai =
                (w, i, stage, g, p, budget) -> {
                    if (stage.equals("analyze")) {
                        analysis.incrementAndGet();
                        return Json.read("{\"entries\":[]}");
                    }
                    if (stage.equals("translate")) translate.incrementAndGet();
                    if (stage.equals("proofread") && edit.incrementAndGet() == 1)
                        throw new IllegalStateException("interrupted");
                    return Json.read(
                            "{\"blocks\":[{\"id\":\"title\",\"kind\":\"heading\",\"text\":\"Заголовок\"},{\"id\":\"p1\",\"kind\":\"paragraph\",\"text\":\"Його"
                                    + " звати Акі.\"}],\"summary\":\"Акі представився.\"}");
                };
        var p = new Pipeline(store, ai, 6000);
        var w = p.create(novel, 1, false);
        assertThrows(IllegalStateException.class, () -> p.run(w, 0));
        assertEquals("translated", store.jobs().job(w.id()).segments().getFirst().state());
        var done = p.run(store.jobs().job(w.id()), 0);
        assertEquals("complete", done.state());
        assertEquals(1, analysis.get());
        assertEquals(1, translate.get());
        assertEquals(2, edit.get());
        assertEquals(w.id(), p.create(novel, 1, false).id());
        assertEquals(2, p.create(novel, 1, true).revision());
        assertEquals(1, store.jobs().completed(novel).size());
    }

    @Test
    void storesManualFactsAndInvalidatesOnlyUsedEntries() throws Exception {
        var e =
                new Entry(
                        "アキ", "character", "アキ", "あき", "Акі", List.of(), "unknown", "", "unknown", 1, false);
        store.glossaryService().update(novel, new Glossary(1, List.of(e)));
        var p = new Pipeline(store, null, 6000);
        var w = p.create(novel, 1, false);
        store.jobs().save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
        store.calls().start(
                new AiCall(
                        "call" + novel,
                        w.id(),
                        "translate",
                        0,
                        "test",
                        "v1",
                        1,
                        Json.write(
                                Map.of(
                                        "messages",
                                        List.of(Map.of("content", Json.write(Map.of("dictionary", List.of(e))))))),
                        0.001,
                        "pending"));
        var changed =
                new Entry(
                        "アキ",
                        "character",
                        "アキ",
                        "あき",
                        "Акі",
                        List.of(),
                        "female",
                        "explicit evidence",
                        "confirmed",
                        1,
                        true);
        store.glossaryService().update(novel, new Glossary(2, List.of(changed)));
        assertEquals("needs-review", store.jobs().job(w.id()).state());
        var error = assertThrows(IllegalStateException.class, () -> p.run(store.jobs().job(w.id()), 0));
        assertTrue(error.getMessage().contains("proofread " + novel + " --chapter 1"));
        assertEquals(
                2, inspection.rows("SELECT revision FROM glossary_versions WHERE novel_id=?", novel).size());
    }

    @Test
    void newFactsDoNotInvalidateEarlierChapters() throws Exception {
        var w = new Pipeline(store, null, 6000).create(novel, 1, false);
        store.jobs().save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
        store.glossaryService().update(
                novel,
                new Glossary(
                        1,
                        List.of(
                                new Entry(
                                        "新", "term", "新", "", "Новий", List.of(), "unknown", "", "unknown", 1,
                                        false))));
        assertEquals("complete", store.jobs().job(w.id()).state());
    }

    @Test
    void changedSourceRequiresExplicitNewRevision() throws Exception {
        var p = new Pipeline(store, null, 6000);
        p.create(novel, 1, false);
        store.chapters().save(novel,
                new Chapter(1, "url", "new", List.of(new Block("title", "heading", "new")), "new html"));
        assertThrows(IllegalStateException.class, () -> p.create(novel, 1, false));
        assertEquals(2, p.create(novel, 1, true).revision());
        assertEquals(2, inspection.rows("SELECT data FROM chapter_versions WHERE novel_id=?", novel).size());
    }
}
