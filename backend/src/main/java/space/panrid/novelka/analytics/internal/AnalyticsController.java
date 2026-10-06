package space.panrid.novelka.analytics.internal;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.platform.web.UserFacingException;

/** The owner's analytics: money spent on models, how good each model is, how the site lives. */
@RestController
class AnalyticsController {

    private static final Set<Integer> PERIODS = Set.of(0, 7, 30, 90, 365);

    private final AccessPolicy access;
    private final Analytics analytics;

    AnalyticsController(AccessPolicy access, Analytics analytics) {
        this.access = access;
        this.analytics = analytics;
    }

    /** @param days 7, 30, 90, 365, or 0 for all time */
    @GetMapping("/api/admin/analytics")
    Report report(@RequestParam(defaultValue = "30") int days) {
        access.requireSiteRole(SiteRole.OWNER);
        if (!PERIODS.contains(days)) {
            throw UserFacingException.badRequest("Невідомий період.");
        }
        return analytics.report(days);
    }
}
