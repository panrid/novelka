package panrid.space.novelka.server;

import com.fasterxml.jackson.databind.JsonNode;
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
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.service.translation.Pipeline;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.TaskRepository;
import panrid.space.novelka.server.task.TaskWorker;
import panrid.space.novelka.server.task.TaskRequest;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccessIntegrationTest {
    static EmbeddedPostgres postgres;
    static ConfigurableApplicationContext application;
    static String base;
    static HttpClient owner;
    static final String PASSWORD = "test-password-only-1234";
    static com.sun.net.httpserver.HttpServer models;
    static volatile boolean modelsFail;

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().setErrorRedirector(ProcessBuilder.Redirect.INHERIT)
                .setOutputRedirector(ProcessBuilder.Redirect.INHERIT).start();
        // Stand-in for the public OpenRouter model list, so tests never call the network.
        models = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        models.createContext("/models", exchange -> {
            if (modelsFail) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
            byte[] response = panrid.space.novelka.server.models.ModelFixtures.MODELS.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        models.start();
        application = new SpringApplication(NovelkaServer.class).run("--server.port=0",
                "--novelka.database.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                "--novelka.database.user=postgres", "--novelka.database.password=",
                "--novelka.owner.username=owner", "--novelka.owner.password=" + PASSWORD, "--novelka.owner.email=Owner@Example.test",
                "--novelka.worker.enabled=false", "--server.servlet.session.cookie.secure=false",
                "--novelka.openrouter.models-url=http://127.0.0.1:" + models.getAddress().getPort() + "/models");
        base = "http://127.0.0.1:" + application.getEnvironment().getProperty("local.server.port") + "/api";
        owner = browser();
        login(owner, "owner");
    }

    @AfterAll
    static void stop() throws Exception {
        if (owner != null) owner.close();
        if (application != null) application.close();
        if (postgres != null) postgres.close();
        if (models != null) models.stop(0);
    }

    @org.junit.jupiter.api.BeforeEach
    void isolateRateLimitWindow() throws Exception {
        // Each scenario represents independent clients, despite sharing one loopback address.
        var security = application.getBean(org.springframework.security.web.FilterChainProxy.class);
        for (var chain : security.getFilterChains()) {
            for (var filter : chain.getFilters()) {
                if (filter instanceof panrid.space.novelka.server.security.LoginRateLimitFilter) {
                    var attempts = filter.getClass().getDeclaredField("attempts");
                    attempts.setAccessible(true);
                    ((Map<?, ?>) attempts.get(filter)).clear();
                }
            }
        }
    }

    @Test
    void notificationCursorDoesNotShiftWhenNewEventsArrive() throws Exception {
        try (var reader = registered(); var sql = jdbc()) {
            String tag = UUID.randomUUID().toString();
            for (int number = 1; number <= 32; number++)
                sql.exec("INSERT INTO notifications(event_key,kind,audience,chapter) VALUES(?,'chapter_published','READER',?)", tag + number, number);
            var first = body(get(reader, "/notifications"));
            assertEquals(30, first.path("items").size());
            long cursor = first.path("nextCursor").asLong();
            assertTrue(cursor > 0);
            sql.exec("INSERT INTO notifications(event_key,kind,audience,chapter) VALUES(?,'chapter_published','READER',33)", tag + "new");
            var second = body(get(reader, "/notifications?before=" + cursor));
            assertEquals(2, second.path("items").size());
            assertEquals(0, second.path("nextCursor").asLong());
            assertEquals(cursor - 1, second.path("items").get(0).path("id").asLong());
            assertEquals(400, get(reader, "/notifications?before=-1").statusCode());
        }
    }

    @Test
    void glossaryEditingRejectsDuplicatesAndBusyNovelButCanMergeExistingEntries() throws Exception {
        String novel = seed();
        var first = new panrid.space.novelka.core.model.Entry("ryo", "character", "涼", "りょう", "Рьо", List.of(), "male", "", "confirmed", 1, true);
        var second = new panrid.space.novelka.core.model.Entry("ryo-alt", "character", "良", "りょう", "Рьо", List.of(), "unknown", "", "assumed", 1, true);
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            db.glossaryService().update(novel, new panrid.space.novelka.core.model.Glossary(1, List.of(first, second)));
            db.glossaries().propose(novel, "job", second);
        }
        var entries = body(get(owner, "/manage/" + novel + "/glossary/entries?size=1&sort=japanese&direction=asc&kind=character"));
        assertEquals(2, entries.path("total").asInt());
        assertEquals(1, entries.path("items").size());
        var next = body(get(owner, "/manage/" + novel + "/glossary/entries?page=2&size=1&sort=japanese&direction=asc"));
        assertEquals(1, next.path("items").size());
        assertNotEquals(entries.path("items").get(0).path("key").asText(), next.path("items").get(0).path("key").asText());
        String name = java.net.URLEncoder.encode("Рьо", java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(2, body(get(owner, "/manage/" + novel + "/glossary/entries?q=" + name)).path("total").asInt());
        assertEquals(400, get(owner, "/manage/" + novel + "/glossary/entries?kind=bad").statusCode());
        var proposed = body(get(owner, "/manage/" + novel + "/proposals")).path("items");
        assertEquals("in_dictionary", proposed.get(0).path("status").asText());
        var third = new panrid.space.novelka.core.model.Entry("another", "character", "亮", "りょう", "Рьо", List.of(), "unknown", "", "assumed", 1, true);
        assertEquals(409, post(owner, "/manage/" + novel + "/glossary", Map.of("revision", 1, "entries", List.of(third))).statusCode());
        try (var sql = jdbc(); var lock = sql.lock(novel)) {
            assertEquals(409, post(owner, "/manage/" + novel + "/glossary", Map.of("revision", 1, "entries", List.of(first))).statusCode());
        }
        assertEquals(200, post(owner, "/manage/" + novel + "/glossary/merge",
                Map.of("revision", 1, "keepKey", "ryo", "removeKey", "ryo-alt")).statusCode());
        var glossary = body(get(owner, "/manage/" + novel)).path("glossary");
        assertEquals(2, glossary.path("revision").asInt());
        var merged = body(get(owner, "/manage/" + novel + "/glossary/entries"));
        assertEquals(1, merged.path("total").asInt());
        assertEquals("ryo", merged.path("items").get(0).path("key").asText());
        assertTrue(merged.path("items").get(0).path("aliases").toString().contains("良"));
    }

    @Test
    void pageableListsCombineSearchFiltersSortingAndBoundaries() throws Exception {
        String first = seed();
        String second = seed();
        var catalog = get(owner, "/novels/search?q=" + first + "&page=1&size=1&sort=id&direction=asc");
        assertEquals(200, catalog.statusCode(), catalog.body());
        assertEquals(1, body(catalog).path("items").size());
        assertEquals(first, body(catalog).path("items").get(0).path("id").asText());
        assertEquals(1, body(catalog).path("total").asInt());
        assertEquals(0, body(get(owner, "/novels/search?q=absent-" + first)).path("items").size());
        assertEquals(400, get(owner, "/novels/search?size=101").statusCode());
        assertEquals(400, get(owner, "/novels/search?sort=data%3BDROP").statusCode());

        var accounts = get(owner, "/accounts?q=owner&role=OWNER&sort=username&direction=asc&page=1&size=1");
        assertEquals(200, accounts.statusCode(), accounts.body());
        assertEquals(1, body(accounts).path("total").asInt());
        assertEquals("owner", body(accounts).path("items").get(0).path("username").asText());
        assertEquals(0, body(get(owner, "/accounts?q=owner&role=READER")).path("total").asInt());
        var byEmail = body(get(owner, "/accounts?q=owner%40example.test&sort=email&direction=asc"));
        assertEquals(1, byEmail.path("total").asInt());
        assertEquals("owner@example.test", byEmail.path("items").get(0).path("email").asText());
        String ownerId = byEmail.path("items").get(0).path("id").asText();
        assertEquals("owner@example.test", body(get(owner, "/accounts/" + ownerId)).path("email").asText());
        assertEquals(404, get(owner, "/accounts/" + UUID.randomUUID()).statusCode());
        try (var reader = registered()) { assertEquals(403, get(reader, "/accounts/" + ownerId).statusCode()); }
        assertEquals(400, get(owner, "/accounts?direction=sideways").statusCode());

        try (var reader = registered(); var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            String job = db.jobs().latest(first, 1).id();
            propose(reader, first, job, 1, "Він ішов.", "Він крокував.");
            String search = java.net.URLEncoder.encode("крокував", java.nio.charset.StandardCharsets.UTF_8);
            var corrections = get(reader, "/corrections?q=" + search + "&state=pending&novel=" + first
                    + "&sort=chapter&direction=asc&page=1&size=1");
            assertEquals(200, corrections.statusCode(), corrections.body());
            assertEquals(1, body(corrections).path("total").asInt());
            assertEquals(0, body(get(reader, "/corrections?q=" + search + "&state=rejected")).path("total").asInt());
            assertEquals(400, get(reader, "/corrections?state=invalid").statusCode());
        }

        String one = body(post(owner, "/tasks", task(first, UUID.randomUUID().toString(), .1))).path("id").asText();
        String two = body(post(owner, "/tasks", task(second, UUID.randomUUID().toString(), .1))).path("id").asText();
        var tasks = get(owner, "/tasks?q=" + first + "&state=queued&operation=translate&sort=created&direction=desc&page=1&size=1");
        assertEquals(200, tasks.statusCode(), tasks.body());
        assertEquals(1, body(tasks).path("total").asInt());
        assertEquals(one, body(tasks).path("items").get(0).path("id").asText());
        assertEquals(0, body(get(owner, "/tasks?novel=" + second + "&q=" + first)).path("total").asInt());
        assertEquals(400, get(owner, "/tasks?state=invalid").statusCode());

        var jobs = get(owner, "/manage/" + first + "/jobs?state=complete&sort=chapter&direction=asc&page=1&size=1");
        assertEquals(200, jobs.statusCode(), jobs.body());
        assertEquals(1, body(jobs).path("total").asInt());
        assertEquals(1, body(jobs).path("items").get(0).path("chapter").asInt());
        assertEquals(0, body(get(owner, "/manage/" + first + "/jobs?q=absent")).path("total").asInt());

        var audit = get(owner, "/accounts/audit?q=" + two + "&sort=created&page=1&size=1");
        assertEquals(200, audit.statusCode(), audit.body());
        assertTrue(body(audit).path("total").asInt() >= 1);
        try (var sql = jdbc(); var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            String job = db.jobs().latest(first, 1).id();
            var calls = new panrid.space.novelka.core.repository.AiCallRepository(sql);
            String known = UUID.randomUUID().toString();
            calls.start(new panrid.space.novelka.core.model.AiCall(known, job, "translate", 0, "test", "v1", 0, "{}", .02, "complete"));
            calls.start(new panrid.space.novelka.core.model.AiCall(UUID.randomUUID().toString(), job, "proofread", 0, "test", "v1", 0, "{}", .1, "complete"));
            sql.exec("UPDATE ai_calls SET actual_usd=0.012 WHERE id=?", known);
        }
        var costs = get(owner, "/manage/costs?novel=" + first + "&details=true&sort=created&page=1&size=1");
        assertEquals(200, costs.statusCode(), costs.body());
        assertEquals(2, body(costs).path("total").asInt());
        var priced = body(get(owner, "/manage/costs?novel=" + first + "&details=true&sort=actual_usd&direction=desc&page=1&size=1"));
        assertEquals("translate", priced.path("items").get(0).path("stage").asText());
        var unknown = body(get(owner, "/manage/costs?novel=" + first + "&details=true&sort=actual_usd&direction=desc&page=2&size=1"));
        assertEquals("proofread", unknown.path("items").get(0).path("stage").asText());
    }

    @Test
    void notificationsAreDurableScopedAndReadIndependently() throws Exception {
        try (var reader = registered(); var second = registered(); var admin = registered(); var guest = browser()) {
            post(owner, "/accounts/" + userId(admin) + "/role", Map.of("role", "ADMIN"));
            String novel = seed();
            long chapterId;
            long glossaryId;
            try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", ""); var sql = jdbc()) {
                db.jobs().save(db.jobs().latest(novel, 1));
                assertEquals(1, sql.rows("SELECT id FROM notifications WHERE novel_id=?", novel).size());
                chapterId = ((Number) sql.rows("SELECT id FROM notifications WHERE novel_id=?", novel).getFirst().get("id")).longValue();
                var entry = new panrid.space.novelka.core.model.Entry("person", "character", "涼", "", "Рьо", List.of(), "male", "", "confirmed", 1, true);
                db.glossaryService().update(novel, new panrid.space.novelka.core.model.Glossary(1, List.of(entry)));
                db.glossaryService().update(novel, new panrid.space.novelka.core.model.Glossary(2, List.of(entry)));
                var events = sql.rows("SELECT id,entry_count FROM notifications WHERE novel_id=? AND kind='glossary_added'", novel);
                assertEquals(1, events.size());
                assertEquals(1, ((Number) events.getFirst().get("entry_count")).intValue());
                glossaryId = ((Number) events.getFirst().get("id")).longValue();
                assertThrows(IllegalStateException.class, () -> sql.transaction(() -> {
                    new panrid.space.novelka.core.repository.NotificationEventRepository(sql).chapterPublished(novel, 99);
                    throw new IllegalStateException("rollback");
                }));
                assertTrue(sql.rows("SELECT id FROM notifications WHERE novel_id=? AND chapter=99", novel).isEmpty());
            }
            assertEquals(401, get(guest, "/notifications").statusCode());
            assertEquals(1, body(get(reader, "/notifications")).path("unread").asInt());
            assertEquals(2, body(get(admin, "/notifications")).path("unread").asInt());
            assertEquals(404, post(reader, "/notifications/" + glossaryId + "/read", Map.of()).statusCode());
            var noCsrf = reader.send(HttpRequest.newBuilder(URI.create(base + "/notifications/" + chapterId + "/read"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, noCsrf.statusCode());
            assertEquals(204, post(reader, "/notifications/" + chapterId + "/read", Map.of()).statusCode());
            assertEquals(0, body(get(reader, "/notifications")).path("unread").asInt());
            assertEquals(1, body(get(second, "/notifications")).path("unread").asInt());
            assertEquals(204, post(admin, "/notifications/read-all?through=" + chapterId, Map.of()).statusCode());
            assertEquals(1, body(get(admin, "/notifications")).path("unread").asInt());
            post(owner, "/accounts/" + userId(admin) + "/role", Map.of("role", "READER"));
            assertEquals(0, body(get(admin, "/notifications")).path("unread").asInt());
            try (var newcomer = registered()) {
                assertEquals(0, body(get(newcomer, "/notifications")).path("items").size());
            }
        }
    }

    @Test
    void taskEventsAreDeduplicatedAndQuickActionsUseLatestWork() throws Exception {
        String novel = seed();
        String task = body(post(owner, "/tasks", task(novel, UUID.randomUUID().toString(), .1))).path("id").asText();
        try (var sql = jdbc(); var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            var queue = new TaskRepository(sql);
            var job = new Pipeline(db, null, 6000).create(novel, 1, true);
            queue.job(task, job.id(), 0);
            queue.state(task, "failed", "Dictionary tool limit exceeded");
            queue.state(task, "failed", "Dictionary tool limit exceeded");
            assertEquals(1, sql.rows("SELECT id FROM notifications WHERE task_id=?", task).size());
            assertEquals(true, queue.find(task).get("can_resume"));
            assertEquals(false, queue.find(task).get("can_proofread"));
            new Pipeline(db, null, 6000).create(novel, 1, true);
            assertEquals(false, queue.find(task).get("can_resume"));
            queue.state(task, "running", "");
            queue.recoverInterrupted();
            queue.recoverInterrupted();
            assertEquals(1, sql.rows("SELECT id FROM notifications WHERE task_id=? AND kind='task_interrupted'", task).size());
        }
        JsonNode event = null;
        for (var item : body(get(owner, "/notifications")).path("items"))
            if (task.equals(item.path("task_id").asText()) && item.path("kind").asText().equals("task_interrupted")) event = item;
        assertNotNull(event, "task notification must be on the first page");
        assertEquals("translate", event.path("task_operation").asText());
        assertEquals(1, event.path("task_first").asInt());
        assertEquals(1, event.path("task_last").asInt());
        assertEquals(200, get(owner, "/tasks/" + task).statusCode());
        try (var reader = registered()) { assertEquals(404, get(reader, "/tasks/" + task).statusCode(), "other people's tasks stay hidden"); }
    }

    @Test
    void glossaryProposalsGroupDuplicatesAndRememberDismissalsWithoutChangingCanonicalData() throws Exception {
        String novel = seed();
        var entry = new panrid.space.novelka.core.model.Entry("person", "character", "涼", "", "Рьо", List.of("Ryo", "Ryō"), "male", "", "confirmed", 1, true);
        var duplicate = new panrid.space.novelka.core.model.Entry("other-key", "character", "涼", "", "Рьо", List.of("Ryō", "Ryo"), "male", "", "confirmed", 2, false);
        var alternative = new panrid.space.novelka.core.model.Entry("other-key", "character", "涼", "", "Рьоу", List.of("Ryō", "Ryo"), "male", "", "confirmed", 2, false);
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            db.glossaryService().update(novel, new panrid.space.novelka.core.model.Glossary(1, List.of(entry)));
            db.glossaries().propose(novel, "job", entry);
            db.glossaries().propose(novel, "job", duplicate);
            db.glossaries().propose(novel, "job", alternative);
            db.glossaries().propose(novel, "job", alternative);
        }
        var proposals = body(get(owner, "/manage/" + novel + "/proposals?sort=created&direction=asc")).path("items");
        assertEquals(2, proposals.size());
        assertEquals("in_dictionary", proposals.get(0).path("status").asText());
        assertEquals(2, proposals.get(0).path("occurrences").asInt());
        assertEquals("pending", proposals.get(1).path("status").asText());
        assertEquals("person", proposals.get(1).path("canonicalKey").asText());
        long id = proposals.get(1).path("id").asLong();
        String dismiss = "/manage/" + novel + "/proposals/" + id + "/dismiss";
        try (var reader = registered()) { assertEquals(403, post(reader, dismiss, Map.of()).statusCode()); }
        var noCsrf = owner.send(HttpRequest.newBuilder(URI.create(base + dismiss)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrf.statusCode());
        assertEquals(404, post(owner, "/manage/" + seed() + "/proposals/" + id + "/dismiss", Map.of()).statusCode());
        assertEquals(200, post(owner, dismiss, Map.of()).statusCode());
        assertEquals(200, post(owner, dismiss, Map.of()).statusCode());
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            db.glossaries().propose(novel, "later", alternative);
            db.glossaries().propose(novel, "later", new panrid.space.novelka.core.model.Entry("other-key", "character", "涼", "", "Рьоу", List.of("Ryō", "Ryo"), "male", "New fact", "confirmed", 3, false));
            assertEquals(1, db.glossaries().glossary(novel).revision());
            assertEquals(List.of(entry), db.glossaries().glossary(novel).entries());
        }
        var updated = body(get(owner, "/manage/" + novel + "/proposals?sort=created&direction=asc")).path("items");
        assertEquals("dismissed", updated.get(1).path("status").asText());
        assertEquals(3, updated.get(1).path("occurrences").asInt());
        assertEquals("pending", updated.get(2).path("status").asText());
    }

    @Test
    void taskSearchLimitIsValidatedAndPersistedWithLegacyDefault() throws Exception {
        String novel = seed();
        for (int limit : new int[]{-1, 31}) {
            var request = new TaskRequest(UUID.randomUUID().toString(), "translate", novel, null, 1, 1, null, false, false, .1, limit);
            assertEquals(400, post(owner, "/tasks", request).statusCode());
        }
        var request = new TaskRequest(UUID.randomUUID().toString(), "translate", novel, null, 1, 1, null, false, false, .1, 12);
        String id = body(post(owner, "/tasks", request)).path("id").asText();
        assertEquals(12, body(get(owner, "/tasks/" + id)).path("request").path("dictionarySearchLimit").asInt());
        post(owner, "/tasks/" + id + "/cancel", Map.of());
        var legacy = Json.M.valueToTree(task(novel, UUID.randomUUID().toString(), .1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("dictionarySearchLimit");
        assertEquals(6, Json.decode(legacy.toString(), TaskRequest.class).dictionarySearchLimit());
    }

    @Test
    void enforcesSessionsCsrfAndFreshRoleChecks() throws Exception {
        try (var anonymous = browser(); var reader = registered()) {
            assertEquals(200, get(anonymous, "/novels").statusCode());
            assertEquals(401, get(anonymous, "/accounts").statusCode());
            assertEquals(403, get(reader, "/accounts").statusCode());
            assertEquals(403, get(reader, "/settings").statusCode());
            assertEquals(403, get(reader, "/settings/openrouter-credits").statusCode());
            assertEquals(403, post(reader, "/tasks", task(seed(), UUID.randomUUID().toString(), .1)).statusCode(), "only the translator or an administrator");
            var withoutCsrf = reader.send(HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, withoutCsrf.statusCode());
            assertFalse(body(get(reader, "/auth/me")).path("user").isNull());
            assertEquals(204, post(reader, "/auth/logout", Map.of()).statusCode());
            assertEquals(401, get(reader, "/corrections").statusCode());
            var health = get(reader, "/health");
            assertEquals(200, health.statusCode(), health.body());
            assertEquals("ok", body(health).path("status").asText());
            assertTrue(body(get(reader, "/auth/me")).path("user").isNull());
        }
    }

    @Test
    void ownerControlsAdminsAndAdminCannotEscalateOrChangeOwner() throws Exception {
        try (var admin = registered(); var editor = registered()) {
            String adminId = userId(admin), editorId = userId(editor), ownerId = userId(owner);
            assertEquals(200, post(owner, "/accounts/" + adminId + "/role", Map.of("role", "ADMIN")).statusCode());
            assertEquals(200, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "MODERATOR")).statusCode());
            assertEquals(400, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "EDITOR")).statusCode(), "the global editor role is gone");
            assertEquals(403, get(editor, "/corrections?queue=true").statusCode(), "moderators review nothing until a novel grants it");
            assertEquals(403, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "ADMIN")).statusCode());
            assertEquals(403, post(admin, "/accounts/" + ownerId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, post(owner, "/accounts/" + ownerId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, post(owner, "/accounts/" + editorId + "/role", Map.of("role", "OWNER")).statusCode());
            assertEquals(200, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, get(admin, "/accounts/audit").statusCode());
            assertEquals(200, get(owner, "/accounts/audit").statusCode());
        }
    }

    @Test
    void managementPublishesLocalizedNovelMetadata() throws Exception {
        String novel = seed();
        var metadata = Map.of(
                "titleUk", "Водяний маг",
                "authorUk", "Кубо Тадаші",
                "descriptionUk", "Історія про мага, який починає нове життя.");
        assertEquals(200, post(owner, "/manage/" + novel + "/metadata", metadata).statusCode());
        var publicNovel = body(get(owner, "/novels/" + novel));
        assertEquals("Водяний маг", publicNovel.path("title").asText());
        assertEquals("Кубо Тадаші", publicNovel.path("author").asText());
        assertEquals("Історія про мага, який починає нове життя.", publicNovel.path("description").asText());
        var managed = body(get(owner, "/manage/" + novel));
        assertEquals("Водяний маг", managed.path("novel").path("titleUk").asText());
        assertEquals("Кубо Тадаші", managed.path("novel").path("authorUk").asText());
    }

    @Test
    void publishesCorrectionsAsRevisionsAndKeepsPendingTextPrivate() throws Exception {
        String novel = seed();
        try (var reader = registered(); var stranger = browser()) {
            String path = "/novels/" + novel + "/chapters/1";
            var original = body(get(reader, path));
            var proposal = Map.of("novelId", novel, "chapter", 1, "baseJobId", original.path("jobId").asText(),
                    "blockIndex", 1, "original", "Він ішов.", "replacement", "Він крокував.", "reason", "Точніше дієслово");
            var submitted = post(reader, "/corrections", proposal);
            assertEquals(200, submitted.statusCode(), submitted.body());
            String correction = body(submitted).path("id").asText();
            assertEquals(correction, body(post(reader, "/corrections", proposal)).path("id").asText(), "a second edit updates the open draft");
            assertEquals("Він крокував.", body(get(reader, path)).path("personalReplacements").path("1").asText());
            assertEquals("draft", body(get(reader, path)).path("personalStates").path("1").asText());
            assertEquals(1, body(get(reader, path)).path("draftCount").asInt());
            assertEquals(409, post(owner, "/corrections/" + correction + "/review", Map.of("approve", true, "note", "")).statusCode(), "drafts are not reviewable");
            assertEquals(200, post(reader, "/corrections/submit", Map.of("novelId", novel, "chapter", 1)).statusCode());
            assertEquals("pending", body(get(reader, path)).path("personalStates").path("1").asText());
            assertTrue(body(get(stranger, path)).path("personalReplacements").isEmpty());
            assertEquals("Він ішов.", body(get(stranger, path)).path("blocks").get(1).path("text").asText());
            assertEquals(403, post(reader, "/corrections/" + correction + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(owner, "/corrections/" + correction + "/review", Map.of("approve", true, "note", "Погоджено")).statusCode());
            assertEquals(409, post(owner, "/corrections/" + correction + "/review", Map.of("approve", true, "note", "")).statusCode());
            var published = body(get(stranger, path));
            assertEquals(2, published.path("revision").asInt());
            assertEquals("Він крокував.", published.path("blocks").get(1).path("text").asText());
            assertTrue(body(get(reader, path)).path("personalReplacements").isEmpty());
            try (var jdbc = jdbc()) {
                assertEquals(1, jdbc.rows("SELECT * FROM work_origins WHERE child_job_id=?", published.path("jobId").asText()).size());
            }
            assertTrue(get(owner, "/manage/" + novel + "/export?format=html").body().contains("Він крокував."));
        }
    }

    @Test
    void correctionListCombinesFiltersSortingPagesAndKeepsDiffOnProposalBase() throws Exception {
        String first = seed();
        String second = seed();
        try (var one = registered(); var two = registered()) {
            String jobA = body(get(one, "/novels/" + first + "/chapters/1")).path("jobId").asText();
            String jobB = body(get(two, "/novels/" + second + "/chapters/1")).path("jobId").asText();
            String paragraph = propose(one, first, jobA, 1, "Він ішов.", "Він крокував.");
            String heading = propose(two, first, jobA, 0, "Пролог", "Початок");
            String other = propose(two, second, jobB, 1, "Він ішов.", "Він біг.");
            String author = userId(two);
            String name = body(get(two, "/auth/me")).path("user").path("username").asText();
            var today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);

            var mine = body(get(two, "/corrections?sort=created&direction=asc"));
            assertEquals(2, mine.path("total").asInt());
            assertFalse(mine.path("items").get(0).has("original"), "list must not carry correction texts");
            assertEquals("Пролог", mine.path("items").get(0).path("chapter_title").asText());
            assertEquals(0, body(get(two, "/corrections?authorId=" + userId(one))).path("total").asInt(),
                    "a reader only sees own corrections even with another author filter");

            assertEquals(2, body(get(owner, "/corrections?queue=true&novel=" + first)).path("total").asInt());
            assertEquals(2, body(get(owner, "/corrections?queue=true&authorId=" + author)).path("total").asInt());
            assertEquals(1, body(get(owner, "/corrections?queue=true&authorId=" + author + "&novel=" + first)).path("total").asInt());
            assertEquals(0, body(get(owner, "/corrections?queue=true&novel=" + first + "&chapter=2")).path("total").asInt());
            assertEquals(1, body(get(owner, "/corrections?queue=true&novel=" + second + "&q=" + name)).path("total").asInt());
            assertEquals(2, body(get(owner, "/corrections?queue=true&novel=" + first + "&q=%D0%9F%D1%80%D0%BE%D0%BB%D0%BE%D0%B3")).path("total").asInt());
            assertEquals(2, body(get(owner, "/corrections?queue=true&authorId=" + author + "&dateFrom=" + today.minusDays(1)
                    + "&dateTo=" + today.plusDays(1) + "&state=pending")).path("total").asInt());
            assertEquals(0, body(get(owner, "/corrections?queue=true&authorId=" + author + "&dateTo=" + today.minusDays(2))).path("total").asInt());

            String paged = "/corrections?queue=true&authorId=" + author + "&sort=created&direction=asc&size=1&page=";
            var page1 = body(get(owner, paged + 1));
            var page2 = body(get(owner, paged + 2));
            assertEquals(2, page1.path("totalPages").asInt());
            assertEquals(heading, page1.path("items").get(0).path("id").asText());
            assertEquals(other, page2.path("items").get(0).path("id").asText());
            var byNovel = get(owner, "/corrections?queue=true&authorId=" + author + "&sort=novel&direction=desc&size=1&page=2");
            assertEquals(200, byNovel.statusCode(), byNovel.body());
            assertEquals(1, body(byNovel).path("items").size());

            assertEquals(400, get(owner, "/corrections?queue=true&dateFrom=" + today + "&dateTo=" + today.minusDays(1)).statusCode());
            assertEquals(400, get(owner, "/corrections?queue=true&dateFrom=23.09.2026").statusCode());
            assertEquals(400, get(owner, "/corrections?queue=true&chapter=-1").statusCode());
            assertEquals(400, get(owner, "/corrections?queue=true&sort=original").statusCode());
            assertEquals(403, get(one, "/corrections/authors?q=u").statusCode());
            var authors = body(get(owner, "/corrections/authors?q=" + name)).path("items");
            assertEquals(author, authors.get(0).path("id").asText());

            assertEquals(403, get(one, "/corrections/" + heading).statusCode());
            assertEquals(404, get(owner, "/corrections/" + UUID.randomUUID()).statusCode());
            assertEquals(200, post(owner, "/corrections/" + paragraph + "/review", Map.of("approve", true, "note", "")).statusCode());
            var approved = body(get(one, "/corrections/" + paragraph));
            assertEquals(1, approved.path("base_revision").asInt());
            assertEquals(2, approved.path("published_revision").asInt());
            var pending = body(get(owner, "/corrections/" + heading));
            assertEquals("Пролог", pending.path("original").asText(), "diff base is the proposal text, not the newer revision");
            assertEquals(1, pending.path("base_revision").asInt());
            assertEquals(1, body(get(owner, "/corrections?queue=true&novel=" + first + "&state=approved")).path("total").asInt());
        }
    }

    @Test
    void accountsSignInByEmailOrCurrentNicknameWithCooldownAndPrivateHistory() throws Exception {
        assertEquals("owner@example.test", body(get(owner, "/profile")).path("email").asText(), "bootstrap email is normalized");
        String name = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (var client = browser(); var other = browser(); var reader = registered(); var sql = jdbc()) {
            assertEquals(200, post(client, "/auth/register", Map.of("username", name, "email", name + "@Example.test", "password", PASSWORD)).statusCode());
            assertEquals(400, post(other, "/auth/register", Map.of("username", name + "x", "email", name + "@example.test", "password", PASSWORD)).statusCode());
            assertEquals(400, post(other, "/auth/register", Map.of("username", name, "email", name + "x@example.test", "password", PASSWORD)).statusCode());
            assertEquals(400, post(other, "/auth/register", Map.of("username", name + "y", "email", "not-an-email", "password", PASSWORD)).statusCode());
            assertEquals(204, loginStatus(other, " " + name.toUpperCase() + "@EXAMPLE.TEST "));
            assertEquals(204, loginStatus(client, name));
            String id = userId(client);

            var profile = body(get(client, "/profile"));
            assertEquals(name + "@example.test", profile.path("email").asText());
            assertEquals(0, profile.path("nicknameChanges").asInt());
            assertTrue(profile.path("nicknameAvailableAt").isNull());

            String second = name + "b";
            assertEquals(200, post(client, "/profile/nickname", Map.of("nickname", second)).statusCode());
            assertEquals(second, body(get(client, "/auth/me")).path("user").path("username").asText(), "session survives rename");
            assertEquals(401, loginStatus(browser(), name), "old nickname no longer signs in");
            assertEquals(204, loginStatus(browser(), second));
            assertEquals(204, loginStatus(browser(), name + "@example.test"));
            assertEquals(429, post(client, "/profile/nickname", Map.of("nickname", name + "c")).statusCode());
            assertTrue(java.time.Instant.parse(body(get(client, "/profile")).path("nicknameAvailableAt").asText())
                    .isAfter(java.time.Instant.now().plus(java.time.Duration.ofMinutes(110))));

            sql.exec("UPDATE nickname_changes SET changed_at=changed_at-interval '3 hours' WHERE account_id=?", id);
            assertEquals(200, post(client, "/profile/nickname", Map.of("nickname", name + "c")).statusCode());
            assertEquals(429, post(client, "/profile/nickname", Map.of("nickname", name + "d")).statusCode());
            assertTrue(java.time.Instant.parse(body(get(client, "/profile")).path("nicknameAvailableAt").asText())
                    .isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(13))));
            sql.exec("UPDATE nickname_changes SET changed_at=changed_at-interval '15 days' WHERE account_id=?", id);
            assertEquals(200, post(client, "/profile/nickname", Map.of("nickname", name + "d")).statusCode());
            assertTrue(java.time.Instant.parse(body(get(client, "/profile")).path("nicknameAvailableAt").asText())
                    .isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(55))));
            assertEquals(400, post(reader, "/profile/nickname", Map.of("nickname", name + "d")).statusCode(), "nickname stays unique");

            assertEquals(403, get(reader, "/accounts/" + id + "/nicknames").statusCode());
            var history = body(get(owner, "/accounts/" + id + "/nicknames")).path("items");
            assertEquals(3, history.size());
            assertEquals(name + "c", history.get(0).path("previous_nickname").asText());
            assertEquals(name + "d", history.get(0).path("new_nickname").asText());
            assertEquals(name, history.get(2).path("previous_nickname").asText());

            assertEquals(403, post(client, "/profile/email", Map.of("email", name + "-new@example.test", "password", "wrong-password-123")).statusCode());
            assertEquals(400, post(client, "/profile/email", Map.of("email", body(get(reader, "/profile")).path("email").asText(), "password", PASSWORD)).statusCode());
            assertEquals(200, post(client, "/profile/email", Map.of("email", name + "-new@example.test", "password", PASSWORD)).statusCode());
            assertEquals(204, loginStatus(browser(), name + "-new@example.test"));
            assertEquals(401, loginStatus(browser(), name + "@example.test"));
        }
    }

    private static int loginStatus(HttpClient client, String login) throws Exception {
        var csrf = body(get(client, "/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + java.net.URLEncoder.encode(login, java.nio.charset.StandardCharsets.UTF_8)
                        + "&password=" + PASSWORD)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void modelCatalogIsCachedKeepsLastListOnFailureAndPricesNewTasks() throws Exception {
        try (var anonymous = browser()) { assertEquals(401, get(anonymous, "/models").statusCode()); }
        var catalog = body(get(owner, "/models"));
        assertEquals("openrouter", catalog.path("provider").asText());
        assertFalse(catalog.path("refreshedAt").isNull());
        assertEquals("good/model", catalog.path("items").get(0).path("id").asText(), "suitable models come first");
        assertEquals(0.15, catalog.path("items").get(0).path("inputUsdM").asDouble());

        modelsFail = true;
        try {
            var failed = body(post(owner, "/models/refresh", Map.of()));
            assertTrue(failed.path("error").asText().contains("503"));
            assertEquals(4, failed.path("items").size(), "last good list stays available");
        } finally {
            modelsFail = false;
        }

        var settings = (com.fasterxml.jackson.databind.node.ObjectNode) body(get(owner, "/settings"));
        var original = settings.deepCopy();
        for (var stage : settings.withArray("stages")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) stage).put("model", "good/model").put("inputUsdM", 9).put("outputUsdM", 9)
                    .put("catalogPricing", true);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) settings.withArray("stages").get(2)).put("model", "no/such-model");
        assertEquals(200, post(owner, "/settings", settings).statusCode());
        try {
            String novel = seed();
            String task = body(post(owner, "/tasks", task(novel, UUID.randomUUID().toString(), .1))).path("id").asText();
            try (var sql = jdbc()) {
                var snapshot = Json.read(sql.rows("SELECT settings FROM web_tasks WHERE id=?", task).getFirst().get("settings").toString());
                assertEquals(0.15, snapshot.path("stages").get(0).path("inputUsdM").asDouble(), "catalog price used");
                assertEquals(0.6, snapshot.path("stages").get(0).path("outputUsdM").asDouble());
                assertEquals(9, snapshot.path("stages").get(2).path("inputUsdM").asDouble(), "manual price kept when the catalog has no model");
            }
            var saved = body(get(owner, "/settings"));
            assertEquals(9, saved.path("stages").get(0).path("inputUsdM").asDouble(), "catalog never overwrites stored manual prices");
        } finally {
            original.put("revision", body(get(owner, "/settings")).path("revision").asLong());
            assertEquals(200, post(owner, "/settings", original).statusCode());
        }
    }

    @Test
    void taskModelOverrideAppliesOnlyToThatTaskAndNeedsCatalogPrices() throws Exception {
        assertEquals(200, get(owner, "/models").statusCode(), "catalog is filled from the stub provider");
        var defaults = body(get(owner, "/tasks/defaults"));
        String defaultModel = defaults.path("models").path("translate").asText();
        try (var anonymous = browser()) { assertEquals(401, get(anonymous, "/tasks/defaults").statusCode()); }
        String novel = seed();
        var overridden = overrideTask(novel, "translate", "good/model");
        var created = post(owner, "/tasks", overridden);
        assertEquals(200, created.statusCode(), created.body());
        String task = body(created).path("id").asText();
        try (var sql = jdbc()) {
            var snapshot = Json.read(sql.rows("SELECT settings FROM web_tasks WHERE id=?", task).getFirst().get("settings").toString());
            for (var stage : snapshot.path("stages")) {
                assertEquals("good/model", stage.path("model").asText());
                assertEquals(0.15, stage.path("inputUsdM").asDouble());
            }
        }
        var details = body(get(owner, "/tasks/" + task));
        assertEquals("good/model", details.path("model_override").asText());
        assertEquals("good/model", details.path("model").asText());
        assertEquals(defaultModel, body(get(owner, "/tasks/defaults")).path("models").path("translate").asText(), "global default untouched");

        var plain = body(post(owner, "/tasks", task(novel, UUID.randomUUID().toString(), .1))).path("id").asText();
        assertTrue(body(get(owner, "/tasks/" + plain)).path("model_override").isNull());
        var unsuitable = post(owner, "/tasks", overrideTask(novel, "translate", "no/tools"));
        assertEquals(400, unsuitable.statusCode());
        assertTrue(unsuitable.body().contains("no/tools"), unsuitable.body());
        assertEquals(400, post(owner, "/tasks", overrideTask(novel, "translate", "missing/model")).statusCode(), "unknown price");
        var imported = overrideTask(novel, "import", "good/model");
        imported.put("url", "https://ncode.syosetu.com/n0022gd/");
        imported.put("first", 0);
        imported.put("last", 0);
        assertEquals(400, post(owner, "/tasks", imported).statusCode(), "import has no model");
    }

    private static Map<String, Object> overrideTask(String novel, String operation, String model) {
        var request = new java.util.HashMap<String, Object>(Json.M.convertValue(
                new TaskRequest(UUID.randomUUID().toString(), operation, novel, null, 1, 1, null, false, false, .1), Map.class));
        request.put("overrides", Map.of("model", model));
        return request;
    }

    @Test
    void tagsAreNormalizedSharedAndFilterTheCatalogTogether() throws Exception {
        String first = seed();
        String second = seed();
        String unique = "Тег" + UUID.randomUUID().toString().substring(0, 8);
        var saved = post(owner, "/manage/" + first + "/tags", Map.of("tags", List.of("  Фентезі ", "фентезі", "ФЕНТЕЗІ", unique, "Машинний  переклад")));
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(3, body(saved).path("tags").size(), "case and spaces do not create duplicates");
        assertEquals(200, post(owner, "/manage/" + second + "/tags", Map.of("tags", List.of("фентезі"))).statusCode());

        String fantasy = java.net.URLEncoder.encode("фентезі", java.nio.charset.StandardCharsets.UTF_8);
        String machine = java.net.URLEncoder.encode("МАШИННИЙ ПЕРЕКЛАД", java.nio.charset.StandardCharsets.UTF_8);
        String scope = "&q=test-";
        var both = body(get(owner, "/novels?tag=" + fantasy + scope + "&size=100"));
        assertTrue(both.path("items").findValuesAsText("id").containsAll(List.of(first, second)));
        var narrowed = body(get(owner, "/novels?tag=" + fantasy + "&tag=" + machine + scope + "&size=100")).path("items").findValuesAsText("id");
        assertTrue(narrowed.contains(first));
        assertFalse(narrowed.contains(second), "several tags narrow the catalog");
        var card = body(get(owner, "/novels?q=" + first)).path("items").get(0);
        assertEquals(3, card.path("tags").size());
        assertEquals(3, body(get(owner, "/novels/" + first)).path("tags").size());

        try (var anonymous = browser(); var reader = registered()) {
            var tags = body(get(anonymous, "/tags?q=" + fantasy)).path("items");
            assertEquals("фентезі", tags.get(0).path("slug").asText());
            assertTrue(tags.get(0).path("novels").asInt() >= 2);
            assertEquals(403, post(reader, "/manage/" + first + "/tags", Map.of("tags", List.of("x"))).statusCode());
        }
        var tooMany = new java.util.ArrayList<String>();
        for (int i = 0; i < 13; i++) tooMany.add("tag-" + i);
        assertEquals(400, post(owner, "/manage/" + first + "/tags", Map.of("tags", tooMany)).statusCode());
        assertEquals(400, post(owner, "/manage/" + first + "/tags", Map.of("tags", List.of(" "))).statusCode());
        assertTrue(body(get(owner, "/manage/" + first)).path("aiTranslated").asBoolean(), "seeded novel has a Novelka translation");
        assertEquals(200, post(owner, "/manage/" + first + "/tags", Map.of("tags", List.of())).statusCode());
        assertEquals(0, body(get(owner, "/novels/" + first)).path("tags").size());
    }

    @Test
    void manualNovelPublishesDraftsAsOrdinaryRevisionsWithoutAi() throws Exception {
        var created = post(owner, "/manage/novels", Map.of("titleUk", "Власний переклад", "authorUk", "Авторка",
                "descriptionUk", "Опис", "tags", List.of("Машинний переклад")));
        assertEquals(200, created.statusCode(), created.body());
        String novel = body(created).path("id").asText();
        assertEquals("машинний переклад", body(get(owner, "/novels/" + novel)).path("tags").get(0).path("slug").asText());
        try (var reader = registered(); var anonymous = browser()) {
            assertEquals(200, post(reader, "/manage/novels", Map.of("titleUk", "x")).statusCode(), "anyone publishes their own translation");
            assertEquals(403, post(reader, "/manage/" + novel + "/manual/1", Map.of("title", "x", "text", "y")).statusCode());

            assertEquals(200, post(owner, "/manage/" + novel + "/manual/1", Map.of("title", "Початок", "text", "Перший абзац.\n\nДругий абзац.")).statusCode());
            assertEquals(404, get(anonymous, "/novels/" + novel + "/chapters/1").statusCode(), "draft stays private");
            assertEquals(1, body(get(owner, "/manage/" + novel + "/manual")).path("drafts").size());

            var published = post(owner, "/manage/" + novel + "/manual/1/publish", Map.of());
            assertEquals(200, published.statusCode(), published.body());
            assertEquals(1, body(published).path("revision").asInt());
            var chapter = body(get(anonymous, "/novels/" + novel + "/chapters/1"));
            assertEquals("Початок", chapter.path("blocks").get(0).path("text").asText());
            assertEquals("Другий абзац.", chapter.path("blocks").get(2).path("text").asText());
            assertEquals(1, body(get(anonymous, "/novels/" + novel)).path("chapterCount").asInt());
            assertEquals(404, post(owner, "/manage/" + novel + "/manual/1/publish", Map.of()).statusCode(), "draft consumed");

            var edit = body(get(owner, "/manage/" + novel + "/manual/1"));
            assertTrue(edit.path("draft").isNull());
            assertEquals("Перший абзац.\n\nДругий абзац.", edit.path("published").path("text").asText());
            assertEquals(200, post(owner, "/manage/" + novel + "/manual/1", Map.of("title", "Початок", "text", "Перший абзац.\n\nВиправлений абзац.")).statusCode());
            assertEquals("Другий абзац.", body(get(anonymous, "/novels/" + novel + "/chapters/1")).path("blocks").get(2).path("text").asText(),
                    "editing a draft does not change the published text");
            assertEquals(2, body(post(owner, "/manage/" + novel + "/manual/1/publish", Map.of())).path("revision").asInt());
            var revised = body(get(anonymous, "/novels/" + novel + "/chapters/1"));
            assertEquals("Виправлений абзац.", revised.path("blocks").get(2).path("text").asText());
            try (var sql = jdbc()) {
                assertEquals(1, sql.rows("SELECT 1 FROM work_origins WHERE child_job_id=?", revised.path("jobId").asText()).size());
                assertEquals(1, sql.rows("SELECT 1 FROM notifications WHERE kind='chapter_published' AND novel_id=?", novel).size());
            }
            assertEquals(200, post(owner, "/manage/" + novel + "/manual/3", Map.of("title", "Третя", "text", "Текст.")).statusCode());
            assertEquals(200, delete(owner, "/manage/" + novel + "/manual/3").statusCode());
            assertEquals(404, delete(owner, "/manage/" + novel + "/manual/3").statusCode());
        }
        assertEquals(400, post(owner, "/manage/" + seed() + "/manual/1", Map.of("title", "x", "text", "y")).statusCode(), "imported originals are protected");
        var translate = post(owner, "/tasks", task(novel, UUID.randomUUID().toString(), .1));
        assertEquals(400, translate.statusCode());
        assertTrue(translate.body().contains("вручну"), translate.body());
    }

    private static HttpResponse<String> delete(HttpClient client, String path) throws Exception {
        var csrf = body(get(client, "/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void novelVotesKeepOneActiveVotePerAccountAndRankTheCatalog() throws Exception {
        String liked = seed();
        String other = seed();
        String alias = "a" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(200, post(owner, "/manage/" + liked + "/aliases", Map.of("alias", alias)).statusCode());
        try (var anonymous = browser(); var first = registered(); var second = registered()) {
            assertEquals(401, post(anonymous, "/votes/novel/" + liked, Map.of("value", 1)).statusCode());
            var up = body(post(first, "/votes/novel/" + alias, Map.of("value", 1)));
            assertEquals(1, up.path("score").asLong());
            assertEquals(1, up.path("mine").asInt());
            assertEquals(1, body(post(first, "/votes/novel/" + liked, Map.of("value", 1))).path("score").asLong(), "repeating a vote is idempotent");
            assertEquals(2, body(post(second, "/votes/novel/" + liked, Map.of("value", 1))).path("score").asLong());
            var changed = body(post(first, "/votes/novel/" + liked, Map.of("value", -1)));
            assertEquals(0, changed.path("score").asLong());
            assertEquals(-1, changed.path("mine").asInt());
            assertEquals(1, body(post(first, "/votes/novel/" + liked, Map.of("value", 0))).path("score").asLong(), "removal");
            assertEquals(400, post(first, "/votes/novel/" + liked, Map.of("value", 2)).statusCode());
            assertEquals(404, post(first, "/votes/chapter/" + liked, Map.of("value", 1)).statusCode());
            assertEquals(1, body(get(second, "/novels/" + liked)).path("rating").path("mine").asInt());
            assertEquals(0, body(get(anonymous, "/novels/" + liked)).path("rating").path("mine").asInt());
            assertEquals(1, body(get(anonymous, "/novels/" + liked)).path("rating").path("score").asLong());
            assertEquals(-1, body(post(first, "/votes/novel/" + other, Map.of("value", -1))).path("score").asLong());
            var ranked = body(get(anonymous, "/novels/search?q=test-&sort=rating&direction=desc&size=100")).path("items").findValuesAsText("id");
            assertTrue(ranked.indexOf(liked) < ranked.indexOf(other));
            assertEquals(1, body(get(anonymous, "/novels/search?q=" + liked)).path("items").get(0).path("score").asLong());
        }
    }

    @Test
    void libraryKeepsOnePrivateShelfPerNovel() throws Exception {
        String reading = seed();
        String planned = seed();
        String alias = "a" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(200, post(owner, "/manage/" + reading + "/aliases", Map.of("alias", alias)).statusCode());
        try (var anonymous = browser(); var reader = registered(); var other = registered()) {
            assertEquals(401, post(anonymous, "/library/" + reading, Map.of("status", "reading")).statusCode());
            assertEquals(401, get(anonymous, "/library").statusCode());
            assertEquals("reading", body(post(reader, "/library/" + alias, Map.of("status", "reading"))).path("status").asText());
            assertEquals(200, post(reader, "/library/" + planned, Map.of("status", "completed")).statusCode());
            assertEquals("planned", body(post(reader, "/library/" + planned, Map.of("status", "planned"))).path("status").asText(), "changing a shelf replaces it");
            assertEquals(400, post(reader, "/library/" + planned, Map.of("status", "favourite")).statusCode());
            assertEquals(404, post(reader, "/library/missing-novel", Map.of("status", "reading")).statusCode());

            var counts = body(get(reader, "/library/counts"));
            assertEquals(1, counts.path("reading").asLong());
            assertEquals(1, counts.path("planned").asLong());
            assertEquals(0, counts.path("completed").asLong());
            var shelf = body(get(reader, "/library?status=reading"));
            assertEquals(1, shelf.path("total").asLong());
            assertEquals(reading, shelf.path("items").get(0).path("id").asText());
            assertEquals(2, body(get(reader, "/library?sort=title&direction=asc")).path("total").asLong());
            assertEquals(400, get(reader, "/library?status=favourite").statusCode());
            assertEquals("reading", body(get(reader, "/novels/" + reading)).path("libraryStatus").asText());
            assertTrue(body(get(anonymous, "/novels/" + reading)).path("libraryStatus").isNull());
            assertEquals(0, body(get(other, "/library")).path("total").asLong(), "shelves are private");

            assertTrue(body(post(reader, "/library/" + reading, Map.of("status", ""))).path("status").isNull());
            assertEquals(0, body(get(reader, "/library/counts")).path("reading").asLong());
        }
    }

    @Test
    void commentsSeparateNovelAndChapterThreadsFollowNicknamesAndAreModerated() throws Exception {
        String novel = seed();
        try (var author = registered(); var other = registered(); var anonymous = browser(); var sql = jdbc()) {
            assertEquals(401, post(anonymous, "/novels/" + novel + "/comments", Map.of("body", "Привіт")).statusCode());
            var created = post(author, "/novels/" + novel + "/comments", Map.of("body", "  Чудова новела  "));
            assertEquals(200, created.statusCode(), created.body());
            long novelComment = body(created).path("id").asLong();
            assertEquals(429, post(author, "/novels/" + novel + "/comments", Map.of("chapter", 1, "body", "Надто швидко")).statusCode());
            sql.exec("UPDATE comments SET created_at=created_at-interval '1 minute' WHERE id=?", novelComment);
            long chapterComment = body(post(author, "/novels/" + novel + "/comments", Map.of("chapter", 1, "body", "Про главу"))).path("id").asLong();
            assertEquals(400, post(other, "/novels/" + novel + "/comments", Map.of("chapter", 99, "body", "x")).statusCode());
            assertEquals(400, post(other, "/novels/" + novel + "/comments", Map.of("body", " ")).statusCode());

            var thread = body(get(anonymous, "/novels/" + novel + "/comments"));
            assertEquals(1, thread.path("items").size(), "chapter comments stay out of the novel thread");
            assertEquals("Чудова новела", thread.path("items").get(0).path("body").asText());
            assertFalse(thread.path("items").get(0).path("can_edit").asBoolean());
            assertEquals(chapterComment, body(get(anonymous, "/novels/" + novel + "/comments?chapter=1")).path("items").get(0).path("id").asLong());

            String renamed = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            assertEquals(200, post(author, "/profile/nickname", Map.of("nickname", renamed)).statusCode());
            assertEquals(renamed, body(get(anonymous, "/novels/" + novel + "/comments")).path("items").get(0).path("author").asText());

            assertEquals(403, post(other, "/comments/" + novelComment, Map.of("body", "Чужий")).statusCode());
            assertEquals(200, post(author, "/comments/" + novelComment, Map.of("body", "Чудова новела!")).statusCode());
            var edited = body(get(author, "/novels/" + novel + "/comments")).path("items").get(0);
            assertFalse(edited.path("edited_at").isNull());
            assertTrue(edited.path("can_edit").asBoolean());

            assertEquals(1, body(post(other, "/votes/comment/" + novelComment, Map.of("value", 1))).path("score").asLong());
            assertEquals(1, body(get(other, "/novels/" + novel + "/comments")).path("items").get(0).path("rating").path("mine").asInt());
            assertEquals(403, delete(other, "/comments/" + novelComment).statusCode());
            assertEquals(403, delete(owner, "/comments/" + chapterComment).statusCode(), "moderators hide, they never delete other people's words");
            assertEquals(403, post(other, "/comments/" + chapterComment + "/hide", Map.of("reason", "")).statusCode());
            assertFalse(body(get(other, "/novels/" + novel + "/comments?chapter=1")).path("items").get(0).path("can_moderate").asBoolean());
            post(owner, "/accounts/" + userId(other) + "/role", Map.of("role", "MODERATOR"));
            assertTrue(body(get(other, "/novels/" + novel + "/comments?chapter=1")).path("items").get(0).path("can_moderate").asBoolean());
            assertEquals(400, post(other, "/comments/" + chapterComment + "/hide", Map.of("reason", "x".repeat(301))).statusCode());
            assertEquals(200, post(other, "/comments/" + chapterComment + "/hide", Map.of("reason", "Спойлер")).statusCode());
            assertEquals(1, sql.rows("SELECT 1 FROM audit_events WHERE action='comment.hide' AND target=?", String.valueOf(chapterComment)).size());
            var hidden = body(get(anonymous, "/novels/" + novel + "/comments?chapter=1")).path("items").get(0);
            assertTrue(hidden.path("hidden").asBoolean());
            assertEquals("Спойлер", hidden.path("hidden_reason").asText());
            assertEquals("Про главу", hidden.path("body").asText(), "readers can still reveal a hidden comment");
            assertEquals(200, post(other, "/comments/" + chapterComment + "/unhide", Map.of()).statusCode());
            assertFalse(body(get(anonymous, "/novels/" + novel + "/comments?chapter=1")).path("items").get(0).path("hidden").asBoolean());
            assertEquals(200, delete(author, "/comments/" + chapterComment).statusCode());
            assertEquals(0, body(get(anonymous, "/novels/" + novel + "/comments?chapter=1")).path("items").size());
            assertEquals(404, post(other, "/votes/comment/" + chapterComment, Map.of("value", 1)).statusCode());
            assertEquals(200, delete(author, "/comments/" + novelComment).statusCode());
            assertEquals(1, sql.rows("SELECT 1 FROM comments WHERE id=? AND deleted_at IS NOT NULL", novelComment).size(), "soft delete");

            String authorId = userId(other);
            for (int i = 0; i < 25; i++) sql.exec("INSERT INTO comments(novel_id,chapter,author_id,body) VALUES(?,2,?,?)", novel, authorId, "Коментар " + i);
            var first = body(get(anonymous, "/novels/" + novel + "/comments?chapter=2"));
            assertEquals(20, first.path("items").size());
            assertEquals("Коментар 24", first.path("items").get(0).path("body").asText());
            var older = body(get(anonymous, "/novels/" + novel + "/comments?chapter=2&before=" + first.path("nextCursor").asLong()));
            assertEquals(5, older.path("items").size());
            assertEquals(0, older.path("nextCursor").asLong());
        }
    }

    @Test
    void chatPollsNewMessagesPagesHistoryAndIsModerated() throws Exception {
        try (var anonymous = browser(); var author = registered(); var other = registered(); var sql = jdbc()) {
            assertEquals(401, get(anonymous, "/chat").statusCode());
            assertEquals(401, post(anonymous, "/chat", Map.of("body", "Привіт")).statusCode());
            long start = body(get(author, "/chat")).path("items").isEmpty() ? 0
                    : body(get(author, "/chat")).path("items").get(0).path("id").asLong();
            var sent = post(author, "/chat", Map.of("body", "  Привіт усім  "));
            assertEquals(200, sent.statusCode(), sent.body());
            long first = body(sent).path("id").asLong();
            assertEquals(429, post(author, "/chat", Map.of("body", "Ще")).statusCode());
            assertEquals(400, post(other, "/chat", Map.of("body", "x".repeat(1001))).statusCode());
            long second = body(post(other, "/chat", Map.of("body", "Вітаю"))).path("id").asLong();

            var updates = body(get(other, "/chat/updates?after=" + start)).path("items");
            assertEquals(List.of(first, second), List.of(updates.get(0).path("id").asLong(), updates.get(1).path("id").asLong()), "oldest first");
            assertEquals("Привіт усім", updates.get(0).path("body").asText());
            assertFalse(updates.get(0).path("can_delete").asBoolean());
            assertFalse(updates.get(0).path("can_moderate").asBoolean());
            var forOwner = body(get(owner, "/chat/updates?after=" + start)).path("items").get(0);
            assertFalse(forOwner.path("can_delete").asBoolean());
            assertTrue(forOwner.path("can_moderate").asBoolean());

            assertEquals(403, delete(other, "/chat/" + first).statusCode());
            assertEquals(403, delete(owner, "/chat/" + first).statusCode());
            assertEquals(403, post(other, "/chat/" + first + "/hide", Map.of("reason", "")).statusCode());
            assertEquals(200, post(owner, "/chat/" + first + "/hide", Map.of("reason", "Реклама")).statusCode());
            assertEquals(1, sql.rows("SELECT 1 FROM audit_events WHERE action='chat.hide' AND target=?", String.valueOf(first)).size());
            var moderated = body(get(other, "/chat/updates?after=" + second)).path("moderated");
            assertEquals(first, moderated.get(0).path("id").asLong(), "open chats learn about the change");
            assertTrue(moderated.get(0).path("hidden").asBoolean());
            assertEquals("Реклама", moderated.get(0).path("hidden_reason").asText());
            assertEquals(200, post(owner, "/chat/" + first + "/unhide", Map.of()).statusCode());
            assertFalse(body(get(other, "/chat/updates?after=" + second)).path("moderated").get(0).path("hidden").asBoolean());
            assertEquals(200, delete(author, "/chat/" + first).statusCode());
            var after = body(get(other, "/chat/updates?after=" + start));
            assertEquals(1, after.path("items").size());
            assertTrue(after.path("deleted").toString().contains(String.valueOf(first)));
            assertEquals(200, delete(other, "/chat/" + second).statusCode());
            assertEquals(404, delete(other, "/chat/" + second).statusCode());

            String id = userId(other);
            for (int i = 0; i < 35; i++) sql.exec("INSERT INTO chat_messages(author_id,body) VALUES(?,?)", id, "Повідомлення " + i);
            var latest = body(get(other, "/chat"));
            assertEquals(30, latest.path("items").size());
            assertEquals("Повідомлення 34", latest.path("items").get(0).path("body").asText());
            var older = body(get(other, "/chat?before=" + latest.path("nextCursor").asLong()));
            assertTrue(older.path("items").size() >= 5);
            assertEquals("Повідомлення 4", older.path("items").get(0).path("body").asText());
        }
    }

    @Test
    void batchedCorrectionsAndReplacementsPublishOneRevisionPerChapter() throws Exception {
        String novel = seed();
        String path = "/novels/" + novel + "/chapters/1";
        try (var reader = registered(); var other = registered()) {
            String job = body(get(reader, path)).path("jobId").asText();
            var draft = post(reader, "/corrections", Map.of("novelId", novel, "chapter", 1, "baseJobId", job, "blockIndex", 1,
                    "original", "Він ішов.", "replacement", "Він ішов повільно.", "reason", ""));
            String paragraph = body(draft).path("id").asText();
            var preview = body(get(reader, "/corrections/replace-preview?novel=" + novel + "&chapter=1&find=" + enc("Пролог") + "&scope=novel"));
            assertEquals(1, preview.path("total").asInt());
            assertEquals(400, post(reader, "/corrections/replace", Map.of("novelId", novel, "chapter", 1, "baseJobId", job,
                    "find", "Немає такого", "replacement", "x", "scope", "chapter", "reason", "")).statusCode());
            String replace = body(post(reader, "/corrections/replace", Map.of("novelId", novel, "chapter", 1, "baseJobId", job,
                    "find", "Пролог", "replacement", "Вступ", "scope", "novel", "reason", "Єдина назва"))).path("id").asText();

            assertEquals(403, post(other, "/corrections/" + paragraph, Map.of("replacement", "Чужа", "reason", "")).statusCode());
            assertEquals(200, post(reader, "/corrections/" + paragraph, Map.of("replacement", "Він ішов неквапом.", "reason", "")).statusCode());
            assertEquals("Він ішов неквапом.", body(get(reader, path)).path("personalReplacements").path("1").asText());
            assertEquals(2, body(get(reader, path)).path("draftCount").asInt());
            assertEquals(0, body(get(owner, "/corrections?queue=true&novel=" + novel)).path("total").asInt(), "drafts stay out of the queue");

            var submitted = body(post(reader, "/corrections/submit", Map.of("novelId", novel)));
            assertEquals(2, submitted.path("count").asInt());
            assertEquals(400, post(reader, "/corrections/submit", Map.of("novelId", novel)).statusCode(), "nothing left to submit");
            String batch = submitted.path("batchId").asText();
            assertEquals(2, body(get(owner, "/corrections/" + paragraph)).path("batch_size").asInt());
            assertEquals(403, post(reader, "/corrections/batches/" + batch + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(owner, "/corrections/batches/" + batch + "/review", Map.of("approve", true, "note", "")).statusCode());

            var chapter = body(get(other, path));
            assertEquals(2, chapter.path("revision").asInt(), "one revision for the whole batch");
            assertEquals("Вступ", chapter.path("blocks").get(0).path("text").asText());
            assertEquals("Він ішов неквапом.", chapter.path("blocks").get(1).path("text").asText());
            assertEquals("approved", body(get(owner, "/corrections/" + replace)).path("state").asText());
            assertEquals(409, post(reader, "/corrections/" + paragraph, Map.of("replacement", "Пізно", "reason", "")).statusCode(), "reviewed corrections are final");

            String job2 = chapter.path("jobId").asText();
            String withdrawn = body(post(other, "/corrections", Map.of("novelId", novel, "chapter", 1, "baseJobId", job2, "blockIndex", 1,
                    "original", "Він ішов неквапом.", "replacement", "Він біг.", "reason", ""))).path("id").asText();
            assertEquals(200, delete(other, "/corrections/" + withdrawn).statusCode());
            assertEquals(404, get(other, "/corrections/" + withdrawn).statusCode());
            assertEquals(0, body(get(other, path)).path("draftCount").asInt());
        }
    }

    private static String enc(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }

    @Test
    void translatorChoosesWhoReviewsCorrectionsOfTheirNovel() throws Exception {
        String novel = seed();
        String other = seed();
        try (var translator = registered(); var writer = registered(); var editor = registered(); var stranger = registered(); var sql = jdbc()) {
            sql.exec("UPDATE novels SET owner_id=? WHERE id=?", userId(translator), novel);
            String job = body(get(writer, "/novels/" + novel + "/chapters/1")).path("jobId").asText();
            String otherJob = body(get(writer, "/novels/" + other + "/chapters/1")).path("jobId").asText();
            String proposal = propose(writer, novel, job, 1, "Він ішов.", "Він крокував.");
            String elsewhere = propose(writer, other, otherJob, 1, "Він ішов.", "Він біг.");
            assertTrue(body(get(translator, "/auth/me")).path("canReview").asBoolean());
            assertFalse(body(get(editor, "/auth/me")).path("canReview").asBoolean());
            assertEquals(403, get(editor, "/corrections?queue=true").statusCode());
            assertEquals(403, get(editor, "/manage/" + novel + "/editors").statusCode());
            assertEquals(403, post(stranger, "/manage/" + novel + "/editors", Map.of("accountId", userId(stranger))).statusCode());
            assertEquals(404, get(translator, "/manage/missing-novel/editors").statusCode());
            assertEquals(403, get(translator, "/manage/" + other + "/editors").statusCode(), "only their own novel");
            assertEquals(userId(translator), body(get(translator, "/manage/" + novel + "/editors")).path("owner").path("id").asText());
            String strangerName = body(get(stranger, "/auth/me")).path("user").path("username").asText();
            var found = body(get(translator, "/users/search?q=" + strangerName)).path("items").get(0);
            assertEquals(strangerName, found.path("username").asText());
            assertTrue(found.path("email").isMissingNode(), "search never exposes email");
            assertEquals(401, get(browser(), "/users/search?q=" + strangerName).statusCode());

            var added = body(post(translator, "/manage/" + novel + "/editors", Map.of("accountId", userId(editor))));
            assertEquals(userId(editor), added.path("editors").get(0).path("id").asText());
            assertEquals(400, post(translator, "/manage/" + novel + "/editors", Map.of("accountId", "missing")).statusCode());
            assertTrue(body(get(editor, "/auth/me")).path("canReview").asBoolean());
            var queue = body(get(editor, "/corrections?queue=true&size=100")).path("items").findValuesAsText("id");
            assertTrue(queue.contains(proposal));
            assertFalse(queue.contains(elsewhere), "editors see only novels they review");
            assertEquals(403, get(editor, "/corrections/" + elsewhere).statusCode());
            assertTrue(body(get(editor, "/corrections/" + proposal)).path("can_review").asBoolean());
            assertEquals(403, post(editor, "/corrections/" + elsewhere + "/review", Map.of("approve", true, "note", "")).statusCode());

            String own = propose(translator, novel, job, 0, "Пролог", "Початок");
            assertTrue(body(get(translator, "/corrections/" + own)).path("can_review").asBoolean(), "the translator approves their own text");
            assertEquals(200, post(translator, "/corrections/" + own + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(editor, "/corrections/" + proposal + "/review", Map.of("approve", false, "note", "Ні")).statusCode());

            assertEquals(200, post(translator, "/manage/" + novel + "/editors", Map.of("accountId", userId(editor))).statusCode(), "idempotent");
            assertEquals(0, body(delete(translator, "/manage/" + novel + "/editors/" + userId(editor))).path("editors").size());
            assertEquals(403, get(editor, "/corrections?queue=true").statusCode());

            assertTrue(body(post(translator, "/manage/" + novel + "/review-access", Map.of("open", true))).path("openReview").asBoolean());
            String late = propose(writer, novel, body(get(writer, "/novels/" + novel + "/chapters/1")).path("jobId").asText(), 1, "Він ішов.", "Він ступав.");
            assertTrue(body(get(stranger, "/corrections/" + late)).path("can_review").asBoolean(), "open review includes everyone");
            assertFalse(body(get(writer, "/corrections/" + late)).path("can_review").asBoolean(), "but not their own corrections");
            assertEquals(200, post(stranger, "/corrections/" + late + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(translator, "/manage/" + novel + "/review-access", Map.of("open", false)).statusCode());
            assertEquals(403, get(stranger, "/corrections?queue=true").statusCode());
            assertEquals(body(get(translator, "/auth/me")).path("user").path("username").asText(),
                    body(get(stranger, "/novels/" + novel)).path("translator").asText());
        }
    }

    @Test
    void translatorsPayForAiTasksFromABalanceOnlyTheOwnerTopsUp() throws Exception {
        String novel = seed();
        String foreign = seed();
        try (var translator = registered(); var other = registered(); var sql = jdbc()) {
            String id = userId(translator);
            sql.exec("UPDATE novels SET owner_id=? WHERE id=?", id, novel);
            assertEquals(200, get(translator, "/manage/" + novel).statusCode());
            assertEquals(403, get(translator, "/manage/" + foreign).statusCode());
            assertEquals(403, get(other, "/manage/" + novel + "/jobs").statusCode());
            var mine = body(get(translator, "/manage/novels")).path("items").findValuesAsText("id");
            assertEquals(List.of(novel), mine, "the workshop lists only their own novels");
            assertEquals(0, body(get(translator, "/manage/costs")).path("total").asLong());

            var empty = body(get(translator, "/balance"));
            assertEquals(0, empty.path("available").decimalValue().signum(), "every balance starts at zero");
            assertFalse(empty.path("unlimited").asBoolean());
            var refused = post(translator, "/tasks", task(novel, UUID.randomUUID().toString(), .1));
            assertEquals(402, refused.statusCode());
            assertTrue(body(refused).path("message").asText().contains("Недостатньо коштів"));

            assertEquals(403, post(other, "/accounts/" + id + "/balance", Map.of("amountUsd", 5, "note", "")).statusCode());
            assertEquals(400, post(owner, "/accounts/" + id + "/balance", Map.of("amountUsd", 0, "note", "")).statusCode());
            assertEquals(400, post(owner, "/accounts/" + id + "/balance", Map.of("amountUsd", -1, "note", "")).statusCode(), "cannot go below zero");
            assertEquals(0, new java.math.BigDecimal("0.25").compareTo(body(post(owner, "/accounts/" + id + "/balance",
                    Map.of("amountUsd", 0.25, "note", "Старт"))).path("available").decimalValue()));
            assertEquals("Старт", body(get(owner, "/accounts/" + id + "/balance")).path("topups").get(0).path("note").asText());
            assertEquals(403, get(translator, "/accounts/" + id + "/balance").statusCode());

            String key = UUID.randomUUID().toString();
            var created = post(translator, "/tasks", task(novel, key, .1));
            assertEquals(200, created.statusCode(), created.body());
            String task = body(created).path("id").asText();
            assertEquals(task, body(post(translator, "/tasks", task(novel, key, .1))).path("id").asText(), "a retry is not charged twice");
            var reserved = body(get(translator, "/balance"));
            assertEquals(0, new java.math.BigDecimal("0.25").compareTo(reserved.path("available").decimalValue()
                    .add(reserved.path("reserved").decimalValue()).add(reserved.path("spent").decimalValue())));
            assertTrue(reserved.path("available").decimalValue().compareTo(new java.math.BigDecimal("0.15")) <= 0);
            assertEquals(402, post(translator, "/tasks", task(novel, UUID.randomUUID().toString(), .2)).statusCode());
            assertEquals(403, post(translator, "/tasks", task(foreign, UUID.randomUUID().toString(), .01)).statusCode());

            assertEquals(List.of(task), body(get(translator, "/tasks")).path("items").findValuesAsText("id"));
            assertFalse(body(get(other, "/tasks")).path("items").findValuesAsText("id").contains(task));
            assertTrue(body(get(owner, "/tasks?size=100")).path("items").findValuesAsText("id").contains(task));
            assertEquals(404, post(other, "/tasks/" + task + "/cancel", Map.of()).statusCode());
            assertEquals(200, post(translator, "/tasks/" + task + "/cancel", Map.of()).statusCode());
            for (int attempt = 0; attempt < 100 && body(get(translator, "/balance")).path("reserved").decimalValue().signum() > 0; attempt++)
                Thread.sleep(100);
            var settled = body(get(translator, "/balance"));
            assertEquals(0, settled.path("reserved").decimalValue().signum(), "a finished task keeps only what it spent");
            assertEquals(0, new java.math.BigDecimal("0.25").compareTo(settled.path("available").decimalValue().add(settled.path("spent").decimalValue())));

            assertTrue(body(get(owner, "/balance")).path("unlimited").asBoolean(), "the site owner uses the site budget");
        }
    }

    @Test
    void hiddenNovelIsVisibleOnlyToItsTranslatorAndAdministrators() throws Exception {
        String novel = seed();
        try (var translator = registered(); var reader = registered(); var anonymous = browser(); var sql = jdbc()) {
            sql.exec("UPDATE novels SET owner_id=? WHERE id=?", userId(translator), novel);
            assertEquals(200, post(reader, "/library/" + novel, Map.of("status", "reading")).statusCode());
            assertEquals(403, post(translator, "/manage/" + novel + "/hide", Map.of("reason", "")).statusCode(), "only administrators hide novels");
            assertEquals(200, post(owner, "/manage/" + novel + "/hide", Map.of("reason", "Порушення правил")).statusCode());
            assertEquals(1, sql.rows("SELECT 1 FROM audit_events WHERE action='novel.hide' AND target=?", novel).size());

            for (var client : List.of(reader, anonymous)) {
                assertEquals(404, get(client, "/novels/" + novel).statusCode());
                assertEquals(404, get(client, "/novels/" + novel + "/contents").statusCode());
                assertEquals(404, get(client, "/novels/" + novel + "/chapters/1").statusCode());
                assertEquals(404, get(client, "/novels/" + novel + "/comments").statusCode());
                assertFalse(body(get(client, "/novels/search?q=" + novel)).path("items").findValuesAsText("id").contains(novel));
            }
            assertEquals(404, post(reader, "/votes/novel/" + novel, Map.of("value", 1)).statusCode());
            assertEquals(0, body(get(reader, "/library")).path("total").asLong(), "the library skips hidden novels");
            var forTranslator = body(get(translator, "/novels/" + novel));
            assertTrue(forTranslator.path("hidden").asBoolean());
            assertEquals("Порушення правил", forTranslator.path("hiddenReason").asText());
            assertEquals(200, get(translator, "/novels/" + novel + "/chapters/1").statusCode());
            assertTrue(body(get(owner, "/novels/" + novel)).path("hidden").asBoolean());
            assertTrue(body(get(translator, "/manage/novels")).path("items").get(0).path("hidden").asBoolean());

            assertEquals(200, post(owner, "/manage/" + novel + "/unhide", Map.of()).statusCode());
            assertEquals(200, get(reader, "/novels/" + novel).statusCode());
            assertFalse(body(get(reader, "/novels/" + novel)).path("hidden").asBoolean());
            assertEquals(1, body(get(reader, "/library")).path("total").asLong());
        }
    }

    @Test
    void selfApprovalSettingAppliesOnlyToAdminsAndIsEnforcedByBackend() throws Exception {
        String novel = seed();
        String job = body(get(owner, "/novels/" + novel + "/chapters/1")).path("jobId").asText();
        try (var editor = registered()) {
            assertEquals(200, post(owner, "/manage/" + novel + "/editors", Map.of("accountId", userId(editor))).statusCode());
            String own = propose(owner, novel, job, 1, "Він ішов.", "Він крокував.");
            String edited = propose(editor, novel, job, 0, "Пролог", "Початок");
            assertFalse(body(get(owner, "/corrections/" + own)).path("can_review").asBoolean());
            assertEquals(403, post(owner, "/corrections/" + own + "/review", Map.of("approve", true, "note", "")).statusCode());

            assertEquals(200, saveSelfApproval(true).statusCode());
            assertTrue(body(get(owner, "/corrections/" + own)).path("can_review").asBoolean());
            assertFalse(body(get(editor, "/corrections/" + edited)).path("can_review").asBoolean(), "editors never self-review");
            assertEquals(403, post(editor, "/corrections/" + edited + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertTrue(body(get(owner, "/corrections/" + edited)).path("can_review").asBoolean());
            assertEquals(200, post(owner, "/corrections/" + own + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertFalse(body(get(owner, "/corrections/" + own)).path("can_review").asBoolean(), "reviewed correction is closed");
        } finally {
            assertEquals(200, saveSelfApproval(false).statusCode());
        }
    }

    private static HttpResponse<String> saveSelfApproval(boolean enabled) throws Exception {
        var settings = (com.fasterxml.jackson.databind.node.ObjectNode) body(get(owner, "/settings"));
        settings.put("adminSelfApproval", enabled);
        return post(owner, "/settings", settings);
    }

    @Test
    void rejectsConflictingEditsAndSelfReviewButRebasesUnrelatedBlocks() throws Exception {
        String novel = seed();
        try (var first = registered(); var second = registered()) {
            String job = body(get(first, "/novels/" + novel + "/chapters/1")).path("jobId").asText();
            String a = propose(first, novel, job, 1, "Він ішов.", "Він крокував.");
            String b = propose(second, novel, job, 1, "Він ішов.", "Він поспішав.");
            String c = propose(second, novel, job, 0, "Пролог", "Початок");
            assertEquals(200, post(owner, "/manage/" + novel + "/editors", Map.of("accountId", userId(first))).statusCode());
            assertEquals(403, post(first, "/corrections/" + a + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(owner, "/corrections/" + a + "/review", Map.of("approve", true, "note", "")).statusCode());
            var personal = body(get(second, "/novels/" + novel + "/chapters/1")).path("personalReplacements");
            assertEquals("Початок", personal.path("0").asText());
            assertFalse(personal.has("1"));
            assertEquals(409, post(owner, "/corrections/" + b + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(owner, "/corrections/" + c + "/review", Map.of("approve", true, "note", "")).statusCode());
            assertEquals(200, post(owner, "/corrections/" + b + "/review", Map.of("approve", false, "note", "Текст уже змінено")).statusCode());
        }
    }

    @Test
    void queueIsIdempotentBudgetedCancellableAndRevokesDemotedAuthors() throws Exception {
        String novel = seed();
        try (var admin = registered()) {
            String account = userId(admin);
            post(owner, "/accounts/" + account + "/role", Map.of("role", "ADMIN"));
            assertEquals(200, post(owner, "/accounts/" + account + "/balance", Map.of("amountUsd", 1, "note", "")).statusCode());
            String key = UUID.randomUUID().toString();
            var task = task(novel, key, .1);
            var created = post(admin, "/tasks", task);
            assertEquals(200, created.statusCode(), created.body());
            String id = body(created).path("id").asText();
            assertEquals(id, body(post(admin, "/tasks", task)).path("id").asText());
            assertEquals(400, post(admin, "/tasks", task(novel, key, 0)).statusCode());
            assertEquals(400, post(admin, "/tasks", task(novel, key, 1001)).statusCode());
            post(owner, "/accounts/" + account + "/role", Map.of("role", "READER"));
            new TaskWorker(application.getBean(ReaderDatabase.class), (calls, settings, searchLimit) -> { throw new AssertionError("Demoted actor must not reach AI"); }).poll();
            try (var jdbc = jdbc()) {
                assertEquals("failed", jdbc.rows("SELECT state FROM web_tasks WHERE id=?", id).getFirst().get("state"));
                assertTrue(jdbc.rows("SELECT a.id FROM ai_calls a JOIN jobs j ON j.id=a.job_id WHERE j.novel_id=?", novel).isEmpty());
                assertEquals(1, jdbc.rows("SELECT id FROM web_tasks WHERE request_key=?", key).size());
            }
            String cancel = body(post(owner, "/tasks", task(novel, UUID.randomUUID().toString(), .1))).path("id").asText();
            assertEquals(200, post(owner, "/tasks/" + cancel + "/cancel", Map.of()).statusCode());
            try (var jdbc = jdbc()) {
                assertEquals("cancelled", jdbc.rows("SELECT state FROM web_tasks WHERE id=?", cancel).getFirst().get("state"));
                jdbc.exec("UPDATE web_tasks SET state='running' WHERE id=?", id);
                new TaskRepository(jdbc).recoverInterrupted();
                assertEquals("interrupted", jdbc.rows("SELECT state FROM web_tasks WHERE id=?", id).getFirst().get("state"));
            }
        }
    }

    @Test
    void ownerSettingsUseOptimisticRevisionAndCanCloseRegistration() throws Exception {
        var credits = body(get(owner, "/settings/openrouter-credits"));
        assertFalse(credits.path("configured").asBoolean());
        assertTrue(credits.path("remainingUsd").isNull());
        var original = body(get(owner, "/settings"));
        var updated = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) updated).put("registrationOpen", false);
        assertEquals(200, post(owner, "/settings", updated).statusCode());
        try (var guest = browser()) {
            assertEquals(403, post(guest, "/auth/register", Map.of("username", "closed", "password", PASSWORD)).statusCode());
        }
        assertEquals(409, post(owner, "/settings", original).statusCode());
        ((com.fasterxml.jackson.databind.node.ObjectNode) original).put("revision", original.path("revision").asInt() + 1);
        assertEquals(200, post(owner, "/settings", original).statusCode());
    }

    private static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    @Test
    void workerRunsPipelineAndKeepsOneBudgetAcrossChapters() throws Exception {
        String novel = seed();
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            var original = db.chapters().chapter(novel, 1);
            db.chapters().save(novel, new Chapter(2, "url", original.title(), original.blocks(), ""));
        }
        var request = new TaskRequest(UUID.randomUUID().toString(), "translate", novel, null, 1, 2, null, true, false, .1, 9);
        String task = body(post(owner, "/tasks", request)).path("id").asText();
        var budgets = new java.util.ArrayList<Double>();
        new TaskWorker(application.getBean(ReaderDatabase.class), (calls, settings, searchLimit) -> (work, index, stage, glossary, payload, budget) -> {
            assertEquals(9, searchLimit);
            budgets.add(budget);
            calls.start(new panrid.space.novelka.core.model.AiCall(UUID.randomUUID().toString(), work.id(), stage, index,
                    "fake", "test", glossary.revision(), "{}", .01, "complete"));
            return stage.equals("analyze") ? Json.read("{\"entries\":[]}") : Json.read("{\"blocks\":[{\"id\":\"title\",\"kind\":\"heading\",\"text\":\"Пролог\"},{\"id\":\"p1\",\"kind\":\"paragraph\",\"text\":\"Готово\"}],\"summary\":\"Подія\"}");
        }).poll();
        assertEquals(6, budgets.size());
        assertEquals(.1, budgets.getFirst(), .000001);
        assertEquals(.07, budgets.getLast(), .000001);
        try (var jdbc = jdbc()) {
            assertEquals("complete", jdbc.rows("SELECT state FROM web_tasks WHERE id=?", task).getFirst().get("state"));
            assertEquals(1, jdbc.rows("SELECT id FROM notifications WHERE task_id=? AND kind='task_complete'", task).size());
            assertEquals(2, jdbc.rows("SELECT id FROM notifications WHERE novel_id=? AND kind='chapter_published'", novel).size());
            var row = new TaskRepository(jdbc).find(task);
            assertEquals(.06, ((Number) row.get("spent_usd")).doubleValue(), .000001);
            String job = jdbc.rows("SELECT current_job_id FROM web_tasks WHERE id=?", task).getFirst().get("current_job_id").toString();
            // A later CLI/resume call on the same Work must not rewrite this run's history.
            new panrid.space.novelka.core.repository.AiCallRepository(jdbc).start(new panrid.space.novelka.core.model.AiCall(
                    UUID.randomUUID().toString(), job, "proofread", 0, "fake", "later", 0, "{}", .02, "complete"));
            var historical = new TaskRepository(jdbc).find(task);
            assertEquals(.06, ((Number) historical.get("spent_usd")).doubleValue(), .000001);
        }
        assertEquals(200, get(owner, "/novels/" + novel + "/chapters/2").statusCode());
    }

    @Test
    void glossaryChangesMarkManuallyEditedDescendantsForReviewWithoutUnpublishing() throws Exception {
        String novel = seed();
        String path = "/novels/" + novel + "/chapters/1";
        String job = body(get(owner, path)).path("jobId").asText();
        var entry = new panrid.space.novelka.core.model.Entry("person", "character", "涼", "", "Рьо", List.of(), "male", "", "confirmed", 1, true);
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            db.glossaryService().update(novel, new panrid.space.novelka.core.model.Glossary(1, List.of(entry)));
            db.calls().start(new panrid.space.novelka.core.model.AiCall(UUID.randomUUID().toString(), job, "translate", 0, "fake", "test", 1,
                    Json.write(Map.of("entries", List.of(entry))), .01, "complete"));
        }
        try (var reader = registered()) {
            String correction = propose(reader, novel, job, 1, "Він ішов.", "Він крокував.");
            assertEquals(200, post(owner, "/corrections/" + correction + "/review", Map.of("approve", true, "note", "")).statusCode());
        }
        var changed = new panrid.space.novelka.core.model.Entry("person", "character", "涼", "", "Рьоу", List.of(), "male", "", "confirmed", 1, true);
        assertEquals(200, post(owner, "/manage/" + novel + "/glossary", Map.of("revision", 1, "entries", List.of(changed))).statusCode());
        assertEquals(200, get(owner, path).statusCode());
        assertEquals(409, post(owner, "/manage/" + novel + "/glossary", Map.of("revision", 1, "entries", List.of(changed))).statusCode());
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            assertEquals("needs-review", db.jobs().latest(novel, 1).state());
        }
    }

    private static TaskRequest task(String novel, String key, double budget) {
        return new TaskRequest(key, "translate", novel, null, 1, 1, null, false, false, budget);
    }

    private static HttpClient registered() throws Exception {
        var client = browser();
        String name = "u" + UUID.randomUUID().toString().replace("-", "");
        var response = post(client, "/auth/register", Map.of("username", name, "email", name + "@example.test", "password", PASSWORD));
        assertEquals(200, response.statusCode(), response.body());
        login(client, name);
        return client;
    }

    private static void login(HttpClient client, String name) throws Exception {
        var csrf = body(get(client, "/auth/csrf"));
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + name + "&password=" + PASSWORD)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode(), response.body());
    }

    private static String userId(HttpClient client) throws Exception { return body(get(client, "/auth/me")).path("user").path("id").asText(); }
    private static JsonNode body(HttpResponse<String> response) { return Json.read(response.body()); }
    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, String path, Object data) throws Exception {
        var csrf = body(get(client, "/auth/csrf"));
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(data))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static JdbcSession jdbc() throws Exception { return new JdbcSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", ""); }
    private static String propose(HttpClient client, String novel, String job, int index, String original, String replacement) throws Exception {
        var response = post(client, "/corrections", Map.of("novelId", novel, "chapter", 1, "baseJobId", job, "blockIndex", index, "original", original, "replacement", replacement, "reason", ""));
        assertEquals(200, response.statusCode(), response.body());
        var submitted = post(client, "/corrections/submit", Map.of("novelId", novel, "chapter", 1));
        assertEquals(200, submitted.statusCode(), submitted.body());
        return body(response).path("id").asText();
    }
    private static String seed() throws Exception {
        String id = "test-" + UUID.randomUUID();
        try (var db = new DatabaseSession(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")) {
            db.novels().save(new Novel(id, "物語", "作者", "url", 2, false));
            var original = List.of(new Block("title", "heading", "原文"), new Block("p1", "paragraph", "原文"));
            db.chapters().save(id, new Chapter(1, "url", "原文", original, ""));
            var job = new Pipeline(db, null, 6000).create(id, 1, false);
            var translated = List.of(new Block("title", "heading", "Пролог"), new Block("p1", "paragraph", "Він ішов."));
            db.jobs().save(new Work(job.id(), id, 1, job.sourceHash(), 1, List.of(new Segment(original, List.of(), translated, "", "complete")), "complete", ""));
        }
        return id;
    }
}
