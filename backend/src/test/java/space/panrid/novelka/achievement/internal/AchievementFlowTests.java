package space.panrid.novelka.achievement.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.support.Browser.json;

import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.Eventually;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Badges and levels for joy only (рішення 33). */
@IntegrationTest
class AchievementFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    Achievements achievements;

    @Autowired
    DSLContext db;

    private long id(Person person) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(person.nick())).fetchSingle(ACCOUNT.ID);
    }

    @Test
    void translatingAndTalkingEarnBadgesALevelAndANotification() {
        Person translator = Accounts.signedIn(port, mailbox);
        Person reader = Accounts.signedIn(port, mailbox);
        long edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Переклад для значків %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        read(translator.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Глава 1","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}"""));
        read(reader.browser().post("/api/editions/" + edition + "/comments", json("body", "Дякую за переклад!")));

        JsonNode before = read(new Browser(port).get("/api/users/" + translator.nick() + "/achievements"));
        assertThat(before.path("badges").findValuesAsString("code")).contains("translator_1", "comment_1");
        assertThat(before.path("badges").findValues("earnedAt")).as("not awarded until counted").allMatch(JsonNode::isNull);

        achievements.sweep(List.of(id(translator), id(reader)));

        JsonNode profile = read(new Browser(port).get("/api/users/" + translator.nick() + "/achievements"));
        assertThat(profile.path("points").asInt()).as("one chapter of their own").isEqualTo(20);
        assertThat(profile.path("level").asInt()).isEqualTo(2);
        assertThat(profile.path("nextLevelPoints").asInt()).isEqualTo(80);
        JsonNode first = profile.path("badges").findParents("code").stream()
                .filter(badge -> badge.path("code").asString().equals("translator_1")).findFirst().orElseThrow();
        assertThat(first.path("earnedAt").isNull()).isFalse();
        assertThat(first.path("title").asString()).isEqualTo("Перекладач");

        JsonNode told = Eventually.eventually(() -> read(translator.browser().get("/api/notifications")),
                page -> page.toString().contains("\"achievement\"")).path("items").path(0);
        assertThat(told.path("kind").asString()).isEqualTo("achievement");
        assertThat(told.path("payload").path("title").asString()).isEqualTo("Перекладач");

        // The level shows next to the nick in comments.
        JsonNode comment = read(new Browser(port).get("/api/editions/" + edition + "/comments")).path("items").path(0);
        assertThat(comment.path("authorNick").asString()).isEqualTo(reader.nick());
        assertThat(comment.path("authorLevel").asInt()).as("3 points: still level 1").isEqualTo(1);

        // Counting again announces nothing new.
        achievements.sweep(List.of(id(translator)));
        assertThat(read(translator.browser().get("/api/notifications")).path("items").size()).isEqualTo(1);
    }

    @Test
    void levelsNeedMoreEachTime() {
        assertThat(Achievements.level(0)).isEqualTo(1);
        assertThat(Achievements.level(19)).isEqualTo(1);
        assertThat(Achievements.level(20)).isEqualTo(2);
        assertThat(Achievements.level(80)).isEqualTo(3);
        assertThat(Achievements.level(179)).isEqualTo(3);
        assertThat(Achievements.pointsFor(4)).isEqualTo(180);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
