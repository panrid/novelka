package space.panrid.novelka.access;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.web.UserFacingException;

/** Site-wide checks. Team and translation rules join here in later stages. */
@Component
public class AccessPolicy {

    private final CurrentUser currentUser;

    AccessPolicy(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /** The signed-in person, or 401 «Увійдіть, щоб продовжити». */
    public Viewer requireSignedIn() {
        return currentUser.viewer().orElseThrow(() ->
                new UserFacingException(HttpStatus.UNAUTHORIZED, "Увійдіть, щоб продовжити."));
    }

    /** The signed-in person whose site role is at least {@code role}, or 403. */
    public Viewer requireSiteRole(SiteRole role) {
        Viewer viewer = requireSignedIn();
        if (!viewer.role().atLeast(role)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Це вам недоступно.");
        }
        return viewer;
    }
}
