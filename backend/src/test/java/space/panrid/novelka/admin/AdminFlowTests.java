package space.panrid.novelka.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.REPORT;
import static space.panrid.novelka.jooq.Tables.SITE_SETTING;
import static space.panrid.novelka.support.Browser.json;

import org.jooq.DSLContext;
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
class AdminFlowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    Person owner;
    Person admin;
    Person moderator;
    Person reader;
    Person translator;
    long edition;
    String slug;

    @BeforeEach
    void theStaffAndATranslation() {
        owner = Accounts.signedIn(port, mailbox);
        admin = Accounts.signedIn(port, mailbox);
        moderator = Accounts.signedIn(port, mailbox);
        reader = Accounts.signedIn(port, mailbox);
        translator = Accounts.signedIn(port, mailbox);
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, "reader").where(ACCOUNT.SITE_ROLE.eq("owner")).execute();
        role(owner, "owner");
        role(admin, "admin");
        role(moderator, "moderator");
        // Reports of other test classes must not crowd this list.
        db.update(REPORT).set(REPORT.STATE, "dismissed").where(REPORT.STATE.eq("open")).execute();
        edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Модерована %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        translator.browser().post("/api/studio/editions/" + edition + "/chapters/1/publish", """
                {"title":"Глава","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""");
        slug = read(translator.browser().get("/api/studio/editions/" + edition)).path("novelSlug").asString();
    }

    @AfterEach
    void settingsBack() {
        db.deleteFrom(SITE_SETTING).execute();
    }

    private void role(Person person, String role) {
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, role).where(ACCOUNT.NICK.eq(person.nick())).execute();
    }

    @Test
    void aReportedCommentIsHiddenByAModeratorAndCanComeBack() {
        long rude = read(translator.browser().post("/api/editions/" + edition + "/comments", json("body", "Грубий коментар"))).path("id").asLong();
        read(reader.browser().post("/api/reports", json("target", "comment", "targetId", rude, "reason", "образи")));
        read(owner.browser().post("/api/reports", json("target", "comment", "targetId", rude, "reason", "спам")));
        assertThat(reader.browser().get("/api/admin/reports").status()).isEqualTo(403);

        JsonNode reports = read(moderator.browser().get("/api/admin/reports"));
        assertThat(reports).singleElement().satisfies(item -> {
            assertThat(item.path("reports").asInt()).isEqualTo(2);
            assertThat(item.path("reasons")).extracting(JsonNode::asString).containsExactlyInAnyOrder("образи", "спам");
            assertThat(item.path("preview").path("text").asString()).isEqualTo("Грубий коментар");
            assertThat(item.path("preview").path("author").asString()).isEqualTo(translator.nick());
            assertThat(item.path("preview").path("slug").asString()).isEqualTo(slug);
        });
        assertThat(read(moderator.browser().get("/api/admin/overview")).path("openReports").asInt()).isEqualTo(1);

        read(moderator.browser().post("/api/admin/reports/comment/" + rude, json("action", "hide", "reason", "образи")));
        assertThat(read(moderator.browser().get("/api/admin/reports"))).isEmpty();
        assertThat(read(new Browser(port).get("/api/editions/" + edition + "/comments")).path("items").toString()).doesNotContain("Грубий");

        JsonNode hidden = read(moderator.browser().get("/api/admin/hidden"));
        assertThat(hidden.path(0).path("target").asString()).isEqualTo("comment");
        assertThat(hidden.path(0).path("hiddenBy").asString()).isEqualTo(moderator.nick());
        assertThat(hidden.path(0).path("reason").asString()).isEqualTo("образи");

        read(moderator.browser().post("/api/admin/hidden/comment/" + rude + "/restore", "{}"));
        assertThat(read(new Browser(port).get("/api/editions/" + edition + "/comments")).path("items").toString()).contains("Грубий");

        JsonNode audit = read(owner.browser().get("/api/admin/audit"));
        assertThat(audit).extracting(entry -> entry.path("action").asString()).startsWith("restore", "hide");
        assertThat(audit.path(1).path("actor").asString()).isEqualTo(moderator.nick());
        assertThat(moderator.browser().get("/api/admin/audit").status()).isEqualTo(403);
    }

    @Test
    void aDismissedReportLeavesTheThingAndModeratorsSeeOnlyReportedMessages() {
        read(reader.browser().post("/api/chat", json("body", "Нормальне повідомлення")));
        JsonNode lines = read(new Browser(port).get("/api/chat"));
        long line = lines.get(lines.size() - 1).path("id").asLong();
        read(translator.browser().post("/api/reports", json("target", "chat", "targetId", line, "reason", "не подобається")));
        read(moderator.browser().post("/api/admin/reports/chat/" + line, json("action", "dismiss")));
        JsonNode after = read(new Browser(port).get("/api/chat"));
        assertThat(after.get(after.size() - 1).path("body").asString()).isEqualTo("Нормальне повідомлення");

        long chat = read(reader.browser().post("/api/conversations/direct", json("nick", translator.nick()))).path("id").asLong();
        long message = read(reader.browser().post("/api/conversations/" + chat + "/messages", json("body", "Погроза"))).path("id").asLong();
        read(translator.browser().post("/api/reports", json("target", "message", "targetId", message, "reason", "погрози")));
        JsonNode report = read(moderator.browser().get("/api/admin/reports")).path(0);
        assertThat(report.path("preview").path("text").asString()).isEqualTo("Погроза");
        assertThat(moderator.browser().get("/api/conversations/" + chat).status()).as("but not the conversation").isEqualTo(404);

        read(moderator.browser().post("/api/admin/reports/message/" + message, json("action", "hide")));
        JsonNode seen = read(translator.browser().get("/api/conversations/" + chat)).path("lines").path(0);
        assertThat(seen.path("hidden").asBoolean()).isTrue();
        assertThat(seen.path("body").asString()).isEmpty();
    }

    @Test
    void administratorsHideTranslationsModeratorsDoNot() {
        assertThat(moderator.browser().post("/api/admin/hidden/edition/" + edition, json("reason", "порушення")).status()).isEqualTo(403);
        read(admin.browser().post("/api/admin/hidden/edition/" + edition, json("reason", "порушення")));
        assertThat(new Browser(port).get("/api/novels/" + slug).status()).isEqualTo(404);
        read(admin.browser().post("/api/admin/hidden/edition/" + edition + "/restore", "{}"));
        assertThat(new Browser(port).get("/api/novels/" + slug).status()).isEqualTo(200);
    }

    @Test
    void whoMayGiveWhichRole() {
        assertThat(moderator.browser().get("/api/admin/users").status()).isEqualTo(403);
        JsonNode found = read(admin.browser().get("/api/admin/users?q=" + reader.nick()));
        assertThat(found.path(0).path("nick").asString()).isEqualTo(reader.nick());
        assertThat(found.path(0).path("email").isNull()).as("administrators do not see addresses").isTrue();
        assertThat(read(owner.browser().get("/api/admin/users?q=" + reader.nick())).path(0).path("email").asString()).isEqualTo(reader.email());

        read(admin.browser().put("/api/admin/users/" + reader.nick() + "/role", json("role", "moderator")));
        assertThat(admin.browser().put("/api/admin/users/" + reader.nick() + "/role", json("role", "admin")).status()).isEqualTo(403);
        assertThat(admin.browser().put("/api/admin/users/" + admin.nick() + "/role", json("role", "reader")).status()).isEqualTo(400);
        assertThat(admin.browser().put("/api/admin/users/" + owner.nick() + "/role", json("role", "reader")).status()).isEqualTo(403);
        read(owner.browser().put("/api/admin/users/" + reader.nick() + "/role", json("role", "admin")));
        assertThat(admin.browser().put("/api/admin/users/" + reader.nick() + "/role", json("role", "reader")).status())
                .as("another administrator is the owner's to change").isEqualTo(403);
        assertThat(reader.browser().get("/api/admin/users").status()).as("the new role works at once").isEqualTo(200);
    }

    @Test
    void theOwnerClosesRegistrationAndSwitchesOffAdultNovels() {
        assertThat(admin.browser().get("/api/admin/settings").status()).isEqualTo(403);
        read(translator.browser().patch("/api/me", json("adultConfirmed", true)));
        long adult = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Доросла %s","adult":true}""".formatted(translator.nick()))).path("editionId").asLong();
        translator.browser().post("/api/studio/editions/" + adult + "/chapters", "{}");
        translator.browser().post("/api/studio/editions/" + adult + "/chapters/1/publish", """
                {"title":"Глава","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""");
        String adultSlug = read(translator.browser().get("/api/studio/editions/" + adult)).path("novelSlug").asString();
        assertThat(translator.browser().get("/api/novels/" + adultSlug).status()).isEqualTo(200);

        JsonNode saved = read(owner.browser().put("/api/admin/settings",
                json("relayInactiveMonths", 6, "registrationOpen", false, "adultEnabled", false)));
        assertThat(saved.path("relayInactiveMonths").asInt()).isEqualTo(6);
        assertThat(translator.browser().get("/api/novels/" + adultSlug).status()).as("18+ is off for the whole site").isEqualTo(403);

        Response closed = new Browser(port).post("/api/auth/register", json("nick", "novyi_chytach", "email", "novyi@example.com",
                "password", Accounts.PASSWORD));
        assertThat(closed.status()).isEqualTo(403);
        assertThat(closed.body()).contains("Реєстрацію тимчасово закрито");
        assertThat(owner.browser().put("/api/admin/settings", json("relayInactiveMonths", 0, "registrationOpen", true, "adultEnabled", true))
                .status()).isEqualTo(400);
        assertThat(read(owner.browser().get("/api/admin/audit")).path(0).path("action").asString()).isEqualTo("settings");
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return response.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }
}
