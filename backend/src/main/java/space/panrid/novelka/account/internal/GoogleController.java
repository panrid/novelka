package space.panrid.novelka.account.internal;

import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.GoogleSignIn;
import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.platform.text.Handles;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * «Увійти через Google» and «Прив’язати Google» in the settings. The browser goes to Google and
 * comes back to {@link #CALLBACK} (the address registered at Google); what it started with —
 * the state against forged returns and the PKCE secret — waits in the session meanwhile.
 * A newcomer then picks a nick on /login/google.
 */
@RestController
class GoogleController {

    static final String CALLBACK = "/login/oauth2/code/google";

    private static final String STARTED = "novelka.google.started";
    private static final String NEWCOMER = "novelka.google.newcomer";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Kept in the session (serialized to the database) between leaving for Google and coming back. */
    record Started(String state, String verifier, String next, Long linkTo) implements Serializable {
    }

    record Newcomer(String subject, String email, String name, String next) implements Serializable {
    }

    record Providers(boolean google) {
    }

    record NewcomerView(String email, String nick, String next) {
    }

    record NickRequest(String nick) {
    }

    private final GoogleSignIn google;
    private final AccountService accounts;
    private final AccountRepository repository;
    private final Sessions sessions;
    private final MeQuery me;
    private final CurrentUser currentUser;
    private final SiteProperties site;

    GoogleController(GoogleSignIn google, AccountService accounts, AccountRepository repository, Sessions sessions,
            MeQuery me, CurrentUser currentUser, SiteProperties site) {
        this.google = google;
        this.accounts = accounts;
        this.repository = repository;
        this.sessions = sessions;
        this.me = me;
        this.currentUser = currentUser;
        this.site = site;
    }

    /** Which ways of signing in besides the password the site offers. */
    @GetMapping("/api/auth/providers")
    Providers providers() {
        return new Providers(google.configured());
    }

    /** Sends the browser to Google; {@code link} ties Google to the signed-in account instead of signing in. */
    @GetMapping("/api/auth/google")
    ResponseEntity<Void> start(@RequestParam(defaultValue = "/") String next, @RequestParam(defaultValue = "false") boolean link,
            HttpServletRequest request) {
        if (!google.configured()) {
            throw UserFacingException.notFound("Вхід через Google на сайті не налаштовано.");
        }
        Long linkTo = link ? currentUser.requireSignedIn().accountId() : null;
        String state = random();
        String verifier = random();
        request.getSession().setAttribute(STARTED, new Started(state, verifier, safeNext(next), linkTo));
        return redirect(google.authorizationUrl(state, challenge(verifier), site.link(CALLBACK)));
    }

    @GetMapping(CALLBACK)
    ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Started started = session == null ? null : (Started) session.getAttribute(STARTED);
        if (started == null || state == null
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), started.state().getBytes(StandardCharsets.UTF_8))) {
            return redirect("/login?google_error=" + query("Вхід через Google не вдався. Спробуйте ще раз."));
        }
        session.removeAttribute(STARTED);
        boolean linking = started.linkTo() != null;
        String back = linking ? "/me/settings?" : "/login?next=" + query(started.next()) + "&";
        if (code == null) {
            // The person changed their mind on Google's page.
            return redirect(linking ? "/me/settings" : "/login?next=" + query(started.next()));
        }
        try {
            GoogleSignIn.Identity identity = google.identity(code, started.verifier(), site.link(CALLBACK));
            if (linking) {
                if (!currentUser.accountId().equals(Optional.of(started.linkTo()))) {
                    throw UserFacingException.badRequest("Ви вийшли з акаунта, поки були на сторінці Google. Увійдіть і спробуйте ще раз.");
                }
                accounts.linkGoogle(started.linkTo(), identity);
                return redirect("/me/settings?google=linked");
            }
            Optional<AccountService.GoogleMatch> match = accounts.matchGoogle(identity);
            if (match.isPresent()) {
                long id = match.get().accountId();
                if (match.get().tookOver()) {
                    sessions.signOutEverywhere(id);
                }
                sessions.signIn(id, request, response);
                return redirect(started.next());
            }
            session.setAttribute(NEWCOMER, new Newcomer(identity.subject(), identity.email(), identity.name(), started.next()));
            return redirect("/login/google");
        } catch (UserFacingException error) {
            return redirect(back + "google_error=" + query(error.getMessage()));
        }
    }

    /** The newcomer from Google waiting for a nick, with one suggested. */
    @GetMapping("/api/auth/google/newcomer")
    NewcomerView newcomer(HttpServletRequest request) {
        Newcomer newcomer = newcomerOf(request);
        return new NewcomerView(newcomer.email(), suggestNick(newcomer), newcomer.next());
    }

    @PostMapping("/api/auth/google/newcomer")
    Me register(@RequestBody NickRequest body, HttpServletRequest request, HttpServletResponse response) {
        Newcomer newcomer = newcomerOf(request);
        long id = accounts.registerWithGoogle(
                new GoogleSignIn.Identity(newcomer.subject(), newcomer.email(), true, newcomer.name()),
                body.nick(), request.getRemoteAddr());
        request.getSession().removeAttribute(NEWCOMER);
        sessions.signIn(id, request, response);
        return me.find(id).orElseThrow();
    }

    @DeleteMapping("/api/me/google")
    Me unlink() {
        long id = currentUser.requireSignedIn().accountId();
        accounts.unlinkGoogle(id);
        return me.find(id).orElseThrow();
    }

    private static Newcomer newcomerOf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Newcomer newcomer = session == null ? null : (Newcomer) session.getAttribute(NEWCOMER);
        if (newcomer == null) {
            throw UserFacingException.notFound("Час минув. Увійдіть через Google ще раз.");
        }
        return newcomer;
    }

    /** The name from Google as a nick, else the start of the address; empty if neither fits or is free. */
    private String suggestNick(Newcomer newcomer) {
        String local = newcomer.email().contains("@") ? newcomer.email().substring(0, newcomer.email().indexOf('@')) : "";
        return Stream.of(newcomer.name().strip().replaceAll("\\s+", "_"), local)
                .map(candidate -> candidate.replaceAll("[^\\p{L}\\d_-]", ""))
                .filter(GoogleController::validNick)
                .filter(candidate -> !repository.nickTaken(candidate))
                .findFirst().orElse("");
    }

    private static boolean validNick(String candidate) {
        try {
            Handles.check(candidate, "Нік");
            return true;
        } catch (UserFacingException invalid) {
            return false;
        }
    }

    private static String safeNext(String next) {
        return next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : "/";
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(location).toString()).build();
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
