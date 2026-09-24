package space.panrid.novelka.account.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import space.panrid.novelka.account.CurrentUser;

/**
 * Remembers when a signed-in person was last on the site (needed for «естафета»:
 * a translation opens up after months of the owner's absence). Written at most
 * every five minutes per account.
 */
@Component
class LastSeenFilter extends OncePerRequestFilter {

    private static final Duration PRECISION = Duration.ofMinutes(5);

    private final AccountRepository accounts;
    private final Clock clock;
    private final Map<Long, Instant> written = new ConcurrentHashMap<>();

    LastSeenFilter(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        CurrentUser.signedInPrincipalId().ifPresent(this::touch);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private void touch(long accountId) {
        Instant now = clock.instant();
        Instant last = written.get(accountId);
        if (last != null && last.isAfter(now.minus(PRECISION))) {
            return;
        }
        written.put(accountId, now);
        OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
        accounts.touchLastSeen(accountId, at, at.minus(PRECISION));
    }

}
