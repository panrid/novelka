package space.panrid.novelka.platform.web;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Errors in readers' browsers (план, етап 23, п. 4): the server cannot see them, so the site sends
 * them here and they go into the same log as the server's own, for Loki and Grafana to group.
 * Nothing personal: the page's path without its query, the message, the stack, the build and the
 * browser; a browser that sends too much is cut off quietly.
 */
@RestController
class ClientErrorsController {

    private static final Logger log = LoggerFactory.getLogger("client-errors");

    private final RateLimiter perAddress;
    private final RateLimiter overall;

    ClientErrorsController(Clock clock) {
        this.perAddress = new RateLimiter(20, Duration.ofMinutes(10), clock);
        this.overall = new RateLimiter(300, Duration.ofMinutes(1), clock);
    }

    /** @param kind «error», «rejection» or «render» (a page that fell in React) */
    record ClientError(String kind, String message, String stack, String url, String version) {
    }

    @PostMapping("/api/client-errors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@RequestBody ClientError body, @RequestHeader(value = "User-Agent", required = false) String agent,
            HttpServletRequest request) {
        if (!perAddress.tryAcquire(request.getRemoteAddr()) || !overall.tryAcquire("all")) {
            return;
        }
        String kind = switch (body.kind() == null ? "" : body.kind()) {
            case "rejection", "render" -> body.kind();
            default -> "error";
        };
        log.warn("Browser {}: {} | page {} | build {} | {}\n{}", kind, clip(body.message(), 500), path(body.url()),
                clip(body.version(), 80), clip(agent, 200), clip(body.stack(), 4_000));
    }

    /** The page without its query or fragment: a reset link's token must never reach the log. */
    static String path(String url) {
        String page = url == null ? "" : url.strip();
        int cut = page.length();
        for (char mark : new char[] {'?', '#'}) {
            int at = page.indexOf(mark);
            if (at >= 0) {
                cut = Math.min(cut, at);
            }
        }
        return clip(page.substring(0, cut), 300);
    }

    /** One line at most {@code max} long, without control characters that would forge log lines. */
    static String clip(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String clean = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
