package space.panrid.novelka.webhook.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOTIFICATION;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;
import static space.panrid.novelka.jooq.Tables.WEBHOOK_DELIVERY;
import static space.panrid.novelka.jooq.Tables.WEBHOOK_ENDPOINT;
import static space.panrid.novelka.support.Browser.json;
import static space.panrid.novelka.support.Eventually.eventually;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import com.sun.net.httpserver.HttpServer;

import space.panrid.novelka.autotranslate.RunEnded;
import space.panrid.novelka.autotranslate.SourceChaptersFound;
import space.panrid.novelka.jooq.tables.records.WebhookDeliveryRecord;
import space.panrid.novelka.suggestion.SuggestionsSubmitted;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.LocalWebhooks;
import space.panrid.novelka.support.TestMailbox;
import space.panrid.novelka.text.ChaptersPublished;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 22.5: webhooks — queued with the event, signed, retried after pauses, switched off after
 * failing for long, only to those who may hear, never through a redirect.
 */
@IntegrationTest
class WebhookTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    WebhookOutbox outbox;

    @Autowired
    LocalWebhooks guard;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ApplicationEventPublisher events;

    Person translator;
    Person reader;
    long edition;
    Stub stub;

    @BeforeEach
    void aTranslationWithAChapterAndAReceiver() throws IOException {
        translator = Accounts.signedIn(port, mailbox);
        reader = Accounts.signedIn(port, mailbox);
        edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Переклад для вебхуків %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        publish(1);
        stub = new Stub();
        guard.allow(stub.port());
        // Endpoints of earlier tests would hear about this translation too, and their stub's port may be reused.
        db.deleteFrom(WEBHOOK_ENDPOINT).execute();
    }

    @AfterEach
    void stopTheStub() {
        stub.close();
        db.deleteFrom(WEBHOOK_ENDPOINT).execute();
    }

    @Test
    void aPublishedChapterIsQueuedWithItSignedAndDeliveredOnce() throws Exception {
        JsonNode made = read(reader.browser().post("/api/me/webhooks",
                json("url", stub.url(), "events", List.of("chapter.published"), "editionIds", List.of(edition))));
        String secret = made.path("secret").asString();
        assertThat(secret).startsWith("whsec_").hasSizeGreaterThan(40);
        long endpoint = made.path("endpoint").path("id").asLong();
        assertThat(made.path("endpoint").path("secretHint").asString()).endsWith("…").hasSizeLessThan(14);
        Response list = reader.browser().get("/api/me/webhooks");
        assertThat(list.body()).doesNotContain(secret).contains(stub.url());
        assertThat(read(translator.browser().get("/api/me/webhooks")).path("translations")).as("the team's translations to choose from")
                .anySatisfy(option -> {
                    assertThat(option.path("editionId").asLong()).isEqualTo(edition);
                    assertThat(option.path("member").asBoolean()).isTrue();
                });

        publish(2);
        assertThat(deliveries(endpoint)).as("queued by the publication itself").singleElement()
                .satisfies(row -> assertThat(row.getState()).isEqualTo("pending"));
        assertThat(stub.received).as("nothing goes before the sender").isEmpty();

        assertThat(outbox.drain()).isEqualTo(1);
        assertThat(stub.received).hasSize(1);
        Received got = stub.received.peek();
        assertThat(got.path()).isEqualTo("/hook");
        assertThat(got.header("X-Novelka-Event")).isEqualTo("chapter.published");
        WebhookDeliveryRecord row = deliveries(endpoint).getFirst();
        assertThat(got.header("X-Novelka-Delivery")).isEqualTo(row.getDeliveryKey().toString());
        String timestamp = got.header("X-Novelka-Timestamp");
        assertThat(Long.parseLong(timestamp)).isCloseTo(System.currentTimeMillis() / 1000, org.assertj.core.data.Offset.offset(60L));
        assertThat(got.header("X-Novelka-Signature")).isEqualTo("sha256=" + hmac(secret, timestamp + "." + got.body()));
        assertThat(got.header("Content-Type")).startsWith("application/json");

        JsonNode body = JSON.readTree(got.body());
        assertThat(body.path("id").asString()).isEqualTo(row.getDeliveryKey().toString());
        assertThat(body.path("event").asString()).isEqualTo("chapter.published");
        assertThat(body.path("createdAt").asString()).isNotBlank();
        JsonNode data = body.path("data");
        assertThat(data.path("translation").path("team").asString()).isNotBlank();
        assertThat(data.path("translation").path("url").asString()).contains("/n/" + data.path("translation").path("novel").asString());
        assertThat(data.path("first").asInt()).isEqualTo(2);
        assertThat(data.path("last").asInt()).isEqualTo(2);
        assertThat(data.path("chapters").path(0).path("title").asString()).isEqualTo("Глава 2");
        assertThat(got.body()).as("no internal ids, no original").doesNotContain("editionId").doesNotContain("original");

        assertThat(row.getState()).isEqualTo("delivered");
        JsonNode journal = read(reader.browser().get("/api/me/webhooks/" + endpoint + "/deliveries"));
        assertThat(journal.path(0).path("state").asString()).isEqualTo("delivered");
        assertThat(journal.path(0).path("status").asInt()).isEqualTo(200);
        assertThat(journal.path(0).path("attempts").asInt()).isEqualTo(1);
        assertThat(journal.path(0).path("event").asString()).isEqualTo("chapter.published");
        assertThat(journal.path(0).has("durationMs")).isTrue();

        assertThat(outbox.drain()).as("once").isZero();
        assertThat(stub.received).hasSize(1);
        assertThat(translator.browser().get("/api/me/webhooks/" + endpoint + "/deliveries").status()).as("someone else's").isEqualTo(404);
    }

    @Test
    void aDeliveryIsQueuedOnlyWhenTheEventCommits() {
        long endpoint = endpoint(reader, List.of("chapter.published"), List.of());
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new ChaptersPublished(edition, 1, 1));
            assertThat(deliveries(endpoint)).hasSize(1);
            status.setRollbackOnly();
        });
        assertThat(deliveries(endpoint)).as("rolled back with the publication").isEmpty();
    }

    @Test
    void failuresAreTriedAgainAfterGrowingPausesThenTheAddressIsSwitchedOff() {
        long endpoint = endpoint(reader, List.of("chapter.published"), List.of(edition));
        stub.status.set(500);
        publish(2);
        outbox.drain();
        WebhookDeliveryRecord row = deliveries(endpoint).getFirst();
        assertThat(row.getState()).isEqualTo("pending");
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastStatus()).isEqualTo(500);
        assertThat(row.getLastError()).contains("500");
        assertThat(row.getNotBefore()).isBetween(OffsetDateTime.now().plusSeconds(5), OffsetDateTime.now().plusSeconds(15));
        assertThat(outbox.drain()).as("not before the pause").isZero();

        due(row.getId());
        outbox.drain();
        row = deliveries(endpoint).getFirst();
        assertThat(row.getAttempts()).isEqualTo(2);
        assertThat(row.getNotBefore()).as("a longer pause").isBetween(OffsetDateTime.now().plusSeconds(50), OffsetDateTime.now().plusSeconds(70));
        assertThat(stub.received).hasSize(2);
        assertThat(stub.received).extracting(received -> received.header("X-Novelka-Delivery")).as("the same delivery").containsOnly(
                row.getDeliveryKey().toString());

        // The last attempt: it gives up.
        db.update(WEBHOOK_DELIVERY).set(WEBHOOK_DELIVERY.ATTEMPTS, WebhookOutbox.MAX_ATTEMPTS - 1).where(WEBHOOK_DELIVERY.ID.eq(row.getId()))
                .execute();
        due(row.getId());
        outbox.drain();
        row = deliveries(endpoint).getFirst();
        assertThat(row.getState()).isEqualTo("failed");
        assertThat(row.getAttempts()).isEqualTo(WebhookOutbox.MAX_ATTEMPTS);
        assertThat(db.select(WEBHOOK_ENDPOINT.DISABLED_AT).from(WEBHOOK_ENDPOINT).where(WEBHOOK_ENDPOINT.ID.eq(endpoint))
                .fetchSingle().value1()).as("one bad day is not enough").isNull();

        // Failing for more than a day, many times in a row: switched off, its owner told.
        db.update(WEBHOOK_ENDPOINT).set(WEBHOOK_ENDPOINT.FAILURES, WebhookOutbox.DISABLE_AFTER_FAILURES - 1)
                .set(WEBHOOK_ENDPOINT.FAILING_SINCE, OffsetDateTime.now().minusHours(25)).where(WEBHOOK_ENDPOINT.ID.eq(endpoint)).execute();
        publish(3);
        publish(4);
        outbox.drain();
        var off = db.selectFrom(WEBHOOK_ENDPOINT).where(WEBHOOK_ENDPOINT.ID.eq(endpoint)).fetchSingle();
        assertThat(off.getDisabledAt()).isNotNull();
        assertThat(off.getDisabledReason()).contains("поспіль");
        assertThat(deliveries(endpoint)).extracting(WebhookDeliveryRecord::getState).as("no more tries once it is off")
                .containsExactly("failed", "cancelled", "cancelled");
        long owner = accountId(reader);
        assertThat(eventually(() -> db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(owner)
                .and(NOTIFICATION.KIND.eq("webhook_disabled"))), count -> count > 0)).isEqualTo(1);
        JsonNode overview = read(reader.browser().get("/api/me/webhooks"));
        assertThat(overview.path("endpoints").path(0).path("enabled").asBoolean()).isFalse();

        publish(5);
        assertThat(deliveries(endpoint)).as("nothing more for an endpoint switched off").hasSize(3);
        long failed = deliveries(endpoint).stream().filter(d -> d.getState().equals("failed")).findFirst().orElseThrow().getId();
        assertThat(reader.browser().post("/api/me/webhooks/" + endpoint + "/deliveries/" + failed + "/retry", "{}").status()).isEqualTo(409);

        // Switched on again and «Повторити»: the same delivery goes now.
        stub.status.set(204);
        JsonNode on = read(reader.browser().patch("/api/me/webhooks/" + endpoint, json("enabled", true)));
        assertThat(on.path("enabled").asBoolean()).isTrue();
        assertThat(on.path("failures").asInt()).isZero();
        int before = stub.received.size();
        JsonNode retried = read(reader.browser().post("/api/me/webhooks/" + endpoint + "/deliveries/" + failed + "/retry", "{}"));
        assertThat(retried.path("state").asString()).isEqualTo("delivered");
        assertThat(retried.path("status").asInt()).isEqualTo(204);
        assertThat(retried.path("attempts").asInt()).isEqualTo(1);
        assertThat(stub.received).hasSize(before + 1);
    }

    @Test
    void theTestEventGoesAtOnceAndARedirectIsNotFollowed() {
        long endpoint = endpoint(reader, List.of("chapter.published"), List.of());
        JsonNode sent = read(reader.browser().post("/api/me/webhooks/" + endpoint + "/test", "{}"));
        assertThat(sent.path("event").asString()).isEqualTo("ping");
        assertThat(sent.path("state").asString()).isEqualTo("delivered");
        assertThat(stub.received).singleElement().satisfies(got -> {
            assertThat(got.header("X-Novelka-Event")).isEqualTo("ping");
            assertThat(JSON.readTree(got.body()).path("data").path("message").asString()).contains("Пробна подія");
        });

        stub.redirect = "http://127.0.0.1:" + stub.port() + "/elsewhere";
        stub.status.set(302);
        JsonNode moved = read(reader.browser().post("/api/me/webhooks/" + endpoint + "/test", "{}"));
        assertThat(moved.path("state").asString()).as("a trial is tried once").isEqualTo("failed");
        assertThat(moved.path("status").asInt()).isEqualTo(302);
        assertThat(moved.path("error").asString()).contains("переадресацію");
        assertThat(stub.received).extracting(Received::path).containsOnly("/hook");
        assertThat(db.select(WEBHOOK_ENDPOINT.FAILURES).from(WEBHOOK_ENDPOINT).where(WEBHOOK_ENDPOINT.ID.eq(endpoint))
                .fetchSingle().value1()).as("a trial does not count against the address").isZero();
    }

    @Test
    void eventsForTheTeamReachOnlyTheTeamAndAccessIsCheckedAgainWhenSending() {
        List<String> all = List.of("chapter.published", "run.finished", "run.stopped", "suggestion.created", "source.chapters");
        long readers = endpoint(reader, all, List.of());
        long team = endpoint(translator, all, List.of());
        Person editor = Accounts.signedIn(port, mailbox);
        long teamId = db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(edition)).fetchSingle().value1();
        db.insertInto(TEAM_MEMBER).set(TEAM_MEMBER.TEAM_ID, teamId).set(TEAM_MEMBER.ACCOUNT_ID, accountId(editor))
                .set(TEAM_MEMBER.ROLE, "editor").execute();
        long editors = endpoint(editor, List.of("suggestion.created"), List.of(edition));

        happen(new ChaptersPublished(edition, 1, 1));
        happen(new SuggestionsSubmitted(edition, accountId(reader), 2));
        happen(new RunEnded(41, edition, "done", 1, 3, null));
        happen(new RunEnded(42, edition, "failed", 4, 6, "Модель не відповіла."));
        happen(new RunEnded(43, edition, "cancelled", 7, 9, null));
        happen(new SourceChaptersFound(edition, 2, 5));

        assertThat(events(readers)).as("readers hear only what readers see").containsExactly("chapter.published");
        assertThat(events(team)).containsExactly("chapter.published", "suggestion.created", "run.finished", "run.stopped", "run.stopped",
                "source.chapters");
        assertThat(events(editors)).containsExactly("suggestion.created");

        // The editor left the team before it went: not sent.
        db.deleteFrom(TEAM_MEMBER).where(TEAM_MEMBER.TEAM_ID.eq(teamId), TEAM_MEMBER.ACCOUNT_ID.eq(accountId(editor))).execute();
        outbox.drain();
        WebhookDeliveryRecord left = deliveries(editors).getFirst();
        assertThat(left.getState()).isEqualTo("cancelled");
        assertThat(left.getLastError()).contains("недоступний");
        Map<String, List<String>> byDelivery = new HashMap<>();
        stub.received.forEach(got -> byDelivery.computeIfAbsent(got.header("X-Novelka-Event"), key -> new java.util.ArrayList<>())
                .add(got.body()));
        assertThat(byDelivery.get("run.stopped")).hasSize(2).anySatisfy(body -> {
            JsonNode data = JSON.readTree(body).path("data");
            assertThat(data.path("state").asString()).isEqualTo("failed");
            assertThat(data.path("error").asString()).isEqualTo("Модель не відповіла.");
            assertThat(data.path("run").asLong()).isEqualTo(42);
        });
        assertThat(JSON.readTree(byDelivery.get("suggestion.created").getFirst()).path("data").path("author").asString())
                .isEqualTo(reader.nick());
        assertThat(JSON.readTree(byDelivery.get("source.chapters").getFirst()).path("data").path("last").asInt()).isEqualTo(5);

        // Hidden: only the team; 18+: only those who confirmed the age.
        db.update(EDITION).set(EDITION.HIDDEN_AT, OffsetDateTime.now()).where(EDITION.ID.eq(edition)).execute();
        happen(new ChaptersPublished(edition, 1, 1));
        db.update(EDITION).set(EDITION.HIDDEN_AT, (OffsetDateTime) null).set(EDITION.ADULT, true).where(EDITION.ID.eq(edition)).execute();
        happen(new ChaptersPublished(edition, 1, 1));
        assertThat(events(readers)).hasSize(1);
        assertThat(events(team)).hasSize(8);
        db.update(ACCOUNT).set(ACCOUNT.ADULT_CONFIRMED_AT, OffsetDateTime.now()).where(ACCOUNT.ID.eq(accountId(reader))).execute();
        happen(new ChaptersPublished(edition, 1, 1));
        assertThat(events(readers)).hasSize(2);
    }

    @Test
    void addressesInsideAndHiddenTranslationsAreRefused() {
        for (String url : List.of("http://hooks.example/x", "https://127.0.0.1/x", "https://169.254.169.254/latest/meta-data",
                "https://localhost:8443/x", "https://[::1]/x", "https://10.0.0.5/x")) {
            Response refused = reader.browser().post("/api/me/webhooks", json("url", url, "events", List.of("chapter.published")));
            assertThat(refused.status()).as(url).isEqualTo(400);
        }
        assertThat(reader.browser().post("/api/me/webhooks", json("url", stub.url(), "events", List.of("ping"))).status())
                .as("a trial is not an event to subscribe to").isEqualTo(400);
        db.update(EDITION).set(EDITION.HIDDEN_AT, OffsetDateTime.now()).where(EDITION.ID.eq(edition)).execute();
        assertThat(reader.browser().post("/api/me/webhooks",
                json("url", stub.url(), "events", List.of("chapter.published"), "editionIds", List.of(edition))).status())
                .as("a hidden translation of someone else's").isEqualTo(400);
        assertThat(db.fetchCount(WEBHOOK_ENDPOINT, WEBHOOK_ENDPOINT.ACCOUNT_ID.eq(accountId(reader)))).isZero();
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private long endpoint(Person person, List<String> events, List<Long> editions) {
        return read(person.browser().post("/api/me/webhooks", json("url", stub.url(), "events", events, "editionIds", editions)))
                .path("endpoint").path("id").asLong();
    }

    /** What happened, in a transaction of its own, as the modules publish it. */
    private void happen(Object event) {
        transactions.executeWithoutResult(status -> events.publishEvent(event));
    }

    private List<WebhookDeliveryRecord> deliveries(long endpoint) {
        return db.selectFrom(WEBHOOK_DELIVERY).where(WEBHOOK_DELIVERY.ENDPOINT_ID.eq(endpoint)).orderBy(WEBHOOK_DELIVERY.ID).fetch();
    }

    private List<String> events(long endpoint) {
        return deliveries(endpoint).stream().map(WebhookDeliveryRecord::getEvent).toList();
    }

    private void due(long deliveryId) {
        db.update(WEBHOOK_DELIVERY).set(WEBHOOK_DELIVERY.NOT_BEFORE, OffsetDateTime.now().minusSeconds(1))
                .where(WEBHOOK_DELIVERY.ID.eq(deliveryId)).execute();
    }

    private long accountId(Person person) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(person.nick())).fetchSingle().value1();
    }

    private void publish(int number) {
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        Response published = translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                {"title":"Глава %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""".formatted(number));
        assertThat(published.status()).as(published.body()).isBetween(200, 299);
    }

    private static String hmac(String secret, String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }

    record Received(String path, Map<String, String> headers, String body) {

        String header(String name) {
            return headers.get(name.toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** A receiver on 127.0.0.1: records every request and answers with {@link #status}. */
    static final class Stub implements AutoCloseable {

        final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();
        final AtomicInteger status = new AtomicInteger(200);
        volatile String redirect;
        private final HttpServer server;

        Stub() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
            server.createContext("/", exchange -> {
                Map<String, String> headers = new HashMap<>();
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), values.getFirst()));
                String body;
                try (InputStream in = exchange.getRequestBody()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                received.add(new Received(exchange.getRequestURI().getPath(), headers, body));
                if (redirect != null) {
                    exchange.getResponseHeaders().add("Location", redirect);
                }
                exchange.sendResponseHeaders(status.get(), -1);
                exchange.close();
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        String url() {
            return "http://127.0.0.1:" + port() + "/hook";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
