package panrid.space.novelka.server.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.AccountService;
import panrid.space.novelka.server.account.Credentials;
import panrid.space.novelka.server.novel.NovelAccessService;
import panrid.space.novelka.server.settings.SettingsService;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    private final AccessService access;
    private final AccountService accounts;
    private final SettingsService settings;
    private final NovelAccessService novels;

    public AuthController(AccessService access, AccountService accounts, SettingsService settings, NovelAccessService novels) {
        this.access = access;
        this.accounts = accounts;
        this.settings = settings;
        this.novels = novels;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Principal principal) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        var user = access.current(principal);
        result.put("user", user);
        // Whether the correction queue is available: the account translates, edits or reviews at least one novel.
        result.put("canReview", user != null && novels.reviewsAnything(user));
        result.put("registrationOpen", settings.read().registrationOpen());
        return result;
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody Credentials credentials) throws Exception {
        if (!settings.read().registrationOpen()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        accounts.register(credentials);
        return Map.of("message", "Обліковий запис створено. Увійдіть.");
    }
}
