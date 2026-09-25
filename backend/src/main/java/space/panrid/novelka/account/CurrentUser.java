package space.panrid.novelka.account;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import space.panrid.novelka.account.internal.AccountRepository;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Who is making this request. Reads the account once per request from the database,
 * so a changed role or a removed account takes effect without signing out.
 */
@Component
@RequestScope
public class CurrentUser {

    private final AccountRepository accounts;
    private final SiteSettings settings;
    private Optional<Viewer> viewer;

    CurrentUser(AccountRepository accounts, SiteSettings settings) {
        this.accounts = accounts;
        this.settings = settings;
    }

    public Optional<Viewer> viewer() {
        if (viewer == null) {
            viewer = principalId().flatMap(accounts::viewer);
            // With 18+ switched off for the site, nobody counts as having confirmed their age.
            if (viewer.isPresent() && viewer.get().adultConfirmed() && !settings.flag(SiteSettings.ADULT_ENABLED, true)) {
                Viewer v = viewer.get();
                viewer = Optional.of(new Viewer(v.accountId(), v.nick(), v.role(), false));
            }
        }
        return viewer;
    }

    /** The signed-in person, or 401 «Увійдіть, щоб продовжити». */
    public Viewer requireSignedIn() {
        return viewer().orElseThrow(() -> new UserFacingException(HttpStatus.UNAUTHORIZED, "Увійдіть, щоб продовжити."));
    }

    public Optional<Long> accountId() {
        return viewer().map(Viewer::accountId);
    }

    /** The account id from the session, without checking the database. For infrastructure only. */
    public static Optional<Long> signedInPrincipalId() {
        return principalId();
    }

    private static Optional<Long> principalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return Optional.of(principal.accountId());
        }
        return Optional.empty();
    }
}
