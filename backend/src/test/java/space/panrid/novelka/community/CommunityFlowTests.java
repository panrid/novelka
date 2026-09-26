package space.panrid.novelka.community;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.support.Browser.json;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import static space.panrid.novelka.support.Eventually.eventually;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CommunityFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person translator;
    Person reader;
    long edition;
    String slug;
    String team;

    @BeforeEach
    void aTranslationWithAChapter() {
        translator = Accounts.signedIn(port, mailbox);
        reader = Accounts.signedIn(port, mailbox);
        edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Обговорюваний переклад %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        publish(1);
        JsonNode about = read(translator.browser().get("/api/studio/editions/" + edition));
        slug = about.path("novelSlug").asString();
        team = about.path("teamHandle").asString();
    }

    private void publish(int number) {
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                {"title":"Глава %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""".formatted(number));
    }

    private String comments() {
        return "/api/editions/" + edition + "/comments";
    }

    private JsonNode inbox(Person person) {
        return read(person.browser().get("/api/notifications"));
    }

    /** The inbox once it has at least {@code count} rows (listeners run after the request). */
    private JsonNode inbox(Person person, int count) {
        return eventually(() -> inbox(person), page -> page.path("items").size() >= count);
    }

    @Test
    void readersTalkAboutAChapterAndHearRepliesAndMentions() {
        long question = read(reader.browser().post(comments(), json("chapter", 1,
                "body", "@" + translator.nick() + " а **чому** тут так? ||вбивця — дворецький||\n>#b1 Рьо зліз з ліжка"))).path("id").asLong();
        JsonNode mention = inbox(translator, 1).path("items").path(0);
        assertThat(mention.path("kind").asString()).isEqualTo("mention");
        assertThat(mention.path("payload").path("actorNick").asString()).isEqualTo(reader.nick());
        assertThat(mention.path("payload").path("chapterNumber").asInt()).isEqualTo(1);
        assertThat(mention.path("payload").path("excerpt").asString()).as("neither a spoiler nor a quote shows in a notification")
                .isEqualTo("@" + translator.nick() + " а чому тут так? (спойлер) (цитата)");

        read(translator.browser().post(comments(), json("chapter", 1, "body", "Бо так в оригіналі.", "replyTo", question)));
        JsonNode reply = inbox(reader, 1).path("items").path(0);
        assertThat(reply.path("kind").asString()).isEqualTo("reply");
        assertThat(inbox(reader).path("unread").asInt()).isEqualTo(1);

        JsonNode thread = read(reader.browser().get(comments() + "?chapter=1"));
        assertThat(thread.path("total").asInt()).isEqualTo(2);
        JsonNode root = thread.path("items").path(0);
        assertThat(root.path("body").asString()).as("mentions shown with today's nick")
                .isEqualTo("@" + translator.nick() + " а **чому** тут так? ||вбивця — дворецький||\n>#b1 Рьо зліз з ліжка");
        assertThat(root.path("mine").asBoolean()).isTrue();
        assertThat(root.path("replies")).singleElement()
                .satisfies(answer -> assertThat(answer.path("authorNick").asString()).isEqualTo(translator.nick()));
        assertThat(read(reader.browser().get(comments())).path("total").asInt()).as("the translation's own talk is separate").isZero();

        Person stranger = Accounts.signedIn(port, mailbox);
        read(stranger.browser().post(comments(), json("body", "$" + team + " коли продовження?")));
        assertThat(inbox(translator, 2).path("items").path(0).path("kind").asString()).isEqualTo("team_mention");

        read(reader.browser().post("/api/notifications/read", "{}"));
        assertThat(read(reader.browser().get("/api/notifications/unread")).path("unread").asInt()).isZero();
    }

    @Test
    void anOpenTabHearsAboutNewNotificationsAtOnce() throws InterruptedException {
        var events = reader.browser().stream("/api/events");
        assertThat(nextEvent(events)).isEqualTo("hello");
        long question = read(reader.browser().post(comments(), json("body", "Питання"))).path("id").asLong();
        read(translator.browser().post(comments(), json("body", "Відповідь", "replyTo", question)));
        assertThat(nextEvent(events)).isEqualTo("notifications");
        assertThat(new Browser(port).get("/api/events").status()).as("only for signed-in people").isEqualTo(401);
    }

    private static String nextEvent(java.util.concurrent.BlockingQueue<String> lines) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String line = lines.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith("event:")) {
                return line.substring(6).strip();
            }
        }
        return null;
    }

    @Test
    void typingAnAtSuggestsPeopleAndADollarSuggestsTeams() {
        String start = translator.nick().substring(0, 7);
        JsonNode people = read(reader.browser().get("/api/mentions?kind=@&q=" + start));
        assertThat(people).extracting(p -> p.path("name").asString()).contains(translator.nick());
        JsonNode teams = read(reader.browser().get("/api/mentions?kind=$&q=" + team.substring(0, 7)));
        assertThat(teams).extracting(t -> t.path("name").asString()).contains(team);
        assertThat(read(reader.browser().get("/api/mentions?kind=@&q=%25"))).as("a wildcard is just a character").isEmpty();
    }

    @Test
    void votesEditsAndRemoval() {
        long first = read(reader.browser().post(comments(), json("body", "Дякую за переклад!"))).path("id").asLong();
        long second = read(translator.browser().post(comments(), json("body", "Будь ласка."))).path("id").asLong();
        read(translator.browser().post(comments(), json("body", "Відповідь", "replyTo", first)));

        assertThat(read(translator.browser().put("/api/comments/" + first + "/vote", json("value", 1))).path("score").asInt()).isEqualTo(1);
        assertThat(reader.browser().put("/api/comments/" + first + "/vote", json("value", 1)).status()).as("not your own").isEqualTo(400);
        assertThat(read(reader.browser().get(comments() + "?sort=top")).path("items").path(0).path("id").asLong()).isEqualTo(first);

        assertThat(translator.browser().patch("/api/comments/" + first, json("body", "Чуже")).status()).isEqualTo(403);
        read(reader.browser().patch("/api/comments/" + first, json("body", "Дуже дякую!")));
        reader.browser().delete("/api/comments/" + first);
        translator.browser().delete("/api/comments/" + second);

        JsonNode thread = read(new Browser(port).get(comments()));
        assertThat(thread.path("items")).as("a removed comment stays only to hold its replies").singleElement().satisfies(stub -> {
            assertThat(stub.path("removed").asString()).isEqualTo("deleted");
            assertThat(stub.path("body").asString()).isEmpty();
            assertThat(stub.path("authorNick").isNull()).isTrue();
            assertThat(stub.path("replies")).hasSize(1);
        });

        Person moderator = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "moderator").where(ACCOUNT.NICK.eq(moderator.nick())).execute();
        long rude = read(reader.browser().post(comments(), json("body", "Грубощі"))).path("id").asLong();
        assertThat(translator.browser().delete("/api/comments/" + rude).status()).isEqualTo(403);
        assertThat(moderator.browser().delete("/api/comments/" + rude + "?reason=образи").status()).isEqualTo(200);
        assertThat(read(new Browser(port).get(comments())).path("items").toString()).doesNotContain("Грубощі");

        assertThat(reader.browser().post("/api/reports", json("target", "comment", "targetId", second, "reason", "спам")).status())
                .isEqualTo(201);
        assertThat(reader.browser().post("/api/reports", json("target", "comment", "targetId", second, "reason", "спам")).status())
                .as("repeating is harmless").isEqualTo(201);
    }

    @Test
    void theSiteChatMentionsAndRatings() {
        read(reader.browser().post("/api/chat", json("body", "Привіт, @" + translator.nick() + "!")));
        JsonNode lines = read(new Browser(port).get("/api/chat"));
        assertThat(lines.get(lines.size() - 1).path("body").asString()).isEqualTo("Привіт, @" + translator.nick() + "!");
        assertThat(new Browser(port).post("/api/chat", json("body", "гість")).status()).isEqualTo(401);
        read(translator.browser().post("/api/chat", json("body", "Привіт!", "replyTo", lines.get(lines.size() - 1).path("id").asLong())));
        assertThat(inbox(translator, 1).path("items").path(0).path("payload").path("where").asString()).isEqualTo("chat");

        read(reader.browser().put("/api/editions/" + edition + "/rating", json("score", 4)));
        JsonNode rating = read(translator.browser().put("/api/editions/" + edition + "/rating", json("score", 2)));
        assertThat(rating.path("average").asDouble()).isEqualTo(3.0);
        assertThat(rating.path("count").asInt()).isEqualTo(2);
        assertThat(reader.browser().put("/api/editions/" + edition + "/rating", json("score", 6)).status()).isEqualTo(400);
        JsonNode page = read(reader.browser().get("/api/novels/" + slug));
        assertThat(page.path("edition").path("rating").asDouble()).isEqualTo(3.0);
        assertThat(page.path("viewer").path("myRating").asInt()).isEqualTo(4);
    }

    @Test
    void newChaptersReachReadersWhoKeepTheTranslationInOneRow() {
        assertThat(reader.browser().put("/api/library/" + edition, json("list", "reading")).status()).isEqualTo(204);
        publish(2);
        publish(3);
        JsonNode items = eventually(() -> inbox(reader),
                page -> page.path("items").path(0).path("payload").path("last").asInt() == 3).path("items");
        assertThat(items).as("one row for a run of chapters").singleElement().satisfies(item -> {
            assertThat(item.path("kind").asString()).isEqualTo("new_chapters");
            assertThat(item.path("payload").path("first").asInt()).isEqualTo(2);
            assertThat(item.path("payload").path("last").asInt()).isEqualTo(3);
            assertThat(item.path("payload").path("slug").asString()).isEqualTo(slug);
            assertThat(item.path("payload").path("firstLabel").asString()).as("the numbers readers see").isEqualTo("2");
            assertThat(item.path("payload").path("lastLabel").asString()).isEqualTo("3");
            assertThat(item.path("payload").has("chapterTitle")).as("no one chapter's name for a run").isFalse();
        });
        read(reader.browser().post("/api/notifications/read", json("upTo", items.path(0).path("id").asLong())));
        publish(4);
        JsonNode single = inbox(reader, 2).path("items");
        assertThat(single).hasSize(2);
        assertThat(single.path(0).path("payload").path("chapterTitle").asString()).as("a single chapter is named").isNotBlank();
        assertThat(inbox(translator).path("items")).as("the translator is not told about their own chapters").isEmpty();
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
