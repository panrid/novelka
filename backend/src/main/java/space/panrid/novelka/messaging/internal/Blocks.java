package space.panrid.novelka.messaging.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.ACCOUNT_BLOCK;

import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;

/** «Заблокувати»: neither side can write to the other or add them to a group. */
@Component
class Blocks {

    private final DSLContext db;

    Blocks(DSLContext db) {
        this.db = db;
    }

    /** Either one blocked the other. */
    boolean between(long a, long b) {
        return db.fetchExists(ACCOUNT_BLOCK, ACCOUNT_BLOCK.BLOCKER_ID.eq(a).and(ACCOUNT_BLOCK.BLOCKED_ID.eq(b))
                .or(ACCOUNT_BLOCK.BLOCKER_ID.eq(b).and(ACCOUNT_BLOCK.BLOCKED_ID.eq(a))));
    }

    void block(long blocker, long blocked) {
        if (blocker == blocked) {
            throw UserFacingException.badRequest("Себе заблокувати не можна.");
        }
        db.insertInto(ACCOUNT_BLOCK).set(ACCOUNT_BLOCK.BLOCKER_ID, blocker).set(ACCOUNT_BLOCK.BLOCKED_ID, blocked)
                .onConflictDoNothing().execute();
    }

    void unblock(long blocker, long blocked) {
        db.deleteFrom(ACCOUNT_BLOCK).where(ACCOUNT_BLOCK.BLOCKER_ID.eq(blocker), ACCOUNT_BLOCK.BLOCKED_ID.eq(blocked)).execute();
    }

    List<String> blockedBy(long blocker) {
        return db.select(ACCOUNT.NICK).from(ACCOUNT_BLOCK).join(ACCOUNT).on(ACCOUNT.ID.eq(ACCOUNT_BLOCK.BLOCKED_ID))
                .where(ACCOUNT_BLOCK.BLOCKER_ID.eq(blocker)).orderBy(ACCOUNT.NICK).fetch(ACCOUNT.NICK);
    }
}
