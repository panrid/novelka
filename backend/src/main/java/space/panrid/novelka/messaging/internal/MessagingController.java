package space.panrid.novelka.messaging.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
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
class MessagingController {

    private final CurrentUser currentUser;
    private final Conversations conversations;
    private final Blocks blocks;
    private final DSLContext db;

    MessagingController(CurrentUser currentUser, Conversations conversations, Blocks blocks, DSLContext db) {
        this.currentUser = currentUser;
        this.conversations = conversations;
        this.blocks = blocks;
        this.db = db;
    }

    @GetMapping("/conversations")
    Conversations.Inbox list() {
        return conversations.list(me().accountId());
    }

    record Started(long id) {
    }

    record DirectRequest(String nick) {
    }

    /** «Написати» on a profile: the existing conversation, or a new one. */
    @PostMapping("/conversations/direct")
    Started direct(@RequestBody DirectRequest body) {
        return new Started(conversations.direct(me(), body.nick()));
    }

    record GroupRequest(String title, List<String> nicks) {
    }

    @PostMapping("/conversations/groups")
    @ResponseStatus(HttpStatus.CREATED)
    Started group(@RequestBody GroupRequest body) {
        return new Started(conversations.group(me(), body.title(), body.nicks()));
    }

    /** Newest page, or older lines before {@code before}. */
    @GetMapping("/conversations/{id}")
    Conversations.Details open(@PathVariable long id, @RequestParam(required = false) Long before) {
        return conversations.open(me(), id, before);
    }

    record NewMessage(String body, Long replyTo, List<Long> imageIds) {
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    Started send(@PathVariable long id, @RequestBody NewMessage body) {
        return new Started(conversations.send(me(), id, body.body(), body.replyTo(), body.imageIds()));
    }

    record Text(String body) {
    }

    @PatchMapping("/messages/{id}")
    void edit(@PathVariable long id, @RequestBody Text body) {
        conversations.edit(me(), id, body.body());
    }

    @DeleteMapping("/messages/{id}")
    void delete(@PathVariable long id) {
        conversations.delete(me(), id);
    }

    record Read(long upTo) {
    }

    @PostMapping("/conversations/{id}/read")
    void read(@PathVariable long id, @RequestBody Read body) {
        conversations.read(me().accountId(), id, body.upTo());
    }

    record Mute(boolean muted) {
    }

    @PutMapping("/conversations/{id}/mute")
    void mute(@PathVariable long id, @RequestBody Mute body) {
        conversations.mute(me().accountId(), id, body.muted());
    }

    /** @param avatarImageId with {@code changeAvatar}: a new picture, or null to remove it */
    record GroupChange(String title, Long avatarImageId, Boolean changeAvatar) {
    }

    @PatchMapping("/conversations/{id}")
    void change(@PathVariable long id, @RequestBody GroupChange body) {
        conversations.rename(me(), id, body.title(), body.avatarImageId(), Boolean.TRUE.equals(body.changeAvatar()));
    }

    record MemberRequest(String nick) {
    }

    @PostMapping("/conversations/{id}/members")
    void add(@PathVariable long id, @RequestBody MemberRequest body) {
        conversations.add(me(), id, body.nick());
    }

    /** Removing oneself is leaving the group. */
    @DeleteMapping("/conversations/{id}/members/{nick}")
    void remove(@PathVariable long id, @PathVariable String nick) {
        conversations.remove(me(), id, nick);
    }

    record Role(String role) {
    }

    @PatchMapping("/conversations/{id}/members/{nick}")
    void role(@PathVariable long id, @PathVariable String nick, @RequestBody Role body) {
        conversations.setRole(me(), id, nick, body.role());
    }

    // ---- blocks ---------------------------------------------------------------------------------

    @GetMapping("/me/blocks")
    List<String> blocked() {
        return blocks.blockedBy(me().accountId());
    }

    @PutMapping("/me/blocks/{nick}")
    Map<String, Boolean> block(@PathVariable String nick) {
        blocks.block(me().accountId(), account(nick));
        return Map.of("blocked", true);
    }

    @DeleteMapping("/me/blocks/{nick}")
    Map<String, Boolean> unblock(@PathVariable String nick) {
        blocks.unblock(me().accountId(), account(nick));
        return Map.of("blocked", false);
    }

    private long account(String nick) {
        return db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT)))
                .fetchOptional(ACCOUNT.ID).orElseThrow(() -> UserFacingException.notFound("Людини з ніком «%s» немає.".formatted(nick)));
    }

    private Viewer me() {
        return currentUser.requireSignedIn();
    }
}
