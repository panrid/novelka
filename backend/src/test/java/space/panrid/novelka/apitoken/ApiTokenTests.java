package space.panrid.novelka.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.API_TOKEN;
import static space.panrid.novelka.jooq.Tables.API_TOKEN_WRITE;
import static space.panrid.novelka.jooq.Tables.AUDIT_LOG;
import static space.panrid.novelka.support.Browser.json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Етап 22.1: access tokens — made, shown once, kept as a hash, narrowed by scopes, revoked. */
@IntegrationTest
class ApiTokenTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person reader;

    @BeforeEach
    void aReaderSignsIn() {
        reader = Accounts.signedIn(port, mailbox);
    }

    @Test
    void theTokenIsShownOnceAndOnlyItsHashIsKept() {
        JsonNode made = read(reader.browser().post("/api/me/api-tokens", json("name", "Моя читалка", "scopes", List.of("read"), "days", 30)));
        String secret = made.path("secret").asString();
        assertThat(secret).startsWith("nvk_").hasSizeGreaterThan(40);
        assertThat(made.path("token").path("name").asString()).isEqualTo("Моя читалка");
        assertThat(made.path("token").path("state").asString()).isEqualTo("active");
        assertThat(made.path("token").path("expiresAt").asString()).isNotBlank();

        Response list = reader.browser().get("/api/me/api-tokens");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).doesNotContain(secret).contains("Моя читалка");
        assertThat(JSON.readTree(list.body()).path("offered")).extracting(JsonNode::asString).doesNotContain("admin").contains("read", "studio");

        var row = db.selectFrom(API_TOKEN).where(API_TOKEN.ID.eq(made.path("token").path("id").asLong())).fetchSingle();
        assertThat(row.getSecretHash()).isEqualTo(sha256(secret)).isNotEqualTo(secret);
        assertThat(row.getHint()).endsWith("…").hasSizeLessThan(16);
        assertThat(secret).startsWith(row.getHint().substring(0, row.getHint().length() - 1));

        Response me = bearer("GET", "/api/me", secret, null);
        assertThat(me.status()).as(me.body()).isEqualTo(200);
        assertThat(JSON.readTree(me.body()).path("nick").asString()).isEqualTo(reader.nick());
        var used = db.selectFrom(API_TOKEN).where(API_TOKEN.ID.eq(row.getId())).fetchSingle();
        assertThat(used.getLastUsedAt()).isNotNull();
        assertThat(used.getLastUsedIp()).isNotBlank();
    }

    @Test
    void aRevokedExpiredOrWrongTokenSignsNobodyIn() {
        String revoked = token(reader, List.of("read"));
        long revokedId = idOf(revoked);
        assertThat(bearer("GET", "/api/me", revoked, null).status()).isEqualTo(200);
        assertThat(reader.browser().delete("/api/me/api-tokens/" + revokedId).status()).isEqualTo(204);
        Response refused = bearer("GET", "/api/me", revoked, null);
        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.body()).contains("Токен недійсний");
        assertThat(JSON.readTree(reader.browser().get("/api/me/api-tokens").body()).path("tokens"))
                .anySatisfy(token -> {
                    assertThat(token.path("id").asLong()).isEqualTo(revokedId);
                    assertThat(token.path("state").asString()).isEqualTo("revoked");
                });

        String expired = token(reader, List.of("read"));
        db.update(API_TOKEN).set(API_TOKEN.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1)).where(API_TOKEN.ID.eq(idOf(expired))).execute();
        assertThat(bearer("GET", "/api/me", expired, null).status()).isEqualTo(401);

        assertThat(bearer("GET", "/api/me", "nvk_" + UUID.randomUUID(), null).status()).isEqualTo(401);

        // Someone else cannot revoke it.
        String mine = token(reader, List.of("read"));
        Person other = Accounts.signedIn(port, mailbox);
        assertThat(other.browser().delete("/api/me/api-tokens/" + idOf(mine)).status()).isEqualTo(404);
        assertThat(bearer("GET", "/api/me", mine, null).status()).isEqualTo(200);
    }

    @Test
    void choicesAreChecked() {
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "", "scopes", List.of("read"), "days", 30)).status()).isEqualTo(400);
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "x", "scopes", List.of(), "days", 30)).status()).isEqualTo(400);
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "x", "scopes", List.of("everything"), "days", 30)).status()).isEqualTo(400);
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "x", "scopes", List.of("read"), "days", 5000)).status()).isEqualTo(400);
        // «admin» is for site roles only.
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "x", "scopes", List.of("admin"), "days", 30)).status()).isEqualTo(403);
    }

    @Test
    void scopesNarrowWhatATokenReachesAndWritesNeedNoCsrfButAreJournaled() {
        long editionId = anEdition();
        String reading = token(reader, List.of("read"));
        Response refused = bearer("GET", "/api/library", reading, null);
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body()).contains("library");
        assertThat(bearer("PUT", "/api/library/" + editionId, reading, json("list", "reading")).status()).isEqualTo(403);

        String library = token(reader, List.of("library"));
        // No X-XSRF-TOKEN and no cookie: a token needs no CSRF.
        Response put = bearer("PUT", "/api/library/" + editionId, library, json("list", "reading"));
        assertThat(put.status()).as(put.body()).isEqualTo(204);
        assertThat(read(bearer("GET", "/api/library", library, null)).path("items"))
                .anySatisfy(item -> assertThat(item.path("card").path("editionId").asLong()).isEqualTo(editionId));
        // «library» alone does not read the public pages.
        assertThat(bearer("GET", "/api/home", library, null).status()).isEqualTo(403);

        var journal = db.selectFrom(API_TOKEN_WRITE).where(API_TOKEN_WRITE.TOKEN_ID.eq(idOf(library))).fetch();
        assertThat(journal).singleElement().satisfies(row -> {
            assertThat(row.getMethod()).isEqualTo("PUT");
            assertThat(row.getPath()).isEqualTo("/api/library/" + editionId);
            assertThat(row.getStatus()).isEqualTo(204);
        });
        // Reads are not journaled, refusals never reach the action.
        assertThat(db.fetchCount(API_TOKEN_WRITE, API_TOKEN_WRITE.TOKEN_ID.eq(idOf(reading)))).isZero();
    }

    @Test
    void cookieSessionsStillNeedCsrf() {
        Response forged = reader.browser().postWithoutCsrf("/api/me/api-tokens", json("name", "x", "scopes", List.of("read"), "days", 30));
        assertThat(forged.status()).isEqualTo(403);
        assertThat(forged.body()).contains("Сторінка застаріла");
        assertThat(db.fetchCount(API_TOKEN, API_TOKEN.ACCOUNT_ID.eq(accountId(reader)))).isZero();
        assertThat(reader.browser().post("/api/me/api-tokens", json("name", "x", "scopes", List.of("read"), "days", 30)).status())
                .isEqualTo(201);
    }

    @Test
    void sessionOnlyActionsRefuseEveryToken() {
        String all = token(reader, List.of("read", "library", "suggest", "studio", "translate"));
        for (String[] call : List.of(new String[] {"GET", "/api/me/api-tokens"}, new String[] {"POST", "/api/me/api-tokens"},
                new String[] {"DELETE", "/api/me/api-tokens/" + idOf(all)}, new String[] {"POST", "/api/me/password"},
                new String[] {"POST", "/api/me/email"}, new String[] {"PATCH", "/api/me"}, new String[] {"POST", "/api/auth/logout"},
                new String[] {"GET", "/api/admin/api-tokens"})) {
            Response response = bearer(call[0], call[1], all, json("name", "x", "scopes", List.of("read"), "days", 30,
                    "currentPassword", Accounts.PASSWORD, "newPassword", "зовсім новий пароль 7"));
            assertThat(response.status()).as(call[0] + " " + call[1] + ": " + response.body()).isEqualTo(403);
        }
        assertThat(db.fetchCount(API_TOKEN, API_TOKEN.ACCOUNT_ID.eq(accountId(reader)))).isEqualTo(1);
        assertThat(bearer("GET", "/api/me", all, null).status()).isEqualTo(200);
    }

    @Test
    void aTokenNeverGivesMoreThanItsOwnerHas() {
        long editionId = anEdition();
        String studio = token(reader, List.of("studio"));
        // Not in that team: the Studio's own check refuses, as it would on the site.
        Response other = bearer("GET", "/api/studio/editions/" + editionId, studio, null);
        assertThat(other.status()).isEqualTo(403);
        assertThat(other.body()).contains("Це вам недоступно");

        Person moderator = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "moderator").where(ACCOUNT.NICK.eq(moderator.nick())).execute();
        assertThat(JSON.readTree(moderator.browser().get("/api/me/api-tokens").body()).path("offered"))
                .extracting(JsonNode::asString).contains("admin");
        String admin = token(moderator, List.of("admin"));
        assertThat(bearer("GET", "/api/admin/overview", admin, null).status()).isEqualTo(200);
        // A moderator is not the owner: the owner's pages stay closed with any token.
        assertThat(bearer("GET", "/api/admin/audit", admin, null).status()).isEqualTo(403);
        // Demoted: the token follows the person's role at once.
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.NICK.eq(moderator.nick())).execute();
        assertThat(bearer("GET", "/api/admin/overview", admin, null).status()).isEqualTo(403);
    }

    @Test
    void theSiteOwnerSeesEveryTokenAndRevokesAny() {
        Person owner = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "owner").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        String secret = token(reader, List.of("read"));
        long id = idOf(secret);

        assertThat(reader.browser().get("/api/admin/api-tokens").status()).isEqualTo(403);
        Response all = owner.browser().get("/api/admin/api-tokens");
        assertThat(all.status()).isEqualTo(200);
        assertThat(all.body()).doesNotContain(secret);
        assertThat(JSON.readTree(all.body())).anySatisfy(token -> {
            assertThat(token.path("id").asLong()).isEqualTo(id);
            assertThat(token.path("owner").asString()).isEqualTo(reader.nick());
        });

        assertThat(owner.browser().delete("/api/admin/api-tokens/" + id).status()).isEqualTo(204);
        assertThat(bearer("GET", "/api/me", secret, null).status()).isEqualTo(401);
        assertThat(db.fetchExists(AUDIT_LOG, AUDIT_LOG.ACTION.eq("api_token_revoked").and(AUDIT_LOG.TARGET_ID.eq(id)))).isTrue();
    }

    // ---- helpers ------------------------------------------------------------------------------

    private long anEdition() {
        Person admin = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(admin.nick())).execute();
        String title = "Токени " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode imported = read(admin.browser().upload("/api/admin/novels",
                Map.of("title", title, "author", "Автор", "description", "Опис.", "tags", "Фентезі", "kind", "machine", "adult", "false"),
                "file", "n.md", "text/markdown", "# Перша\nТекст.\n".getBytes(StandardCharsets.UTF_8)));
        return read(new Browser(port).get("/api/novels/" + imported.path("slug").asString())).path("edition").path("editionId").asLong();
    }

    private static String token(Person person, List<String> scopes) {
        return read(person.browser().post("/api/me/api-tokens", json("name", "Агент", "scopes", scopes, "days", 7)))
                .path("secret").asString();
    }

    private long idOf(String secret) {
        return db.select(API_TOKEN.ID).from(API_TOKEN).where(API_TOKEN.SECRET_HASH.eq(sha256(secret))).fetchSingle(API_TOKEN.ID);
    }

    private long accountId(Person person) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(person.nick())).fetchSingle(ACCOUNT.ID);
    }

    private Response bearer(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, body == null || method.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null && !method.equals("GET")) {
            request.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
