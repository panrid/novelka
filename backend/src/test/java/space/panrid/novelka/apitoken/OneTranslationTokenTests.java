package space.panrid.novelka.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.API_TOKEN;
import static space.panrid.novelka.support.Browser.json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Етап 22.12: a token limited to one translation works with it alone — every request of the
 * Studio's API to another translation is refused before it reaches the Studio, and so is
 * everything the limit does not name.
 */
@IntegrationTest
class OneTranslationTokenTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<String> SCOPES = List.of("read", "studio", "translate", "suggest");
    private static final String LIMITED = "Токен обмежено перекладом";

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    Person translator;
    Work mine;
    Work other;

    /** A translation as the API names it. */
    record Work(String novel, String team, long editionId) {

        String base() {
            return "/api/v1/studio/novels/" + novel + "/translations/" + team;
        }
    }

    @BeforeEach
    void aTranslatorWithTwoTranslations() {
        translator = Accounts.signedIn(port, mailbox);
        mine = translation(translator);
        other = translation(translator);
    }

    @Test
    void theTokenIsMadeForATranslationOfMyTeamAndShowsIt() {
        JsonNode offered = read(translator.browser().get("/api/me/api-tokens")).path("translations");
        assertThat(offered).extracting(node -> node.path("editionId").asLong()).contains(mine.editionId(), other.editionId());

        JsonNode made = read(translator.browser().post("/api/me/api-tokens",
                json("name", "Агент", "scopes", SCOPES, "days", 7, "edition", mine.editionId())));
        JsonNode translation = made.path("token").path("translation");
        assertThat(translation.path("editionId").asLong()).isEqualTo(mine.editionId());
        assertThat(translation.path("novel").asString()).isEqualTo(mine.novel());
        assertThat(translation.path("title").asString()).startsWith("Мати навчання");
        assertThat(translation.path("teamName").asString()).isNotBlank();
        assertThat(read(translator.browser().get("/api/me/api-tokens")).path("tokens"))
                .anySatisfy(token -> assertThat(token.path("translation").path("editionId").asLong()).isEqualTo(mine.editionId()));
        assertThat(db.select(API_TOKEN.EDITION_ID).from(API_TOKEN).where(API_TOKEN.ID.eq(made.path("token").path("id").asLong()))
                .fetchSingle(API_TOKEN.EDITION_ID)).isEqualTo(mine.editionId());

        JsonNode unlimited = read(translator.browser().post("/api/me/api-tokens", json("name", "Усе", "scopes", List.of("read"), "days", 7)));
        assertThat(unlimited.path("token").path("translation").isNull()).isTrue();

        Response library = translator.browser().post("/api/me/api-tokens",
                json("name", "x", "scopes", List.of("studio", "library"), "days", 7, "edition", mine.editionId()));
        assertThat(library.status()).isEqualTo(400);
        assertThat(library.body()).contains("не поєднуються");

        Person stranger = Accounts.signedIn(port, mailbox);
        Response theirs = stranger.browser().post("/api/me/api-tokens",
                json("name", "x", "scopes", List.of("studio"), "days", 7, "edition", mine.editionId()));
        assertThat(theirs.status()).as("never more than its owner has").isEqualTo(403);
        assertThat(theirs.body()).contains("вашої команди");
    }

    @Test
    void everyRequestOfTheStudioToAnotherTranslationIsRefused() {
        String token = token(mine);
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (PathPattern pattern : info.getPathPatternsCondition().getPatterns()) {
                String text = pattern.getPatternString();
                if (!text.startsWith("/api/v1/studio/novels/{novel}/translations/{team}")) {
                    continue;
                }
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    Response refused = bearer(method.name(), address(text, other), token, "{}");
                    assertThat(refused.status()).as(method + " " + text + ": " + refused.body()).isEqualTo(403);
                    assertThat(refused.body()).contains(LIMITED);

                    Response own = bearer(method.name(), address(text, mine), token, "{}");
                    assertThat(own.body()).as(method + " " + text + " in its own translation").doesNotContain(LIMITED);
                    assertThat(own.status()).isNotEqualTo(401);
                    checked++;
                }
            }
        }
        assertThat(checked).as("chapters, originals, runs, glossary, suggestions, pictures").isGreaterThan(25);

        // The same novel in another team's translation is another translation too.
        Response otherTeam = bearer("GET", "/api/v1/studio/novels/" + mine.novel() + "/translations/someone-else/chapters", token, null);
        assertThat(otherTeam.status()).isEqualTo(403);
        assertThat(otherTeam.body()).contains(LIMITED);
    }

    @Test
    void itsOwnTranslationWorksAsWithAnyToken() {
        String token = token(mine);
        Response pasted = bearer("POST", mine.base() + "/originals", token, JSON.writeValueAsString(Map.of("language", "en",
                "chapters", List.of(Map.of("title", "Chapter 1", "text", "Zorian woke up at dawn " + System.nanoTime() + ".")))));
        assertThat(pasted.status()).as(pasted.body()).isEqualTo(201);
        assertThat(read(bearer("GET", mine.base() + "/originals/1", token, null)).path("number").asInt()).isEqualTo(1);
        assertThat(read(bearer("GET", mine.base(), token, null)).path("role").asString()).isEqualTo("owner");
        assertThat(bearer("GET", mine.base() + "/chapters", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", mine.base() + "/glossary", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", mine.base() + "/suggestions", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", mine.base() + "/runs/options", token, null).body()).as("AI is for those with шаги, as always")
                .doesNotContain(LIMITED);

        Response elsewhere = bearer("GET", other.base() + "/originals/1", token, null);
        assertThat(elsewhere.status()).isEqualTo(403);
        assertThat(elsewhere.body()).contains(LIMITED).contains("Мати навчання");
    }

    @Test
    void outsideTheTranslationOnlyWhatNamesNoOtherWorkIsOpen() {
        String token = token(mine);

        JsonNode list = read(bearer("GET", "/api/v1/studio/translations", token, null));
        assertThat(list.path("items")).hasSize(1);
        assertThat(list.path("items").path(0).path("novel").asString()).isEqualTo(mine.novel());
        String unlimited = read(translator.browser().post("/api/me/api-tokens", json("name", "Усе", "scopes", List.of("studio"), "days", 7)))
                .path("secret").asString();
        assertThat(read(bearer("GET", "/api/v1/studio/translations", unlimited, null)).path("items").size()).isGreaterThan(1);

        assertThat(bearer("GET", "/api/v1/studio/pictures", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", "/api/me", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", "/api/v1/catalog", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", "/api/v1/tags", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", "/api/v1/novels/" + mine.novel(), token, null).body()).doesNotContain(LIMITED);
        assertThat(bearer("GET", "/api/v1/studio/novels/" + mine.novel() + "/translations", token, null).body()).doesNotContain(LIMITED);

        for (String[] request : List.of(
                new String[] {"GET", "/api/v1/novels/" + other.novel()},
                new String[] {"GET", "/api/v1/novels/" + other.novel() + "/translations/" + other.team() + "/chapters"},
                new String[] {"POST", "/api/v1/studio/novels"},
                new String[] {"PATCH", "/api/v1/studio/novels/" + other.novel()},
                new String[] {"POST", "/api/v1/studio/novels/" + mine.novel() + "/translations"},
                new String[] {"GET", "/api/v1/studio/somewhere-new"},
                new String[] {"GET", "/api/studio"},
                new String[] {"GET", "/api/studio/editions/" + mine.editionId() + "/chapters"},
                new String[] {"POST", "/api/suggestions"})) {
            Response refused = bearer(request[0], request[1], token, "{}");
            assertThat(refused.status()).as(request[0] + " " + request[1] + ": " + refused.body()).isEqualTo(403);
            assertThat(refused.body()).contains(LIMITED);
        }
    }

    @Test
    void theLimitIsReadOnEveryRequest() {
        String token = token(mine);
        db.update(API_TOKEN).set(API_TOKEN.EDITION_ID, other.editionId()).where(API_TOKEN.SECRET_HASH.eq(sha256(token))).execute();
        assertThat(bearer("GET", other.base() + "/chapters", token, null).status()).isEqualTo(200);
        assertThat(bearer("GET", mine.base() + "/chapters", token, null).status()).as("the limit is read on every request")
                .isEqualTo(403);
    }

    private String token(Work work) {
        return read(translator.browser().post("/api/me/api-tokens", json("name", "Агент", "scopes", SCOPES, "days", 7, "edition",
                work.editionId()))).path("secret").asString();
    }

    private Work translation(Person person) {
        JsonNode created = read(person.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Мати навчання %s","description":[],"tags":[],"language":"en"}"""
                .formatted(UUID.randomUUID().toString().substring(0, 6))));
        String slug = created.path("novelSlug").asString();
        JsonNode edition = read(person.browser().get("/api/studio")).valueStream()
                .filter(node -> node.path("novelSlug").asString().equals(slug)).findFirst().orElseThrow();
        long editionId = read(person.browser().get("/api/me/api-tokens")).path("translations").valueStream()
                .filter(node -> node.path("novel").asString().equals(slug)).findFirst().orElseThrow().path("editionId").asLong();
        return new Work(slug, edition.path("teamHandle").asString(), editionId);
    }

    /** The pattern with the translation's names and a 1 for every other variable. */
    private static String address(String pattern, Work work) {
        List<String> parts = new ArrayList<>();
        for (String part : pattern.split("/")) {
            parts.add(switch (part) {
                case "{novel}" -> work.novel();
                case "{team}" -> work.team();
                default -> part.startsWith("{") ? "1" : part;
            });
        }
        return String.join("/", parts);
    }

    private Response bearer(String method, String path, String token, String body) {
        boolean sends = body != null && !method.equals("GET") && !method.equals("DELETE");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, sends ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        if (sends) {
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
