package space.panrid.novelka.notification.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.platform.live.LiveEvents;

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
    Inbox.Page notifications(@RequestParam(required = false) Long before) {
        return inbox.page(currentUser.requireSignedIn().accountId(), before);
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

    /** Live nudges for this tab: notifications, chat, messages, translation progress. */
    // No «produces»: a signed-out tab must still get its JSON 401, not a 406.
    @GetMapping("/events")
    SseEmitter events() {
        return live.connect(currentUser.requireSignedIn().accountId());
    }
}
