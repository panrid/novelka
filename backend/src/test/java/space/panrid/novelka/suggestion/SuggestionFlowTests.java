package space.panrid.novelka.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

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

@IntegrationTest
class SuggestionFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    Person owner;
    Person reader;
    long edition;
    String slug;

    @BeforeEach
    void aPublishedChapter() {
        owner = Accounts.signedIn(port, mailbox);
        reader = Accounts.signedIn(port, mailbox);
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Правки %s"}""".formatted(owner.nick()))).path("editionId").asLong();
        owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        read(owner.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Ліжко","blocks":[
                  {"id":"b1","type":"paragraph","content":[{"text":"Але це не була розкішна ліжко.","marks":[]}]},
                  {"id":"b2","type":"paragraph","content":[{"text":"Рьо сів на ліжко. Ліжко скрипнуло.","marks":[]}]},
                  {"id":"b3","type":"paragraph","content":[{"text":"Нічого не сталося.","marks":[]}]}]}"""));
        slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
    }

    @Test
    void draftsGoAsABatchAndAcceptedOnesBecomeOneRevision() {
        read(reader.browser().put("/api/suggestions/draft/block", """
                {"editionId":%d,"number":1,"blockId":"b1","note":"рід",
                 "content":[{"text":"Але це не було розкішне ліжко.","marks":[]}]}""".formatted(edition)));
        assertThat(read(reader.browser().get("/api/suggestions/count?editionId=" + edition + "&number=1&find=ліжко"))
                .path("occurrences").asInt()).isEqualTo(2);
        read(reader.browser().put("/api/suggestions/draft/replace", json("editionId", edition, "number", 1,
                "find", "скрипнуло", "replacement", "рипнуло")));

        JsonNode mine = read(reader.browser().get("/api/suggestions/mine?editionId=" + edition + "&number=1"));
        assertThat(mine.path("draftsInEdition").asInt()).isEqualTo(2);
        assertThat(read(owner.browser().get("/api/studio/editions/" + edition + "/suggestions"))).as("drafts stay private").isEmpty();

        assertThat(read(reader.browser().post("/api/suggestions/submit", json("editionId", edition))).path("count").asInt()).isEqualTo(2);
        JsonNode queue = read(owner.browser().get("/api/studio/editions/" + edition + "/suggestions"));
        assertThat(queue).singleElement().satisfies(row -> assertThat(row.path("pending").asInt()).isEqualTo(2));

        JsonNode pending = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1/suggestions"));
        assertThat(pending).hasSize(2).allSatisfy(item -> assertThat(item.path("stale").asBoolean()).isFalse());
        String decisions = "{\"decisions\":[{\"id\":%d,\"accept\":true},{\"id\":%d,\"accept\":true}]}"
                .formatted(pending.get(0).path("id").asLong(), pending.get(1).path("id").asLong());
        JsonNode result = read(owner.browser().post("/api/studio/editions/" + edition + "/chapters/1/suggestions/review", decisions));
        assertThat(result.path("accepted").asInt()).isEqualTo(2);
        JsonNode inbox = space.panrid.novelka.support.Eventually.eventually(
                () -> read(reader.browser().get("/api/notifications")), page -> !page.path("items").isEmpty()).path("items");
        assertThat(inbox).anySatisfy(item -> {
            assertThat(item.path("kind").asString()).isEqualTo("suggestions_reviewed");
            assertThat(item.path("payload").path("accepted").asInt()).isEqualTo(2);
        });

        JsonNode text = read(new Browser(port).get("/api/novels/" + slug + "/chapters/1")).path("blocks");
        assertThat(text.get(0).path("content").get(0).path("text").asString()).isEqualTo("Але це не було розкішне ліжко.");
        assertThat(text.get(1).path("content").get(0).path("text").asString()).isEqualTo("Рьо сів на ліжко. Ліжко рипнуло.");
        JsonNode history = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1/revisions"));
        assertThat(history).as("one revision for the whole batch").hasSize(2);
        assertThat(history.get(0).path("origin").asString()).isEqualTo("suggestion");
        assertThat(read(owner.browser().get("/api/studio/editions/" + edition + "/contributions")))
                .anySatisfy(row -> assertThat(row.path("nick").asString()).isEqualTo(reader.nick()));
        assertThat(read(reader.browser().get("/api/me/suggestions"))).allSatisfy(item ->
                assertThat(item.path("state").asString()).isEqualTo("accepted"));
    }

    @Test
    void aSuggestionForAParagraphChangedMeanwhileIsNotApplied() {
        long base = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1")).path("revisionId").asLong();
        read(reader.browser().put("/api/suggestions/draft/block", """
                {"editionId":%d,"number":1,"blockId":"b3","content":[{"text":"Нічого не трапилося.","marks":[]}]}""".formatted(edition)));
        reader.browser().post("/api/suggestions/submit", json("editionId", edition));
        read(owner.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Ліжко","baseRevisionId":%d,"blocks":[
                  {"id":"b1","type":"paragraph","content":[{"text":"Але це не була розкішна ліжко.","marks":[]}]},
                  {"id":"b3","type":"paragraph","content":[{"text":"Нічого не сталося взагалі.","marks":[]}]}]}""".formatted(base)));

        JsonNode pending = read(owner.browser().get("/api/studio/editions/" + edition + "/chapters/1/suggestions"));
        assertThat(pending).singleElement().satisfies(item -> assertThat(item.path("stale").asBoolean()).isTrue());
        JsonNode result = read(owner.browser().post("/api/studio/editions/" + edition + "/chapters/1/suggestions/review",
                "{\"decisions\":[{\"id\":%d,\"accept\":true}]}".formatted(pending.get(0).path("id").asLong())));
        assertThat(result.path("stale").asInt()).isEqualTo(1);
        assertThat(result.path("revisionId").isNull()).isTrue();
    }

    @Test
    void anEditedDraftReplacesTheOldOneAndCanBeWithdrawn() {
        String draft = """
                {"editionId":%d,"number":1,"blockId":"b3","content":[{"text":"%s","marks":[]}]}""";
        long first = read(reader.browser().put("/api/suggestions/draft/block", draft.formatted(edition, "Варіант один."))).path("id").asLong();
        long second = read(reader.browser().put("/api/suggestions/draft/block", draft.formatted(edition, "Варіант два."))).path("id").asLong();
        assertThat(second).isEqualTo(first);

        assertThat(reader.browser().delete("/api/suggestions/" + first).status()).isEqualTo(204);
        assertThat(reader.browser().post("/api/suggestions/submit", json("editionId", edition)).status()).isEqualTo(400);
    }

    @Test
    void onlyTheTeamReviewsAndNobodyEditsSomeoneElsesSuggestion() {
        long id = read(reader.browser().put("/api/suggestions/draft/replace", json("editionId", edition, "number", 1,
                "find", "Рьо", "replacement", "Ріо"))).path("id").asLong();
        reader.browser().post("/api/suggestions/submit", json("editionId", edition));

        assertThat(reader.browser().get("/api/studio/editions/" + edition + "/chapters/1/suggestions").status()).isEqualTo(403);
        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().delete("/api/suggestions/" + id).status()).isEqualTo(404);
        Response missing = reader.browser().put("/api/suggestions/draft/replace", json("editionId", edition, "number", 1,
                "find", "дракон", "replacement", "змій"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(JSON.readTree(missing.body()).path("detail").asString()).isEqualTo("У цій главі немає такого фрагмента.");
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
