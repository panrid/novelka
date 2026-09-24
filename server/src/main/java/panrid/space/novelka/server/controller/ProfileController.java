package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.AccountEmailService;
import panrid.space.novelka.server.account.AccountService;
import panrid.space.novelka.server.account.EmailChange;
import panrid.space.novelka.server.account.NicknameChange;
import panrid.space.novelka.server.account.Role;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public final class ProfileController {
    private final AccessService access;
    private final AccountService accounts;
    private final AccountEmailService emails;

    public ProfileController(AccessService access, AccountService accounts, AccountEmailService emails) {
        this.access = access;
        this.accounts = accounts;
        this.emails = emails;
    }

    @GetMapping
    public Map<String, Object> profile(Principal principal) throws Exception {
        return accounts.profile(access.require(principal, Role.READER));
    }

    @PostMapping("/nickname")
    public Map<String, Object> nickname(Principal principal, @RequestBody NicknameChange request) throws Exception {
        return accounts.profile(accounts.changeNickname(access.require(principal, Role.READER), request.nickname()));
    }

    @PostMapping("/email")
    public Map<String, Object> email(Principal principal, @RequestBody EmailChange request) throws Exception {
        var account = access.require(principal, Role.READER);
        accounts.changeEmail(account, request.email(), request.password());
        emails.verificationAfterChange(account);
        return accounts.profile(account);
    }

    @PostMapping("/email/verification")
    public Map<String, String> sendVerification(Principal principal) throws Exception {
        emails.requestVerification(access.require(principal, Role.READER));
        return Map.of("message", "Лист надіслано. Перевірте пошту, зокрема папку «Спам».");
    }
}
