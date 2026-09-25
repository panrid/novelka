package space.panrid.novelka.platform.live;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-sent events to open tabs: a new notification, a chat line, a message, a job's
 * progress. Only a nudge travels here; pages fetch the details through the usual API, so a
 * missed event costs nothing but a delay. One process: connections live in memory.
 */
@Component
public class LiveEvents {

    private static final Logger log = LoggerFactory.getLogger(LiveEvents.class);
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final int TABS_PER_ACCOUNT = 10;

    private final Map<Long, Set<SseEmitter>> byAccount = new ConcurrentHashMap<>();

    /** A new stream for one tab of a signed-in person. */
    public SseEmitter connect(long accountId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Set<SseEmitter> tabs = byAccount.computeIfAbsent(accountId, id -> new CopyOnWriteArraySet<>());
        if (tabs.size() >= TABS_PER_ACCOUNT) {
            tabs.stream().findFirst().ifPresent(oldest -> {
                tabs.remove(oldest);
                oldest.complete();
            });
        }
        tabs.add(emitter);
        Runnable drop = () -> forget(accountId, emitter);
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(error -> drop.run());
        send(emitter, accountId, "hello", Map.of());
        return emitter;
    }

    public void send(long accountId, String type, Object payload) {
        for (SseEmitter emitter : byAccount.getOrDefault(accountId, Set.of())) {
            send(emitter, accountId, type, payload);
        }
    }

    public void send(List<Long> accountIds, String type, Object payload) {
        accountIds.stream().distinct().forEach(id -> send(id, type, payload));
    }

    /** Everyone with the site open and signed in (the site chat). */
    public void sendAll(String type, Object payload) {
        byAccount.keySet().forEach(id -> send(id, type, payload));
    }

    public boolean connected(long accountId) {
        return !byAccount.getOrDefault(accountId, Set.of()).isEmpty();
    }

    /** Keeps proxies from closing idle streams and finds dead ones. */
    @Scheduled(fixedDelay = 25_000)
    void ping() {
        byAccount.forEach((accountId, tabs) -> tabs.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException gone) {
                forget(accountId, emitter);
            }
        }));
    }

    private void send(SseEmitter emitter, long accountId, String type, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(type).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException gone) {
            log.debug("Live stream of {} closed", accountId);
            forget(accountId, emitter);
        }
    }

    private void forget(long accountId, SseEmitter emitter) {
        Set<SseEmitter> tabs = byAccount.get(accountId);
        if (tabs != null) {
            tabs.remove(emitter);
            if (tabs.isEmpty()) {
                byAccount.remove(accountId, tabs);
            }
        }
    }
}
