package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_RATING;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.web.UserFacingException;

@RestController
@RequestMapping("/api")
class CommunityController {

    private final CurrentUser currentUser;
    private final CommentService comments;
    private final ChatService chat;
    private final DSLContext db;
    private final People people;

    CommunityController(CurrentUser currentUser, CommentService comments, ChatService chat, DSLContext db, People people) {
        this.people = people;
        this.currentUser = currentUser;
        this.comments = comments;
        this.chat = chat;
        this.db = db;
    }

    // ---- comments ---------------------------------------------------------------------------

    /** @param chapter the chapter's position; absent for the translation's own discussion */
    @GetMapping("/editions/{editionId}/comments")
    CommentService.Thread comments(@PathVariable long editionId, @RequestParam(required = false) Integer chapter,
            @RequestParam(defaultValue = "new") String sort, @RequestParam(defaultValue = "1") int page) {
        return comments.list(editionId, chapter, sort, page, currentUser.accountId().orElse(null));
    }

    record NewComment(Integer chapter, String body, Long replyTo) {
    }

    record Created(long id) {
    }

    @PostMapping("/editions/{editionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Created comment(@PathVariable long editionId, @RequestBody NewComment body) {
        return new Created(comments.post(currentUser.requireSignedIn(), editionId, body.chapter(), body.body(), body.replyTo()));
    }

    record Text(String body) {
    }

    @PatchMapping("/comments/{id}")
    void editComment(@PathVariable long id, @RequestBody Text body) {
        comments.edit(currentUser.requireSignedIn(), id, body.body());
    }

    @DeleteMapping("/comments/{id}")
    void deleteComment(@PathVariable long id, @RequestParam(required = false) String reason) {
        comments.remove(currentUser.requireSignedIn(), id, reason);
    }

    record Vote(int value) {
    }

    record Score(int score) {
    }

    @PutMapping("/comments/{id}/vote")
    Score vote(@PathVariable long id, @RequestBody Vote body) {
        return new Score(comments.vote(currentUser.requireSignedIn(), id, body.value()));
    }

    // ---- site chat ----------------------------------------------------------------------------

    /** The channels of the chat, with what the viewer has not read yet. */
    @GetMapping("/chat/channels")
    List<ChatService.Channel> channels() {
        return chat.channels(currentUser.accountId().orElse(null));
    }

    /** @param channel a channel's slug; «загальний» when absent */
    @GetMapping("/chat")
    List<ChatService.Line> chat(@RequestParam(required = false) String channel, @RequestParam(required = false) Long before) {
        return chat.lines(channel, before, currentUser.accountId().orElse(null));
    }

    record NewLine(String body, Long replyTo, String channel) {
    }

    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.CREATED)
    Created say(@RequestBody NewLine body) {
        return new Created(chat.post(currentUser.requireSignedIn(), body.channel(), body.body(), body.replyTo()));
    }

    record Seen(long upTo) {
    }

    @PostMapping("/chat/channels/{slug}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable String slug, @RequestBody Seen body) {
        chat.markRead(currentUser.requireSignedIn().accountId(), slug, body.upTo());
    }

    @DeleteMapping("/chat/{id}")
    void unsay(@PathVariable long id) {
        chat.remove(currentUser.requireSignedIn(), id);
    }

    /** «Зараз на сайті»: open to guests too, like the chat itself. */
    @GetMapping("/chat/online")
    ChatService.Online online() {
        return chat.online();
    }

    record Presence(boolean showOnline) {
    }

    @GetMapping("/me/presence")
    Presence presence() {
        return new Presence(chat.shownOnline(currentUser.requireSignedIn().accountId()));
    }

    @PutMapping("/me/presence")
    Presence presence(@RequestBody Presence body) {
        long me = currentUser.requireSignedIn().accountId();
        chat.showOnline(me, body.showOnline());
        return new Presence(chat.shownOnline(me));
    }

    // ---- rating of a translation -----------------------------------------------------------

    record Rating(Integer score) {
    }

    record RatingSummary(Double average, int count, Integer mine) {
    }

    /** 1–5 stars, or null to take one's rating back. */
    @PutMapping("/editions/{editionId}/rating")
    RatingSummary rate(@PathVariable long editionId, @RequestBody Rating body) {
        Viewer viewer = currentUser.requireSignedIn();
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId).and(EDITION.HIDDEN_AT.isNull()))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
        if (body.score() == null) {
            db.deleteFrom(EDITION_RATING).where(EDITION_RATING.EDITION_ID.eq(editionId), EDITION_RATING.ACCOUNT_ID.eq(viewer.accountId()))
                    .execute();
        } else {
            if (body.score() < 1 || body.score() > 5) {
                throw UserFacingException.badRequest("Оцінка — від 1 до 5 зірок.");
            }
            short score = body.score().shortValue();
            db.insertInto(EDITION_RATING).set(EDITION_RATING.EDITION_ID, editionId).set(EDITION_RATING.ACCOUNT_ID, viewer.accountId())
                    .set(EDITION_RATING.SCORE, score)
                    .onConflict(EDITION_RATING.EDITION_ID, EDITION_RATING.ACCOUNT_ID).doUpdate()
                    .set(EDITION_RATING.SCORE, score).set(EDITION_RATING.UPDATED_AT, DSL.currentOffsetDateTime())
                    .execute();
        }
        var row = db.select(DSL.avg(EDITION_RATING.SCORE), DSL.count()).from(EDITION_RATING)
                .where(EDITION_RATING.EDITION_ID.eq(editionId)).fetchSingle();
        return new RatingSummary(row.value1() == null ? null : row.value1().doubleValue(), row.value2(), body.score());
    }

    // ---- suggestions while typing @ or $ ------------------------------------------------------

    record Suggestion(String name, String title, String avatarUrl) {
    }

    /** People (@) or teams ($) whose name starts with what was typed, for the box under the cursor. */
    @GetMapping("/mentions")
    List<Suggestion> mentions(@RequestParam String kind, @RequestParam String q) {
        currentUser.requireSignedIn();
        // startsWith escapes % and _ itself, so nicks like user_ab match as typed.
        String prefix = q.strip().toLowerCase(java.util.Locale.ROOT);
        if (prefix.isEmpty() || prefix.length() > 30) {
            return List.of();
        }
        if ("$".equals(kind)) {
            return db.select(TEAM.HANDLE, TEAM.NAME).from(TEAM).where(TEAM.HANDLE_KEY.startsWith(prefix))
                    .orderBy(TEAM.HANDLE_KEY).limit(8)
                    .fetch(r -> new Suggestion(r.value1(), r.value2(), null));
        }
        var rows = db.select(ACCOUNT.NICK, ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.startsWith(prefix))
                .orderBy(ACCOUNT.NICK_KEY).limit(8).fetch();
        var avatars = people.of(rows.map(r -> r.value2()));
        return rows.map(r -> new Suggestion(r.value1(), null, avatars.get(r.value2()).avatarUrl()));
    }
}
