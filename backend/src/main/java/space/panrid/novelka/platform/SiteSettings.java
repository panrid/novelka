package space.panrid.novelka.platform;

import static space.panrid.novelka.jooq.Tables.SITE_SETTING;

import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
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

    /** The stored JSON of a setting. */
    public Optional<String> json(String key) {
        return db.select(SITE_SETTING.VALUE).from(SITE_SETTING).where(SITE_SETTING.KEY.eq(key))
                .fetchOptional(SITE_SETTING.VALUE).map(JSONB::data);
    }

    public void put(String key, String json, Long changedBy) {
        db.insertInto(SITE_SETTING)
                .set(SITE_SETTING.KEY, key)
                .set(SITE_SETTING.VALUE, JSONB.valueOf(json))
                .set(SITE_SETTING.UPDATED_BY, changedBy)
                .onConflict(SITE_SETTING.KEY).doUpdate()
                .set(SITE_SETTING.VALUE, JSONB.valueOf(json))
                .set(SITE_SETTING.UPDATED_BY, changedBy)
                .set(SITE_SETTING.UPDATED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
                .execute();
    }
}
