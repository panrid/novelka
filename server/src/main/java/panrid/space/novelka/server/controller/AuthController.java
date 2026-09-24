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
import panrid.space.novelka.server.account.AccountEmailService;
import panrid.space.novelka.server.account.AccountService;
import panrid.space.novelka.server.account.Credentials;
import panrid.space.novelka.server.account.EmailTokenRequest;
import panrid.space.novelka.server.account.PasswordResetConfirm;
import panrid.space.novelka.server.account.PasswordResetRequest;
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
    private final AccountEmailService emails;

    public AuthController(AccessService access, AccountService accounts, SettingsService settings, NovelAccessService novels,
            AccountEmailService emails) {
        this.access = access;
        this.accounts = accounts;
        this.settings = settings;
        this.novels = novels;
        this.emails = emails;
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
        emails.verificationAfterChange(accounts.register(credentials));
        return Map.of("message", "Обліковий запис створено. Ми надіслали лист для підтвердження email. Увійдіть.");
    }

    /** Always the same answer, so the form does not reveal which addresses have accounts. */
    @PostMapping("/password-reset")
    public Map<String, String> requestReset(@RequestBody PasswordResetRequest request) throws Exception {
        emails.requestReset(request.email());
        return Map.of("message", "Якщо акаунт із цією адресою існує, ми надіслали лист із посиланням. Воно діє 30 хвилин.");
    }

    @PostMapping("/password-reset/confirm")
    public Map<String, String> confirmReset(@RequestBody PasswordResetConfirm request) throws Exception {
        emails.resetPassword(request.token(), request.password());
        return Map.of("message", "Пароль змінено. Увійдіть із новим паролем.");
    }

    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@RequestBody EmailTokenRequest request) throws Exception {
        emails.verify(request.token());
        return Map.of("message", "Email підтверджено. Дякуємо!");
    }
}
