package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.CHAT_MESSAGE;
import static space.panrid.novelka.jooq.Tables.COMMENT;

import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.community.CommunityModeration;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;

@Service
class ModerationService implements CommunityModeration {

    private final DSLContext db;
    private final LiveEvents live;

    ModerationService(DSLContext db, LiveEvents live) {
        this.db = db;
        this.live = live;
    }

    @Override
    @Transactional
    public void hideComment(long commentId, long moderatorId, String reason) {
        db.update(COMMENT).set(COMMENT.HIDDEN_AT, DSL.currentOffsetDateTime()).set(COMMENT.HIDDEN_BY, moderatorId)
                .set(COMMENT.HIDDEN_REASON, reason).where(COMMENT.ID.eq(commentId), COMMENT.HIDDEN_AT.isNull()).execute();
    }

    @Override
    @Transactional
    public void restoreComment(long commentId) {
        db.update(COMMENT).setNull(COMMENT.HIDDEN_AT).setNull(COMMENT.HIDDEN_BY).setNull(COMMENT.HIDDEN_REASON)
                .where(COMMENT.ID.eq(commentId)).execute();
    }

    @Override
    @Transactional
    public void hideChat(long lineId, long moderatorId, String reason) {
        db.update(CHAT_MESSAGE).set(CHAT_MESSAGE.HIDDEN_AT, DSL.currentOffsetDateTime()).set(CHAT_MESSAGE.HIDDEN_BY, moderatorId)
                .set(CHAT_MESSAGE.HIDDEN_REASON, reason).where(CHAT_MESSAGE.ID.eq(lineId), CHAT_MESSAGE.HIDDEN_AT.isNull()).execute();
        AfterCommit.run(() -> live.sendAll("chat", Map.of("removed", lineId)));
    }

    @Override
    @Transactional
    public void restoreChat(long lineId) {
        db.update(CHAT_MESSAGE).setNull(CHAT_MESSAGE.HIDDEN_AT).setNull(CHAT_MESSAGE.HIDDEN_BY).setNull(CHAT_MESSAGE.HIDDEN_REASON)
                .where(CHAT_MESSAGE.ID.eq(lineId)).execute();
        AfterCommit.run(() -> live.sendAll("chat", Map.of("restored", lineId)));
    }
}
