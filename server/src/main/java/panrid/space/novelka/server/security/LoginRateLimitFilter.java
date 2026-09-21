package panrid.space.novelka.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-process limiter. Remote address is supplied by the trusted reverse proxy configuration. */
public final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, long[]> attempts = new HashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if ("POST".equals(request.getMethod()) && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
            if (request.getContentLengthLong() > 8192) {
                response.sendError(413);
                return;
            }
            if (!allow(request.getRemoteAddr())) {
                response.setHeader("Retry-After", "600");
                response.sendError(429);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String address) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue()[0] < now - 600_000);
        if (!attempts.containsKey(address) && attempts.size() >= 10000) return false;
        long[] attempt = attempts.computeIfAbsent(address, ignored -> new long[]{now, 0});
        return ++attempt[1] <= 20;
    }
}
