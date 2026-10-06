package space.panrid.novelka.autotranslate.internal;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The server is going down (a deploy): runs take no new step and ask the model nothing more.
 * An answer already on its way is waited for and saved, so the run goes on after the restart
 * by itself, without paying for that part again.
 */
@Component
class Shutdown {

    private final AtomicBoolean stopping = new AtomicBoolean();

    @EventListener(ContextClosedEvent.class)
    void closing() {
        stopping.set(true);
    }

    boolean stopping() {
        return stopping.get();
    }

    /** For tests: as if the server were going down, or back up. */
    void set(boolean value) {
        stopping.set(value);
    }

    /** Thrown before a model call while the server is going down; the step goes back to the queue. */
    static final class Stopping extends RuntimeException {

        Stopping() {
            super("the server is stopping", null, false, false);
        }
    }
}
