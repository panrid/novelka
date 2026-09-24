package space.panrid.novelka.account.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import space.panrid.novelka.platform.web.UserFacingException;

@RestController
class ProfileController {

    record TokenRequest(String token) {
    }

    private final AccountRepository accounts;
    private final AccountService service;
    private final MeQuery query;
    private final Sessions sessions;

    ProfileController(AccountRepository accounts, AccountService service, MeQuery query, Sessions sessions) {
        this.accounts = accounts;
        this.service = service;
        this.query = query;
        this.sessions = sessions;
    }

    /** Public page of a person. A former nick answers with the current one, so the page can redirect. */
    @GetMapping("/api/users/{nick}")
    MeQuery.PublicProfile profile(@PathVariable String nick) {
        return accounts.idByNickOrFormerNick(nick)
                .flatMap(query::publicProfile)
                .orElseThrow(() -> UserFacingException.notFound("Такого користувача немає."));
    }

    /** Opens the link from the «нова пошта» letter. */
    @PostMapping("/api/auth/confirm-email")
    Me confirmEmail(@RequestBody TokenRequest body, HttpServletRequest request, HttpServletResponse response) {
        long id = service.confirmEmailChange(body.token());
        sessions.signIn(id, request, response);
        return query.find(id).orElseThrow();
    }
}
