package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;

import java.io.IOException;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import space.panrid.novelka.account.CurrentUser;

/** The offline reader must never keep private publication responses in its Cache API store. */
@Component
class PrivateResponseHeaders extends OncePerRequestFilter {
    private final DSLContext db;
    private final CurrentUser currentUser;

    PrivateResponseHeaders(DSLContext db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean personal = path.equals("/api/home") || path.equals("/api/library");
        String prefix = path.startsWith("/api/v1/novels/") ? "/api/v1/novels/" : "/api/novels/";
        boolean privateData = path.contains("/workspace");
        if (path.startsWith(prefix)) {
            String slug = path.substring(prefix.length()).split("/", 2)[0];
            privateData |= db.fetchExists(db.selectOne().from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                    .where(NOVEL.SLUG.eq(slug), EDITION.VISIBILITY.eq(Visibility.PRIVATE)));
        } else if (personal && currentUser.accountId().isPresent()) {
            privateData |= db.fetchExists(EDITION, EDITION.VISIBILITY.eq(Visibility.PRIVATE),
                    Visibility.readableNow(currentUser.accountId().orElseThrow()));
        }
        if (privateData) {
            response.setHeader("X-Novelka-Private", "true");
            response.setHeader("Cache-Control", "private, no-store");
        }
        chain.doFilter(request, response);
    }
}
