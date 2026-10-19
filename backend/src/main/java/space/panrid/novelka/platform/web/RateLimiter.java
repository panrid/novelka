package space.panrid.novelka.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limit: at most {@code max} events per key within {@code window}.
 * One server instance is enough for the site; a restart simply forgets old attempts.
 */
public final class RateLimiter {

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public RateLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** Records an attempt and says whether it is still within the limit. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant horizon = now.minus(window);
        boolean[] allowed = {false};
        events.compute(key, (k, times) -> {
            Deque<Instant> recent = times == null ? new ArrayDeque<>() : times;
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(horizon)) {
                recent.pollFirst();
            }
            if (recent.size() < max) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent;
        });
        if (events.size() > 10_000) {
            events.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        return allowed[0];
    }

    /** How long until {@code key} may try again: zero when it may now. */
    public Duration retryAfter(String key) {
        Instant now = clock.instant();
        Duration[] wait = {Duration.ZERO};
        events.computeIfPresent(key, (k, times) -> {
            Instant oldest = times.peekFirst();
            if (times.size() >= max && oldest != null) {
                Duration left = Duration.between(now, oldest.plus(window));
                wait[0] = left.isNegative() ? Duration.ZERO : left;
            }
            return times;
        });
        return wait[0];
    }

    /** Forgets every attempt, as a restart does. */
    public void clear() {
        events.clear();
    }
}
