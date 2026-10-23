package space.panrid.novelka.apitoken.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.apitoken.internal.ApiTokenService.Created;
import space.panrid.novelka.apitoken.internal.ApiTokenService.TokenView;
import space.panrid.novelka.apitoken.internal.ApiTokenService.Translation;
import space.panrid.novelka.platform.security.TokenGrant;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * «Я → Налаштування → API» (етап 22.1) and the site owner's list of every token. Only with the
 * session: a token can neither make nor see tokens ({@link TokenScopes} refuses it before it gets
 * here; this checks once more).
 */
@RestController
class ApiTokenController {

    /**
     * @param days    how long it works: 7, 30, 90 or 365 days
     * @param edition the one translation it is limited to; null — all the person's
     */
    record NewToken(String name, List<String> scopes, Integer days, Long edition) {
    }

    /**
     * @param offered      the scopes this person may give a token
     * @param translations the translations it may be limited to
     */
    record Tokens(List<TokenView> tokens, List<String> offered, List<Integer> days, List<Translation> translations) {
    }

    private final ApiTokenService tokens;
    private final AccessPolicy access;

    ApiTokenController(ApiTokenService tokens, AccessPolicy access) {
        this.tokens = tokens;
        this.access = access;
    }

    @GetMapping("/api/me/api-tokens")
    Tokens mine(HttpServletRequest request) {
        Viewer viewer = signedInBySession(request);
        return new Tokens(tokens.mine(viewer.accountId()), Scope.offeredTo(viewer.role()).stream().map(Scope::code).toList(),
                ApiTokenService.DAYS, tokens.translations(viewer.accountId()));
    }

    @PostMapping("/api/me/api-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    Created create(@RequestBody NewToken body, HttpServletRequest request) {
        return tokens.create(signedInBySession(request), body.name(), body.scopes(), body.days(), body.edition());
    }

    @DeleteMapping("/api/me/api-tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable long id, HttpServletRequest request) {
        tokens.revoke(signedInBySession(request), id);
    }

    /** Every live token of the site: the owner sees them all and may revoke any. */
    @GetMapping("/api/admin/api-tokens")
    List<TokenView> all(HttpServletRequest request) {
        signedInBySession(request);
        access.requireSiteRole(SiteRole.OWNER);
        return tokens.all();
    }

    @DeleteMapping("/api/admin/api-tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeAny(@PathVariable long id, HttpServletRequest request) {
        signedInBySession(request);
        tokens.revokeAny(access.requireSiteRole(SiteRole.OWNER), id);
    }

    private Viewer signedInBySession(HttpServletRequest request) {
        if (TokenGrant.of(request) != null) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Це можна зробити лише на сайті, не токеном.");
        }
        return access.requireSignedIn();
    }
}
