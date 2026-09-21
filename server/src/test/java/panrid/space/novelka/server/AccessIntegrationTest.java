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

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().setErrorRedirector(ProcessBuilder.Redirect.INHERIT)
                .setOutputRedirector(ProcessBuilder.Redirect.INHERIT).start();
        application = new SpringApplication(NovelkaServer.class).run("--server.port=0",
                "--novelka.database.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                "--novelka.database.user=postgres", "--novelka.database.password=",
                "--novelka.owner.username=owner", "--novelka.owner.password=" + PASSWORD,
                "--novelka.worker.enabled=false", "--server.servlet.session.cookie.secure=false");
        base = "http://127.0.0.1:" + application.getEnvironment().getProperty("local.server.port") + "/api";
        owner = browser();
        login(owner, "owner");
    }

    @AfterAll
    static void stop() throws Exception {
        if (owner != null) owner.close();
        if (application != null) application.close();
        if (postgres != null) postgres.close();
    }

    @Test
    void enforcesSessionsCsrfAndFreshRoleChecks() throws Exception {
        try (var anonymous = browser(); var reader = registered()) {
            assertEquals(200, get(anonymous, "/novels").statusCode());
            assertEquals(401, get(anonymous, "/accounts").statusCode());
            assertEquals(403, get(reader, "/accounts").statusCode());
            assertEquals(403, get(reader, "/settings").statusCode());
            assertEquals(403, post(reader, "/tasks", task("novel", UUID.randomUUID().toString(), .1)).statusCode());
            var withoutCsrf = reader.send(HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, withoutCsrf.statusCode());
            assertFalse(body(get(reader, "/auth/me")).path("user").isNull());
            assertEquals(204, post(reader, "/auth/logout", Map.of()).statusCode());
            assertEquals(401, get(reader, "/corrections").statusCode());
            assertTrue(body(get(reader, "/auth/me")).path("user").isNull());
        }
    }

    @Test
    void ownerControlsAdminsAndAdminCannotEscalateOrChangeOwner() throws Exception {
        try (var admin = registered(); var editor = registered()) {
            String adminId = userId(admin), editorId = userId(editor), ownerId = userId(owner);
            assertEquals(200, post(owner, "/accounts/" + adminId + "/role", Map.of("role", "ADMIN")).statusCode());
            assertEquals(200, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "EDITOR")).statusCode());
            assertEquals(200, get(editor, "/corrections?queue=true").statusCode());
            assertEquals(403, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "ADMIN")).statusCode());
            assertEquals(403, post(admin, "/accounts/" + ownerId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, post(owner, "/accounts/" + ownerId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, post(owner, "/accounts/" + editorId + "/role", Map.of("role", "OWNER")).statusCode());
            assertEquals(200, post(admin, "/accounts/" + editorId + "/role", Map.of("role", "READER")).statusCode());
            assertEquals(403, get(editor, "/corrections?queue=true").statusCode());
            assertEquals(403, get(admin, "/accounts/audit").statusCode());
            assertEquals(200, get(owner, "/accounts/audit").statusCode());
        }
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
            assertEquals(409, post(reader, "/corrections", proposal).statusCode());
            assertEquals("Він крокував.", body(get(reader, path)).path("personalReplacements").path("1").asText());
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
    void rejectsConflictingEditsAndSelfReviewButRebasesUnrelatedBlocks() throws Exception {
        String novel = seed();
        try (var first = registered(); var second = registered()) {
            String job = body(get(first, "/novels/" + novel + "/chapters/1")).path("jobId").asText();
            String a = propose(first, novel, job, 1, "Він ішов.", "Він крокував.");
            String b = propose(second, novel, job, 1, "Він ішов.", "Він поспішав.");
            String c = propose(second, novel, job, 0, "Пролог", "Початок");
            post(owner, "/accounts/" + userId(first) + "/role", Map.of("role", "EDITOR"));
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
            String key = UUID.randomUUID().toString();
            var task = task(novel, key, .1);
            var created = post(admin, "/tasks", task);
            assertEquals(200, created.statusCode(), created.body());
            String id = body(created).path("id").asText();
            assertEquals(id, body(post(admin, "/tasks", task)).path("id").asText());
            assertEquals(400, post(admin, "/tasks", task(novel, key, 0)).statusCode());
            assertEquals(400, post(admin, "/tasks", task(novel, key, 1001)).statusCode());
            post(owner, "/accounts/" + account + "/role", Map.of("role", "READER"));
            new TaskWorker(application.getBean(ReaderDatabase.class), (calls, settings) -> { throw new AssertionError("Demoted actor must not reach AI"); }).poll();
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
        var request = new TaskRequest(UUID.randomUUID().toString(), "translate", novel, null, 1, 2, null, true, false, .1);
        String task = body(post(owner, "/tasks", request)).path("id").asText();
        var budgets = new java.util.ArrayList<Double>();
        new TaskWorker(application.getBean(ReaderDatabase.class), (calls, settings) -> (work, index, stage, glossary, payload, budget) -> {
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
            var row = new TaskRepository(jdbc).list(0).stream().filter(item -> item.get("id").equals(task)).findFirst().orElseThrow();
            assertEquals(.06, ((Number) row.get("spent_usd")).doubleValue(), .000001);
            String job = jdbc.rows("SELECT current_job_id FROM web_tasks WHERE id=?", task).getFirst().get("current_job_id").toString();
            // A later CLI/resume call on the same Work must not rewrite this run's history.
            new panrid.space.novelka.core.repository.AiCallRepository(jdbc).start(new panrid.space.novelka.core.model.AiCall(
                    UUID.randomUUID().toString(), job, "proofread", 0, "fake", "later", 0, "{}", .02, "complete"));
            var historical = new TaskRepository(jdbc).list(0).stream().filter(item -> item.get("id").equals(task)).findFirst().orElseThrow();
            assertEquals(.06, ((Number) historical.get("spent_usd")).doubleValue(), .000001);
        }
        assertEquals(200, get(owner, "/novels/" + novel + "/chapters/2").statusCode());
    }

    @Test
    void glossaryChangesInvalidateManuallyEditedDescendants() throws Exception {
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
        assertEquals(404, get(owner, path).statusCode());
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
        var response = post(client, "/auth/register", Map.of("username", name, "password", PASSWORD));
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
