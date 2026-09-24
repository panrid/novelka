package space.panrid.novelka.account.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import space.panrid.novelka.account.CurrentUser;

@RestController
@RequestMapping("/api")
class AuthController {

    record RegisterRequest(String nick, String email, String password) {
    }

    record LoginRequest(String login, String password) {
    }

    record TokenRequest(String token) {
    }

    record EmailRequest(String email) {
    }

    record ResetRequest(String token, String password) {
    }

    private final AccountService service;
    private final Sessions sessions;
    private final MeQuery me;
    private final CurrentUser currentUser;

    AuthController(AccountService service, Sessions sessions, MeQuery me, CurrentUser currentUser) {
        this.service = service;
        this.sessions = sessions;
        this.me = me;
        this.currentUser = currentUser;
    }

    /** The signed-in account, or 204 for a guest. */
    @GetMapping("/me")
    ResponseEntity<Me> me() {
        return currentUser.accountId().flatMap(me::find)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        service.register(body.nick(), body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/auth/verify")
    Me verify(@RequestBody TokenRequest body, HttpServletRequest request, HttpServletResponse response) {
        long id = service.verify(body.token());
        sessions.signIn(id, request, response);
        return me.find(id).orElseThrow();
    }

    @PostMapping("/auth/verify/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resend(@RequestBody EmailRequest body) {
        service.resendVerification(body.email());
    }

    @PostMapping("/auth/login")
    Me login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        long id = service.authenticate(body.login(), body.password(), request.getRemoteAddr());
        sessions.signIn(id, request, response);
        return me.find(id).orElseThrow();
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        sessions.signOut(request);
    }

    @PostMapping("/auth/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@RequestBody EmailRequest body) {
        service.requestPasswordReset(body.email());
    }

    @PostMapping("/auth/password-reset/confirm")
    Me confirmReset(@RequestBody ResetRequest body, HttpServletRequest request, HttpServletResponse response) {
        long id = service.resetPassword(body.token(), body.password());
        sessions.signOutEverywhere(id);
        sessions.signIn(id, request, response);
        return me.find(id).orElseThrow();
    }
}
