package space.panrid.novelka.webhook.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a webhook may hear (етап 22.5). The codes are part of the API (header {@code X-Novelka-Event}
 * and {@code event} in the body): new ones may come, these do not change.
 */
enum WebhookEvent {

    /** Readers can open new chapters of a translation. */
    CHAPTER_PUBLISHED("chapter.published", false),
    /** An autotranslation run translated every chapter. Only for the translation's team. */
    RUN_FINISHED("run.finished", true),
    /** An autotranslation run stopped: an error, or someone cancelled it. Only for the team. */
    RUN_STOPPED("run.stopped", true),
    /** A reader sent suggestions to the team. Only for the team. */
    SUGGESTION_CREATED("suggestion.created", true),
    /** The original's site has chapters the translation can take. Only for the team. */
    SOURCE_CHAPTERS("source.chapters", true),
    /** «Надіслати пробну подію»: nothing happened, the address is being tried. */
    PING("ping", false);

    private final String code;
    private final boolean team;

    WebhookEvent(String code, boolean team) {
        this.code = code;
        this.team = team;
    }

    String code() {
        return code;
    }

    /** Only the team of the translation hears it. */
    boolean team() {
        return team;
    }

    static Optional<WebhookEvent> of(String code) {
        return Arrays.stream(values()).filter(event -> event.code.equals(code)).findFirst();
    }

    /** What a person may subscribe to: everything but the trial. */
    static List<WebhookEvent> subscribable() {
        return Arrays.stream(values()).filter(event -> event != PING).toList();
    }
}
