package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.Eventually;
import space.panrid.novelka.support.FakeModel;
import space.panrid.novelka.support.FakeSyosetu;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** «Що перекласти» (рішення 32): propose by a link, vote, correct, take to translate. */
@IntegrationTest
class ProposalFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    FakeSyosetu syosetu;

    @Autowired
    FakeModel model;

    String code;

    @BeforeEach
    void aNovelOnSyosetu() {
        Random random = new Random();
        code = "n%04d%c%c".formatted(random.nextInt(10_000), (char) ('a' + random.nextInt(26)), (char) ('a' + random.nextInt(26)));
        // A story of its own: the AI layer reuses an answer to the very same page another test asked for.
        syosetu.add(code, new FakeSyosetu.Novel("灯台守の夜", "桜ゆき", "あらすじ。" + code, 4));
        model.reset();
    }

    private String url() {
        return "https://ncode.syosetu.com/" + code + "/";
    }

    private JsonNode find(Browser browser, String state, long id) {
        for (JsonNode item : read(browser.get("/api/proposals?sort=new&state=" + state)).path("items")) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError("no proposal " + id);
    }

    @Test
    void aProposedNovelIsVotedForCorrectedAndTakenToTranslate() {
        Person proposer = Accounts.signedIn(port, mailbox);
        Person voter = Accounts.signedIn(port, mailbox);
        Person translator = Accounts.signedIn(port, mailbox);
        Browser guest = new Browser(port);

        Response first = proposer.browser().post("/api/proposals", json("url", url()));
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        long id = read(first).path("id").asLong();
        assertThat(model.calls).as("the page is translated once, at the site's cost").containsExactly("novel");

        JsonNode seen = find(guest, "open", id);
        assertThat(seen.toString()).as("only Ukrainian on the site").doesNotContain("灯台").contains("Ліхтарник із туману");
        assertThat(seen.path("votes").asInt()).as("the proposer votes for it").isEqualTo(1);
        assertThat(seen.path("voted").asBoolean()).isFalse();
        assertThat(seen.path("chapters").asInt()).isEqualTo(4);
        assertThat(seen.path("proposedBy").asString()).isEqualTo(proposer.nick());

        // The same link again is a vote for the same proposal, not a new model call.
        JsonNode again = read(voter.browser().post("/api/proposals", json("url", url())));
        assertThat(again.path("id").asLong()).isEqualTo(id);
        assertThat(again.path("created").asBoolean()).isFalse();
        assertThat(model.calls).hasSize(1);
        assertThat(find(voter.browser(), "open", id).path("votes").asInt()).isEqualTo(2);
        assertThat(voter.browser().delete("/api/proposals/" + id + "/vote").status()).isEqualTo(200);
        assertThat(find(voter.browser(), "open", id).path("voted").asBoolean()).isFalse();
        assertThat(voter.browser().post("/api/proposals/" + id + "/vote", "{}").status()).isEqualTo(200);
        assertThat(guest.post("/api/proposals/" + id + "/vote", "{}").status()).isEqualTo(401);

        // Only the proposer corrects what the model wrote.
        String change = json("title", "Доглядач маяка", "author", "Сакура Юкі", "description", "Перший абзац.\nДругий абзац.");
        assertThat(voter.browser().put("/api/proposals/" + id, change).status()).isEqualTo(403);
        assertThat(proposer.browser().put("/api/proposals/" + id, change).status()).isEqualTo(200);
        JsonNode corrected = find(proposer.browser(), "open", id);
        assertThat(corrected.path("title").asString()).isEqualTo("Доглядач маяка");
        assertThat(corrected.path("description").size()).isEqualTo(2);
        assertThat(corrected.path("mine").asBoolean()).isTrue();

        // «Беру перекладати»: the edition gets the corrected page, the model is not asked again.
        JsonNode taken = read(translator.browser().post("/api/proposals/" + id + "/take", json("team", "")));
        long edition = taken.path("editionId").asLong();
        assertThat(model.calls).hasSize(1);
        JsonNode about = read(translator.browser().get("/api/studio/editions/" + edition));
        assertThat(about.path("title").asString()).isEqualTo("Доглядач маяка");
        assertThat(read(translator.browser().get("/api/studio/editions/" + edition + "/autotranslate")).path("sourceChapters").asInt())
                .isEqualTo(4);

        JsonNode done = find(guest, "taken", id);
        assertThat(done.path("taken").path("novelSlug").asString()).isEqualTo(taken.path("novelSlug").asString());
        assertThat(done.path("taken").path("teamHandle").asString()).isEqualTo(translator.nick());
        assertThat(voter.browser().post("/api/proposals/" + id + "/vote", "{}").status()).isEqualTo(409);
        assertThat(translator.browser().post("/api/proposals/" + id + "/take", json("team", "")).status()).isEqualTo(409);

        for (Person told : java.util.List.of(proposer, voter)) {
            JsonNode inbox = Eventually.eventually(() -> read(told.browser().get("/api/notifications")),
                    page -> page.toString().contains("proposal_taken")).path("items").path(0);
            assertThat(inbox.path("payload").path("title").asString()).isEqualTo("Доглядач маяка");
            assertThat(inbox.path("payload").path("slug").asString()).isEqualTo(taken.path("novelSlug").asString());
        }

        // Proposing it again finds the taken proposal: no new model call.
        assertThat(read(voter.browser().post("/api/proposals", json("url", url()))).path("created").asBoolean()).isFalse();
        assertThat(model.calls).hasSize(1);
    }

    @Test
    void theProposerMayWithdrawItAndOthersMayNot() {
        Person proposer = Accounts.signedIn(port, mailbox);
        Person other = Accounts.signedIn(port, mailbox);
        long id = read(proposer.browser().post("/api/proposals", json("url", url()))).path("id").asLong();
        assertThat(other.browser().delete("/api/proposals/" + id).status()).isEqualTo(403);
        assertThat(proposer.browser().delete("/api/proposals/" + id).status()).isEqualTo(200);
        JsonNode open = read(new Browser(port).get("/api/proposals?sort=new"));
        assertThat(open.toString()).doesNotContain("\"id\":" + id + ",");
        assertThat(other.browser().post("/api/proposals", json("url", "https://example.com/novel")).status()).isEqualTo(400);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
