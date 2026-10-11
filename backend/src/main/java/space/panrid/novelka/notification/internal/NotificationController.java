package space.panrid.novelka.notification.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.web.UserFacingException;

@RestController
@RequestMapping("/api")
class NotificationController {

    private final CurrentUser currentUser;
    private final Inbox inbox;
    private final LiveEvents live;

    NotificationController(CurrentUser currentUser, Inbox inbox, LiveEvents live) {
        this.currentUser = currentUser;
        this.inbox = inbox;
        this.live = live;
    }

    @GetMapping("/notifications")
    Inbox.Page notifications(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "false") boolean archived) {
        return inbox.page(currentUser.requireSignedIn().accountId(), page, archived);
    }

    record Unread(int unread) {
    }

    @GetMapping("/notifications/unread")
    Unread unread() {
        return new Unread(inbox.unread(currentUser.requireSignedIn().accountId()));
    }

    /** @param upTo newest id the person has seen; absent means everything */
    record Seen(Long upTo) {
    }

    @PostMapping("/notifications/read")
    Unread read(@RequestBody Seen body) {
        long me = currentUser.requireSignedIn().accountId();
        inbox.markRead(me, body.upTo());
        return new Unread(inbox.unread(me));
    }

    /**
     * Rows to put away, bring back or delete.
     *
     * @param ids      the chosen rows; absent means all of the list (or of the archive)
     * @param archived for deleting everything: the archive rather than the list
     */
    record Chosen(List<Long> ids, Boolean archived) {
    }

    @PostMapping("/notifications/archive")
    Unread archive(@RequestBody Chosen body) {
        long me = currentUser.requireSignedIn().accountId();
        inbox.archive(me, limited(body.ids()));
        return new Unread(inbox.unread(me));
    }

    @PostMapping("/notifications/restore")
    Unread restore(@RequestBody Chosen body) {
        long me = currentUser.requireSignedIn().accountId();
        inbox.restore(me, limited(body.ids()));
        return new Unread(inbox.unread(me));
    }

    @PostMapping("/notifications/delete")
    Unread delete(@RequestBody Chosen body) {
        long me = currentUser.requireSignedIn().accountId();
        inbox.delete(me, limited(body.ids()), Boolean.TRUE.equals(body.archived()));
        return new Unread(inbox.unread(me));
    }

    /** A page holds 20, «select all» a few pages at most. */
    private static List<Long> limited(List<Long> ids) {
        if (ids != null && ids.size() > 500) {
            throw UserFacingException.badRequest("Забагато сповіщень за раз.");
        }
        return ids;
    }

    /** Live nudges for this tab: notifications, chat, messages, translation progress. */
    // No «produces»: a signed-out tab must still get its JSON 401, not a 406.
    @GetMapping("/events")
    SseEmitter events() {
        return live.connect(currentUser.requireSignedIn().accountId());
    }
}
