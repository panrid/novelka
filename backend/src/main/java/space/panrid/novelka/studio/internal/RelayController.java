package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TAKEOVER_REQUEST;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.Relay;
import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.platform.mail.Mail;
import space.panrid.novelka.platform.mail.Mailer;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.Teams;

/** «Естафета»: asking to continue a translation, answering, and starting the continuation. */
@RestController
class RelayController {

    record Ask(String team, String message) {
    }

    record Answer(boolean grant) {
    }

    record Continue(String team, String kind) {
    }

    record RequestView(long id, String teamHandle, String teamName, String requestedBy, String message, String state,
            OffsetDateTime createdAt) {
    }

    record Started(long editionId, String novelSlug) {
    }

    private final AccessPolicy access;
    private final Relay relay;
    private final Teams teams;
    private final DSLContext db;
    private final Mailer mailer;
    private final SiteProperties site;

    RelayController(AccessPolicy access, Relay relay, Teams teams, DSLContext db, Mailer mailer, SiteProperties site) {
        this.access = access;
        this.relay = relay;
        this.teams = teams;
        this.db = db;
        this.mailer = mailer;
        this.site = site;
    }

    @PostMapping("/api/editions/{editionId}/takeover-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void ask(@PathVariable long editionId, @RequestBody Ask body) {
        Viewer viewer = access.requireSignedIn();
        TeamInfo team = team(body.team(), viewer);
        access.requireTeamTranslator(team.id());
        var edition = db.select(ACCOUNT.EMAIL, ACCOUNT.NICK, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), EDITION.TEAM_ID)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TEAM.OWNER_ID)).where(EDITION.ID.eq(editionId)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої новели немає."));
        if (edition.value4() == team.id()) {
            throw UserFacingException.badRequest("Це переклад вашої ж команди.");
        }
        relay.request(editionId, team.id(), viewer.accountId(), body.message());
        String link = site.link("/studio/" + editionId + "/relay");
        AfterCommit.run(() -> mailer.send(new Mail(edition.value1(), "Хочуть продовжити ваш переклад на Новелці", """
                Привіт, %s!

                Команда «%s» хоче продовжити ваш переклад «%s». Відповісти можна тут:
                %s

                Якщо не відповісти за %d днів, а нових глав не було кілька місяців, переклад стане вільним для продовження.
                """.formatted(edition.value2(), team.name(), edition.value3(), link, 14))));
    }

    @GetMapping("/api/studio/editions/{editionId}/takeover-requests")
    List<RequestView> requests(@PathVariable long editionId) {
        access.requireEditionOwner(editionId);
        return db.select(TAKEOVER_REQUEST.ID, TEAM.HANDLE, DSL.coalesce(TEAM.NAME, DSL.field("owner.nick", String.class)),
                        ACCOUNT.NICK, TAKEOVER_REQUEST.MESSAGE, TAKEOVER_REQUEST.STATE, TAKEOVER_REQUEST.CREATED_AT)
                .from(TAKEOVER_REQUEST)
                .join(TEAM).on(TEAM.ID.eq(TAKEOVER_REQUEST.TEAM_ID))
                .join(ACCOUNT.as("owner")).on(DSL.field("owner.id", Long.class).eq(TEAM.OWNER_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(TAKEOVER_REQUEST.REQUESTED_BY))
                .where(TAKEOVER_REQUEST.EDITION_ID.eq(editionId))
                .orderBy(TAKEOVER_REQUEST.CREATED_AT.desc())
                .fetch(r -> new RequestView(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7()));
    }

    @PostMapping("/api/studio/editions/{editionId}/takeover-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@PathVariable long editionId, @PathVariable long requestId, @RequestBody Answer body) {
        access.requireEditionOwner(editionId);
        relay.answer(editionId, requestId, body.grant());
    }

    @PostMapping("/api/editions/{editionId}/continue")
    @ResponseStatus(HttpStatus.CREATED)
    Started start(@PathVariable long editionId, @RequestBody Continue body) {
        Viewer viewer = access.requireSignedIn();
        TeamInfo team = team(body.team(), viewer);
        access.requireTeamTranslator(team.id());
        EditionRef ref = relay.continueEdition(editionId, team.id(), body.kind() == null ? "human" : body.kind());
        return new Started(ref.editionId(), ref.novelSlug());
    }

    private TeamInfo team(String handle, Viewer viewer) {
        if (handle == null || handle.isBlank()) {
            return teams.find(teams.personalTeam(viewer.accountId())).orElseThrow();
        }
        return teams.findByHandle(handle).orElseThrow(() -> UserFacingException.notFound("Такої команди немає."));
    }
}
