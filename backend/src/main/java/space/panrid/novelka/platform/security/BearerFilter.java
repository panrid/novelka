package space.panrid.novelka.platform.security;

import java.io.IOException;
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
 * Not a bean: the security chain alone runs it.
 */
final class BearerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerFilter.class);
    private static final String BEARER = "bearer ";

    private final BearerTokens tokens;
    private final JsonMapper json;

    BearerFilter(BearerTokens tokens, JsonMapper json) {
        this.tokens = tokens;
        this.json = json;
    }

    /** Whether the request presents a token rather than (or besides) the session cookie. */
    static boolean carriesBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.length() > BEARER.length()
                && header.substring(0, BEARER.length()).toLowerCase(Locale.ROOT).equals(BEARER);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !carriesBearer(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
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

    static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
