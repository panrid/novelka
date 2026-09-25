package space.panrid.novelka.platform;

import static space.panrid.novelka.jooq.Tables.SITE_SETTING;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/** Values the site owner changes without a redeploy; a missing key means the code default. */
@Component
public class SiteSettings {

    /** Months of silence after which a translation is free to continue (рішення 4). */
    public static final String RELAY_INACTIVE_MONTHS = "relay.inactive_months";

    private final DSLContext db;

    SiteSettings(DSLContext db) {
        this.db = db;
    }

    public int integer(String key, int fallback) {
        return db.select(SITE_SETTING.VALUE).from(SITE_SETTING).where(SITE_SETTING.KEY.eq(key))
                .fetchOptional(SITE_SETTING.VALUE)
                .map(value -> {
                    try {
                        return Integer.parseInt(value.data().strip());
                    } catch (NumberFormatException wrong) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
