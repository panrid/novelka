package space.panrid.novelka.platform.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Signs in a request that carries {@code Authorization: Bearer <token>} (етап 22.1). The token
 * replaces the session for this one request: nothing is saved, the cookie session (if any) is
 * ignored, and the request goes on only where the token's scopes reach. CSRF is not asked of
 * these requests — a browser never adds the header by itself — while cookie sessions keep it.
 * Here too the API's limits are counted ({@link ApiRateLimits}): per token, and per address for the
 * public {@code /api/v1} without one; past them the answer is 429 with {@code Retry-After}.
 * Not a bean: the security chain alone runs it.
 */
final class BearerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerFilter.class);
    private static final String BEARER = "bearer ";

    private final BearerTokens tokens;
    private final ApiRateLimits limits;
    private final JsonMapper json;

    BearerFilter(BearerTokens tokens, ApiRateLimits limits, JsonMapper json) {
        this.tokens = tokens;
        this.limits = limits;
        this.json = json;
    }

    /** Whether the request presents a token rather than (or besides) the session cookie. */
    static boolean carriesBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.length() > BEARER.length()
                && header.substring(0, BEARER.length()).toLowerCase(Locale.ROOT).equals(BEARER);
    }

    /** The public reading API, open without a token; the Studio's API under it (етап 22.3) is for those signed in. */
    static boolean publicApi(HttpServletRequest request) {
        String path = path(request);
        return (path.equals("/api/v1") || path.startsWith("/api/v1/")) && !path.equals("/api/v1/studio")
                && !path.startsWith("/api/v1/studio/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !carriesBearer(request) && !publicApi(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!carriesBearer(request)) {
            Duration wait = limits.address(request.getRemoteAddr());
            if (wait != null) {
                tooMany(response, wait);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(HttpHeaders.AUTHORIZATION).substring(BEARER.length()).strip();
        String path = path(request);
        Optional<TokenGrant> found = tokens.authenticate(token, request.getRemoteAddr());
        if (found.isEmpty()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            SecurityConfiguration.problem(response, json, HttpStatus.UNAUTHORIZED,
                    "Токен недійсний: його відкликали, строк дії минув або він хибний.");
            return;
        }
        TokenGrant grant = found.get();
        String refusal = tokens.refusal(grant, request.getMethod(), path);
        if (refusal != null) {
            SecurityConfiguration.problem(response, json, HttpStatus.FORBIDDEN, refusal);
            return;
        }
        Duration wait = limits.token(grant.tokenId());
        if (wait != null) {
            tooMany(response, wait);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(grant.authentication());
        SecurityContextHolder.setContext(context);
        request.setAttribute(TokenGrant.ATTRIBUTE, grant);
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                tokens.completed(grant, request.getMethod(), path, request.getRemoteAddr(), response.getStatus());
            } catch (RuntimeException error) {
                log.error("Could not journal a request made with token {}", grant.tokenId(), error);
            }
        }
    }

    private void tooMany(HttpServletResponse response, Duration wait) throws IOException {
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        SecurityConfiguration.problem(response, json, HttpStatus.TOO_MANY_REQUESTS,
                "Забагато запитів. Зачекайте " + seconds + " с і спробуйте знову.");
    }

    static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
