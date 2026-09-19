package panrid.space.novelka.core;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import panrid.space.novelka.core.model.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {
    static EmbeddedPostgres postgres;
    Store store;
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
        store = new Store(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
        novel = "n" + Math.abs(UUID.randomUUID().getLeastSignificantBits()) + "a";
        store.save(new Novel(novel, "題", "作者", "https://ncode.syosetu.com/" + novel + "/", 2, false));
        store.save(
                novel,
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
    }

    @Test
    void storesResolvesListsAndRemovesNovelAliases() throws Exception {
        store.saveAlias(novel, " Водний-Маг ");
        store.saveAlias(novel, "water");

        assertEquals(novel, store.resolveNovel("ВОДНИЙ-МАГ"));
        assertEquals(novel, store.resolveNovel(novel));
        assertEquals(2, store.aliases(novel).size());
        assertTrue(store.removeAlias("water"));
        assertFalse(store.removeAlias("water"));
        assertThrows(IllegalArgumentException.class, () -> store.resolveNovel("water"));
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
            try (var resource = Store.class.getResourceAsStream("/db/V1.sql")) {
                assertNotNull(resource);
                for (String sql : new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            statement.execute("INSERT INTO schema_versions VALUES(1)");
            try (var insert = connection.prepareStatement("INSERT INTO novels VALUES(?,?::jsonb)")) {
                insert.setString(1, novel);
                insert.setString(2, Json.write(store.novel(novel)));
                insert.executeUpdate();
            }
        }
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try (var upgraded = new Store(url, "postgres", "")) {
            assertEquals(store.novel(novel), upgraded.novel(novel));
            upgraded.saveAlias(novel, "upgraded");
            assertEquals(2, upgraded.rows("SELECT version FROM schema_versions").size());
        }
        try (var reopened = new Store(url, "postgres", "")) {
            assertEquals(novel, reopened.resolveNovel("UPGRADED"));
            assertEquals(2, reopened.rows("SELECT version FROM schema_versions").size());
        }
    }

    @Test
    void aliasConflictsDoNotRetargetExistingNames() throws Exception {
        String alias = "alias-" + UUID.randomUUID();
        String other = "novel-" + UUID.randomUUID();
        store.save(new Novel(other, "Other", "Author", "url", 1, true));
        store.saveAlias(novel, alias);
        store.saveAlias(novel, alias.toUpperCase(java.util.Locale.ROOT));
        assertEquals(1, store.aliases(novel).size());
        assertThrows(IllegalArgumentException.class, () -> store.saveAlias(other, alias));
        assertThrows(IllegalArgumentException.class, () -> store.saveAlias(novel, other));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(new Novel(alias, "New", "Author", "url", 1, true)));
        assertEquals(novel, store.resolveNovel(alias));
    }

    @Test
    void conflictingAliasRollsBackMetadataInImportTransaction() throws Exception {
        String alias = "alias-" + UUID.randomUUID();
        String other = "novel-" + UUID.randomUUID();
        store.saveAlias(novel, alias);
        assertThrows(IllegalArgumentException.class, () -> store.transaction(() -> {
            store.save(new Novel(other, "Other", "Author", "url", 1, true));
            store.saveAlias(other, alias);
            return null;
        }));
        assertTrue(store.rows("SELECT id FROM novels WHERE id=?", other).isEmpty());
        assertEquals(novel, store.resolveNovel(alias));
    }

    @Test
    void concurrentAliasRegistrationIsIdempotent() throws Exception {
        String alias = "concurrent-" + UUID.randomUUID();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.Callable<Void> registration = () -> {
                try (var other = new Store(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
                    ready.countDown();
                    if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test timed out");
                    }
                    other.saveAlias(novel, alias);
                    return null;
                }
            };
            var first = executor.submit(registration);
            var second = executor.submit(registration);
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            second.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(novel, store.resolveNovel(alias));
        assertEquals(1, store.aliases(novel).size());
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
        assertEquals("translated", store.job(w.id()).segments().getFirst().state());
        var done = p.run(store.job(w.id()), 0);
        assertEquals("complete", done.state());
        assertEquals(1, analysis.get());
        assertEquals(1, translate.get());
        assertEquals(2, edit.get());
        assertEquals(w.id(), p.create(novel, 1, false).id());
        assertEquals(2, p.create(novel, 1, true).revision());
        assertEquals(0, store.completed(novel).size());
    }

    @Test
    void storesManualFactsAndInvalidatesOnlyUsedEntries() throws Exception {
        var e =
                new Entry(
                        "アキ", "character", "アキ", "あき", "Акі", List.of(), "unknown", "", "unknown", 1, false);
        store.glossary(novel, new Glossary(1, List.of(e)));
        var p = new Pipeline(store, null, 6000);
        var w = p.create(novel, 1, false);
        store.save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
        store.start(
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
        store.glossary(novel, new Glossary(2, List.of(changed)));
        assertEquals("needs-review", store.job(w.id()).state());
        assertEquals(
                2, store.rows("SELECT revision FROM glossary_versions WHERE novel_id=?", novel).size());
    }

    @Test
    void newFactsDoNotInvalidateEarlierChapters() throws Exception {
        var w = new Pipeline(store, null, 6000).create(novel, 1, false);
        store.save(new Work(w.id(), novel, 1, w.sourceHash(), 1, w.segments(), "complete", ""));
        store.glossary(
                novel,
                new Glossary(
                        1,
                        List.of(
                                new Entry(
                                        "新", "term", "新", "", "Новий", List.of(), "unknown", "", "unknown", 1,
                                        false))));
        assertEquals("complete", store.job(w.id()).state());
    }

    @Test
    void changedSourceRequiresExplicitNewRevision() throws Exception {
        var p = new Pipeline(store, null, 6000);
        p.create(novel, 1, false);
        store.save(
                novel,
                new Chapter(1, "url", "new", List.of(new Block("title", "heading", "new")), "new html"));
        assertThrows(IllegalStateException.class, () -> p.create(novel, 1, false));
        assertEquals(2, p.create(novel, 1, true).revision());
        assertEquals(2, store.rows("SELECT data FROM chapter_versions WHERE novel_id=?", novel).size());
    }
}
