package space.panrid.novelka.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.support.Browser.json;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.Pictures;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class MessagingFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    Person anna;
    Person bohdan;

    @BeforeEach
    void twoPeople() {
        anna = Accounts.signedIn(port, mailbox);
        bohdan = Accounts.signedIn(port, mailbox);
    }

    private long direct(Person from, Person to) {
        return read(from.browser().post("/api/conversations/direct", json("nick", to.nick()))).path("id").asLong();
    }

    private JsonNode inbox(Person person) {
        return read(person.browser().get("/api/conversations"));
    }

    @Test
    void twoPeopleWriteWithPicturesAndReadMarks() throws InterruptedException {
        BlockingQueue<String> bohdanTab = bohdan.browser().stream("/api/events");
        long chat = direct(anna, bohdan);
        assertThat(direct(bohdan, anna)).as("one conversation per pair").isEqualTo(chat);

        long picture = read(anna.browser().upload("/api/media/images", Map.of("kind", "message"), "file", "p.png", "image/png",
                Pictures.png(400, 300, Color.PINK))).path("id").asLong();
        read(anna.browser().post("/api/conversations/" + chat + "/messages",
                json("body", "Привіт, @" + bohdan.nick() + "! ||спойлер||", "imageIds", List.of(picture))));
        assertThat(nextEvent(bohdanTab, "message")).as("an open tab hears at once").isTrue();

        JsonNode list = inbox(bohdan);
        assertThat(list.path("unread").asInt()).isEqualTo(1);
        JsonNode row = list.path("items").path(0);
        assertThat(row.path("kind").asString()).isEqualTo("direct");
        assertThat(row.path("title").asString()).isEqualTo(anna.nick());
        assertThat(row.path("lastText").asString()).as("a spoiler stays hidden in the list").isEqualTo("Привіт, @" + bohdan.nick() + "! (спойлер)");

        JsonNode opened = read(bohdan.browser().get("/api/conversations/" + chat));
        JsonNode line = opened.path("lines").path(0);
        assertThat(line.path("body").asString()).isEqualTo("Привіт, @" + bohdan.nick() + "! ||спойлер||");
        assertThat(line.path("pictures")).hasSize(1);
        assertThat(line.path("mine").asBoolean()).isFalse();
        read(bohdan.browser().post("/api/conversations/" + chat + "/read", json("upTo", line.path("id").asLong())));
        assertThat(inbox(bohdan).path("unread").asInt()).isZero();

        long reply = read(bohdan.browser().post("/api/conversations/" + chat + "/messages",
                json("body", "Привіт!", "replyTo", line.path("id").asLong()))).path("id").asLong();
        assertThat(bohdan.browser().post("/api/conversations/" + chat + "/messages", json("imageIds", List.of(picture))).status())
                .as("someone else's picture").isEqualTo(400);
        assertThat(anna.browser().patch("/api/messages/" + reply, json("body", "чуже")).status()).isEqualTo(403);
        read(bohdan.browser().patch("/api/messages/" + reply, json("body", "Привіт-привіт!")));
        read(bohdan.browser().delete("/api/messages/" + reply));
        JsonNode lines = read(anna.browser().get("/api/conversations/" + chat)).path("lines");
        assertThat(lines.path(1).path("deleted").asBoolean()).isTrue();
        assertThat(lines.path(1).path("body").asString()).isEmpty();

        Person stranger = Accounts.signedIn(port, mailbox);
        assertThat(stranger.browser().get("/api/conversations/" + chat).status()).as("outsiders are not told it exists").isEqualTo(404);
    }

    @Test
    void blockingAndWhoMayWriteToMe() {
        long chat = direct(anna, bohdan);
        read(bohdan.browser().put("/api/me/blocks/" + anna.nick(), "{}"));
        assertThat(anna.browser().post("/api/conversations/" + chat + "/messages", json("body", "Агов")).status()).isEqualTo(403);
        assertThat(read(anna.browser().get("/api/conversations/" + chat)).path("canWrite").asBoolean()).isFalse();
        assertThat(read(bohdan.browser().get("/api/me/blocks"))).extracting(JsonNode::asString).containsExactly(anna.nick());
        read(bohdan.browser().delete("/api/me/blocks/" + anna.nick()));
        assertThat(anna.browser().post("/api/conversations/" + chat + "/messages", json("body", "Агов")).status()).isEqualTo(201);

        Person quiet = Accounts.signedIn(port, mailbox);
        assertThat(quiet.browser().patch("/api/me", json("dmPolicy", "nobody")).status()).isEqualTo(200);
        assertThat(anna.browser().post("/api/conversations/direct", json("nick", quiet.nick())).status()).isEqualTo(403);
        assertThat(anna.browser().post("/api/conversations/groups", json("title", "Клуб", "nicks", List.of(quiet.nick()))).status())
                .isEqualTo(403);
    }

    @Test
    void aGroupWithAdminsMembersAndLeaving() {
        Person cyril = Accounts.signedIn(port, mailbox);
        long group = read(anna.browser().post("/api/conversations/groups",
                json("title", "Книжковий клуб", "nicks", List.of(bohdan.nick(), cyril.nick())))).path("id").asLong();
        JsonNode opened = read(bohdan.browser().get("/api/conversations/" + group));
        assertThat(opened.path("title").asString()).isEqualTo("Книжковий клуб");
        assertThat(opened.path("members")).extracting(m -> m.path("nick").asString() + ":" + m.path("role").asString())
                .containsExactly(anna.nick() + ":admin", bohdan.nick() + ":member", cyril.nick() + ":member");
        assertThat(opened.path("lines")).allSatisfy(line -> assertThat(line.path("kind").asString()).isEqualTo("system"));

        assertThat(bohdan.browser().patch("/api/conversations/" + group, json("title", "Мій клуб")).status()).isEqualTo(403);
        read(anna.browser().patch("/api/conversations/" + group, json("title", "Клуб читачів")));
        Person dana = Accounts.signedIn(port, mailbox);
        read(anna.browser().post("/api/conversations/" + group + "/members", json("nick", dana.nick())));
        assertThat(inbox(dana).path("items").path(0).path("title").asString()).isEqualTo("Клуб читачів");

        read(bohdan.browser().delete("/api/conversations/" + group + "/members/" + bohdan.nick()));
        assertThat(bohdan.browser().get("/api/conversations/" + group).status()).isEqualTo(404);
        read(anna.browser().delete("/api/conversations/" + group + "/members/" + anna.nick()));
        JsonNode after = read(cyril.browser().get("/api/conversations/" + group));
        assertThat(after.path("admin").asBoolean()).as("the oldest member takes over").isTrue();
        assertThat(after.path("lines").toString()).contains("виходить із групи");
    }

    @Test
    void aTeamChatFollowsTheTeam() {
        String team = read(anna.browser().get("/api/me/teams")).path(0).path("handle").asString();
        if (team.isEmpty()) {
            read(anna.browser().post("/api/studio/editions", "{\"kind\":\"human\",\"title\":\"Для команди " + anna.nick() + "\"}"));
            team = read(anna.browser().get("/api/me/teams")).path(0).path("handle").asString();
        }
        read(anna.browser().post("/api/teams/" + team + "/members", json("nick", bohdan.nick(), "role", "editor")));
        JsonNode teamRow = inbox(bohdan).path("items").path(0);
        assertThat(teamRow.path("kind").asString()).isEqualTo("team");
        assertThat(teamRow.path("teamHandle").asString()).isEqualTo(team);
        long chat = teamRow.path("id").asLong();

        read(anna.browser().post("/api/conversations/" + chat + "/messages", json("body", "Нова глава готова")));
        assertThat(inbox(bohdan).path("unread").asInt()).isEqualTo(1);
        assertThat(bohdan.browser().delete("/api/conversations/" + chat + "/members/" + bohdan.nick()).status())
                .as("a team chat is left only with the team").isEqualTo(400);

        read(anna.browser().delete("/api/teams/" + team + "/members/" + bohdan.nick()));
        assertThat(bohdan.browser().get("/api/conversations/" + chat).status()).isEqualTo(404);
        assertThat(inbox(bohdan).path("items")).isEmpty();
    }

    private static boolean nextEvent(BlockingQueue<String> lines, String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line != null && line.equals("event:" + name)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
