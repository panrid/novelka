package space.panrid.novelka.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.SITE_SETTING;
import static space.panrid.novelka.jooq.Tables.TAKEOVER_REQUEST;
import static space.panrid.novelka.support.Browser.json;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.AfterEach;
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
class RelayTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person owner;
    Person successor;
    long edition;
    String slug;

    @BeforeEach
    void aTranslationWithTwoChapters() {
        owner = Accounts.signedIn(port, mailbox);
        successor = Accounts.signedIn(port, mailbox);
        edition = read(owner.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Покинутий переклад %s"}""".formatted(owner.nick()))).path("editionId").asLong();
        for (int n = 1; n <= 2; n++) {
            owner.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
            owner.browser().post("/api/studio/editions/" + edition + "/chapters/" + n + "/publish", """
                    {"title":"Глава %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""".formatted(n));
        }
        slug = read(owner.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
    }

    @AfterEach
    void resetSettings() {
        db.deleteFrom(SITE_SETTING).execute();
    }

    @Test
    void anotherTeamTranslatesTheNovelItselfFromTheStartOrFromTheNextChapterAndReadersChoose() {
        long fresh = read(successor.browser().post("/api/novels/" + slug + "/own-translation", "{}")).path("editionId").asLong();
        assertThat(read(successor.browser().post("/api/studio/editions/" + fresh + "/chapters", "{}")).path("number").asInt())
                .as("from scratch: chapter 1").isEqualTo(1);
        assertThat(successor.browser().post("/api/novels/" + slug + "/own-translation", "{}").status())
                .as("one translation per team").isEqualTo(409);

        Person third = Accounts.signedIn(port, mailbox);
        long after = read(third.browser().post("/api/novels/" + slug + "/own-translation", json("after", edition))).path("editionId").asLong();
        assertThat(read(third.browser().post("/api/studio/editions/" + after + "/chapters", "{}")).path("number").asInt())
                .as("going on from the owner's last chapter, no permission asked").isEqualTo(3);
        third.browser().post("/api/studio/editions/" + after + "/chapters/3/publish", """
                {"title":"Глава 3","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Далі.","marks":[]}]}]}""");

        // Readers pick the team; hiding one translation leaves the other.
        JsonNode novel = read(new Browser(port).get("/api/novels/" + slug));
        assertThat(novel.path("editions").size()).isEqualTo(2);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "admin").where(ACCOUNT.NICK.eq(owner.nick())).execute();
        assertThat(owner.browser().post("/api/admin/hidden/edition/" + after, json("reason", "перевірка")).status()).isBetween(200, 204);
        JsonNode left = read(new Browser(port).get("/api/novels/" + slug));
        assertThat(left.path("editions").size()).isEqualTo(1);
        assertThat(left.path("edition").path("editionId").asLong()).isEqualTo(edition);
    }

    @Test
    void anAbandonedTranslationContinuesWithAnotherTeamFromTheNextChapter() {
        assertThat(successor.browser().post("/api/editions/" + edition + "/continue", json("kind", "machine")).status())
                .as("not free yet").isEqualTo(403);

        owner.browser().patch("/api/studio/editions/" + edition, json("status", "abandoned"));
        JsonNode relay = read(new Browser(port).get("/api/novels/" + slug)).path("relay");
        assertThat(relay.path("free").asBoolean()).isTrue();
        assertThat(relay.path("reason").asString()).isEqualTo("abandoned");
        assertThat(relay.path("lastNumber").asInt()).isEqualTo(2);

        long next = read(successor.browser().post("/api/editions/" + edition + "/continue", json("kind", "machine"))).path("editionId").asLong();
        int number = read(successor.browser().post("/api/studio/editions/" + next + "/chapters", "{}")).path("number").asInt();
        assertThat(number).as("numbering continues after the old edition").isEqualTo(3);
        successor.browser().post("/api/studio/editions/" + next + "/chapters/3/publish", """
                {"title":"Глава 3","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Далі.","marks":[]}]}]}""");

        JsonNode last = read(new Browser(port).get("/api/novels/" + slug + "/chapters/2?t=" + owner.nick()));
        assertThat(last.path("next").isNull()).isTrue();
        assertThat(last.path("continuation").path("teamHandle").asString()).isEqualTo(successor.nick());
        assertThat(last.path("continuation").path("firstNumber").asInt()).isEqualTo(3);
        assertThat(read(new Browser(port).get("/api/novels/" + slug + "/chapters/3?t=" + successor.nick())).path("title").asString())
                .isEqualTo("Глава 3");
    }

    @Test
    void theOwnerMayAllowARequestEarly() {
        assertThat(successor.browser().post("/api/editions/" + edition + "/takeover-requests",
                json("message", "Хочемо продовжити машинним перекладом.")).status()).isEqualTo(201);
        assertThat(mailbox.to(owner.email())).anySatisfy(mail -> {
            assertThat(mail.subject()).isEqualTo("Хочуть продовжити ваш переклад на Новелці");
            assertThat(mail.text()).as("the letter says what they wrote").contains("«Хочемо продовжити машинним перекладом.»");
        });
        JsonNode told = space.panrid.novelka.support.Eventually.eventually(() -> read(owner.browser().get("/api/notifications")),
                page -> page.toString().contains("takeover_request")).path("items").path(0);
        assertThat(told.path("kind").asString()).as("the owner hears on the site too").isEqualTo("takeover_request");
        assertThat(told.path("payload").path("excerpt").asString()).isEqualTo("Хочемо продовжити машинним перекладом.");
        assertThat(told.path("payload").path("actorNick").asString()).isEqualTo(successor.nick());
        assertThat(read(successor.browser().get("/api/novels/" + slug)).path("viewer").path("relayAsked").asBoolean())
                .as("the page remembers the request instead of offering it again").isTrue();
        assertThat(successor.browser().post("/api/editions/" + edition + "/takeover-requests", json("message", "Ще раз")).status())
                .isEqualTo(409);

        JsonNode requests = read(owner.browser().get("/api/studio/editions/" + edition + "/takeover-requests"));
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path("teamHandle").asString()).isEqualTo(successor.nick());
            assertThat(request.path("state").asString()).isEqualTo("open");
        });
        assertThat(successor.browser().get("/api/studio/editions/" + edition + "/takeover-requests").status()).isEqualTo(403);

        owner.browser().post("/api/studio/editions/" + edition + "/takeover-requests/" + requests.get(0).path("id").asLong(),
                json("grant", true));
        assertThat(successor.browser().post("/api/editions/" + edition + "/continue", json("kind", "human")).status()).isEqualTo(201);
        assertThat(space.panrid.novelka.support.Eventually.eventually(() -> read(successor.browser().get("/api/notifications")),
                page -> page.toString().contains("takeover_answered")).path("items").path(0).path("payload").path("granted").asBoolean())
                .as("and the one who asked hears the answer").isTrue();
    }

    @Test
    void itOpensUpAfterMonthsOfTheOwnersAbsenceOrAnUnansweredRequest() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        db.update(ACCOUNT).set(ACCOUNT.LAST_SEEN_AT, now.minusMonths(4)).where(ACCOUNT.NICK.eq(owner.nick())).execute();
        assertThat(relay().path("reason").asString()).isEqualTo("inactive");

        db.insertInto(SITE_SETTING).set(SITE_SETTING.KEY, "relay.inactive_months").set(SITE_SETTING.VALUE, JSONB.valueOf("6")).execute();
        assertThat(relay().path("free").asBoolean()).as("the owner's setting moves the line").isFalse();

        db.update(EDITION).set(EDITION.LAST_PUBLISHED_AT, now.minusMonths(7)).where(EDITION.ID.eq(edition)).execute();
        successor.browser().post("/api/editions/" + edition + "/takeover-requests", json("message", "Можна?"));
        assertThat(relay().path("free").asBoolean()).as("the owner has 14 days to answer").isFalse();
        db.update(TAKEOVER_REQUEST).set(TAKEOVER_REQUEST.CREATED_AT, now.minusDays(15)).where(TAKEOVER_REQUEST.EDITION_ID.eq(edition)).execute();
        assertThat(relay().path("reason").asString()).isEqualTo("unanswered");
    }

    @Test
    void originalWorksAreNeverFreeForOthers() {
        long work = read(owner.browser().post("/api/studio/editions", """
                {"kind":"original","title":"Мій твір %s"}""".formatted(owner.nick()))).path("editionId").asLong();
        owner.browser().post("/api/studio/editions/" + work + "/chapters", "{}");
        owner.browser().post("/api/studio/editions/" + work + "/chapters/1/publish", """
                {"title":"Початок","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""");
        owner.browser().patch("/api/studio/editions/" + work, json("status", "abandoned"));
        String workSlug = read(owner.browser().get("/api/studio/editions/" + work)).path("novelSlug").asString();

        assertThat(read(new Browser(port).get("/api/novels/" + workSlug)).path("relay").path("free").asBoolean()).isFalse();
        Response attempt = successor.browser().post("/api/editions/" + work + "/continue", json("kind", "human"));
        assertThat(attempt.status()).isEqualTo(403);
    }

    private JsonNode relay() {
        return read(new Browser(port).get("/api/novels/" + slug)).path("relay");
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
