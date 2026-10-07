package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;

/** Keeps how a person wants the site and the reader to look ({@link Appearance}). */
@Component
class AppearanceStore {

    private final DSLContext db;

    AppearanceStore(DSLContext db) {
        this.db = db;
    }

    /** @param appearance already checked by {@link Appearance#checked} */
    void save(long accountId, String appearance) {
        db.update(ACCOUNT).set(ACCOUNT.APPEARANCE, JSONB.valueOf(appearance)).where(ACCOUNT.ID.eq(accountId)).execute();
    }
}
