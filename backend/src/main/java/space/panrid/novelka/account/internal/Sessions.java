package space.panrid.novelka.account.internal;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import space.panrid.novelka.account.AccountPrincipal;

/** Starting and ending sessions. Sessions live in PostgreSQL (Spring Session JDBC). */
@Component
class Sessions {

    private final SecurityContextRepository contexts;
    private final FindByIndexNameSessionRepository<? extends Session> store;

    Sessions(SecurityContextRepository contexts, FindByIndexNameSessionRepository<? extends Session> store) {
        this.contexts = contexts;
        this.store = store;
    }

    void signIn(long accountId, HttpServletRequest request, HttpServletResponse response) {
        // A new session id on every sign-in: a session fixed before sign-in is useless after it.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AccountPrincipal(accountId), null, List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /** After a password change nobody who had the old password stays signed in. */
    void signOutEverywhere(long accountId) {
        store.findByPrincipalName(Long.toString(accountId)).keySet().forEach(store::deleteById);
    }
}
