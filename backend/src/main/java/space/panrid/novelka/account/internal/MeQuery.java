package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
class MeQuery {

    private final DSLContext db;

    MeQuery(DSLContext db) {
        this.db = db;
    }

    Optional<Me> find(long id) {
        return db.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOptional(r -> new Me(
                r.getId(), r.getNick(), r.getEmail(), r.getEmailVerifiedAt() != null, r.getSiteRole(), r.getBio(),
                null, r.getDmPolicy(), r.getShowReading(), r.getAdultConfirmedAt() != null, r.getShowShah()));
    }
}
