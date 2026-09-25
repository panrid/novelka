package space.panrid.novelka.admin.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAT_MESSAGE;
import static space.panrid.novelka.jooq.Tables.COMMENT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.MESSAGE;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REPORT;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.community.CommunityModeration;
import space.panrid.novelka.community.Mentions;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.messaging.MessageModeration;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Reports come in per thing (a comment, a chat line, a message, a picture); a moderator
 * looks at the thing and hides it or dismisses the reports. Moderators see a private
 * message only when someone reported it.
 */
@Service
class Moderation {

    static final Set<String> TARGETS = Set.of("comment", "chat", "message", "image");

    private final DSLContext db;
    private final CommunityModeration community;
    private final MessageModeration messages;
    private final Images images;
    private final Catalog catalog;
    private final Mentions mentions;
    private final AuditLog audit;

    Moderation(DSLContext db, CommunityModeration community, MessageModeration messages, Images images, Catalog catalog,
            Mentions mentions, AuditLog audit) {
        this.db = db;
        this.community = community;
        this.messages = messages;
        this.images = images;
        this.catalog = catalog;
        this.mentions = mentions;
        this.audit = audit;
    }

    // ---- reports -------------------------------------------------------------------------------

    /** One report per person and thing; repeating it just keeps the first. */
    @Transactional
    void report(Viewer reporter, String target, long targetId, String rawReason) {
        String reason = rawReason == null ? "" : rawReason.strip();
        if (target == null || !TARGETS.contains(target)) {
            throw UserFacingException.badRequest("Невідомо, на що скарга.");
        }
        if (reason.isEmpty() || reason.length() > 500) {
            throw UserFacingException.badRequest("Коротко опишіть, що не так (до 500 знаків).");
        }
        if (!exists(target, targetId)) {
            throw UserFacingException.notFound("Цього вже немає.");
        }
        db.insertInto(REPORT).set(REPORT.REPORTER_ID, reporter.accountId()).set(REPORT.TARGET, target)
                .set(REPORT.TARGET_ID, targetId).set(REPORT.REASON, reason).onConflictDoNothing().execute();
    }

    record Preview(String author, String text, String imageUrl, String where, String slug, Integer chapter, String team,
            boolean hidden) {
    }

    record Reported(String target, long targetId, int reports, List<String> reasons, OffsetDateTime firstAt, Preview preview) {
    }

    List<Reported> open() {
        var count = DSL.count().as("reports");
        var first = DSL.min(REPORT.CREATED_AT).as("first_at");
        var reasons = DSL.arrayAgg(REPORT.REASON).as("reasons");
        List<Reported> out = new ArrayList<>();
        for (Record row : db.select(REPORT.TARGET, REPORT.TARGET_ID, count, first, reasons).from(REPORT)
                .where(REPORT.STATE.eq("open")).groupBy(REPORT.TARGET, REPORT.TARGET_ID).orderBy(first).limit(100).fetch()) {
            String target = row.get(REPORT.TARGET);
            long id = row.get(REPORT.TARGET_ID);
            out.add(new Reported(target, id, row.get(count), List.of(row.get(reasons)), row.get(first), preview(target, id)));
        }
        return out;
    }

    int openCount() {
        return db.fetchCount(db.selectDistinct(REPORT.TARGET, REPORT.TARGET_ID).from(REPORT).where(REPORT.STATE.eq("open")));
    }

    /** «Приховати» hides the thing and closes its reports; «Відхилити» only closes them. */
    @Transactional
    void decide(Viewer moderator, String target, long targetId, boolean hide, String reason) {
        if (target == null || !TARGETS.contains(target)) {
            throw UserFacingException.badRequest("Невідомо, що саме.");
        }
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        if (hide) {
            hide(moderator, target, targetId, why);
        }
        db.update(REPORT).set(REPORT.STATE, hide ? "resolved" : "dismissed").set(REPORT.RESOLVED_BY, moderator.accountId())
                .set(REPORT.RESOLVED_AT, DSL.currentOffsetDateTime())
                .where(REPORT.TARGET.eq(target), REPORT.TARGET_ID.eq(targetId), REPORT.STATE.eq("open")).execute();
        if (!hide) {
            audit.record(moderator.accountId(), "dismiss", target, targetId, Map.of());
        }
    }

    // ---- hiding and restoring --------------------------------------------------------------------

    @Transactional
    void hide(Viewer moderator, String target, long targetId, String reason) {
        switch (target) {
            case "comment" -> community.hideComment(targetId, moderator.accountId(), reason);
            case "chat" -> community.hideChat(targetId, moderator.accountId(), reason);
            case "message" -> messages.hide(targetId, moderator.accountId(), reason);
            case "image" -> images.hide(targetId, moderator.accountId(), reason);
            case "edition" -> {
                requireAdmin(moderator);
                catalog.hideEdition(targetId, moderator.accountId(), reason);
            }
            default -> throw UserFacingException.badRequest("Невідомо, що приховати.");
        }
        audit.record(moderator.accountId(), "hide", target, targetId, reason == null ? Map.of() : Map.of("reason", reason));
    }

    @Transactional
    void restore(Viewer moderator, String target, long targetId) {
        switch (target) {
            case "comment" -> community.restoreComment(targetId);
            case "chat" -> community.restoreChat(targetId);
            case "message" -> messages.restore(targetId);
            case "image" -> images.restore(targetId);
            case "edition" -> {
                requireAdmin(moderator);
                catalog.restoreEdition(targetId);
            }
            default -> throw UserFacingException.badRequest("Невідомо, що повернути.");
        }
        audit.record(moderator.accountId(), "restore", target, targetId, Map.of());
    }

    record Hidden(String target, long targetId, OffsetDateTime hiddenAt, String hiddenBy, String reason, Preview preview) {
    }

    /** The latest hidden things of every kind, newest first. */
    List<Hidden> hidden() {
        List<Hidden> out = new ArrayList<>();
        collect(out, "comment", db.select(COMMENT.ID, COMMENT.HIDDEN_AT, COMMENT.HIDDEN_BY, COMMENT.HIDDEN_REASON).from(COMMENT)
                .where(COMMENT.HIDDEN_AT.isNotNull()).orderBy(COMMENT.HIDDEN_AT.desc()).limit(30).fetch());
        collect(out, "chat", db.select(CHAT_MESSAGE.ID, CHAT_MESSAGE.HIDDEN_AT, CHAT_MESSAGE.HIDDEN_BY, CHAT_MESSAGE.HIDDEN_REASON)
                .from(CHAT_MESSAGE).where(CHAT_MESSAGE.HIDDEN_AT.isNotNull()).orderBy(CHAT_MESSAGE.HIDDEN_AT.desc()).limit(30).fetch());
        collect(out, "message", db.select(MESSAGE.ID, MESSAGE.HIDDEN_AT, MESSAGE.HIDDEN_BY, MESSAGE.HIDDEN_REASON).from(MESSAGE)
                .where(MESSAGE.HIDDEN_AT.isNotNull()).orderBy(MESSAGE.HIDDEN_AT.desc()).limit(30).fetch());
        collect(out, "image", db.select(IMAGE.ID, IMAGE.HIDDEN_AT, IMAGE.HIDDEN_BY, IMAGE.HIDDEN_REASON).from(IMAGE)
                .where(IMAGE.HIDDEN_AT.isNotNull()).orderBy(IMAGE.HIDDEN_AT.desc()).limit(30).fetch());
        collect(out, "edition", db.select(EDITION.ID, EDITION.HIDDEN_AT, EDITION.HIDDEN_BY, EDITION.HIDDEN_REASON).from(EDITION)
                .where(EDITION.HIDDEN_AT.isNotNull()).orderBy(EDITION.HIDDEN_AT.desc()).limit(30).fetch());
        out.sort((a, b) -> b.hiddenAt().compareTo(a.hiddenAt()));
        return out;
    }

    private void collect(List<Hidden> out, String target, List<? extends Record> rows) {
        Map<Long, String> nicks = nicks(rows.stream().map(r -> r.get(2, Long.class)).filter(java.util.Objects::nonNull).toList());
        for (Record row : rows) {
            long id = row.get(0, Long.class);
            Long by = row.get(2, Long.class);
            out.add(new Hidden(target, id, row.get(1, OffsetDateTime.class), by == null ? null : nicks.get(by),
                    row.get(3, String.class), preview(target, id)));
        }
    }

    // ---- what a moderator looks at -------------------------------------------------------------

    private Preview preview(String target, long id) {
        switch (target) {
            case "comment" -> {
                Record r = db.select(ACCOUNT.NICK, COMMENT.BODY, NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), COMMENT.CHAPTER_NUMBER,
                                TEAM.HANDLE, COMMENT.HIDDEN_AT)
                        .from(COMMENT).join(ACCOUNT).on(ACCOUNT.ID.eq(COMMENT.AUTHOR_ID)).join(EDITION).on(EDITION.ID.eq(COMMENT.EDITION_ID))
                        .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                        .where(COMMENT.ID.eq(id)).fetchOne();
                return r == null ? gone() : new Preview(r.get(ACCOUNT.NICK), mentions.render(r.get(COMMENT.BODY)), null,
                        r.get(3, String.class), r.get(NOVEL.SLUG), r.get(COMMENT.CHAPTER_NUMBER), r.get(TEAM.HANDLE),
                        r.get(COMMENT.HIDDEN_AT) != null);
            }
            case "chat" -> {
                Record r = db.select(ACCOUNT.NICK, CHAT_MESSAGE.BODY, CHAT_MESSAGE.HIDDEN_AT).from(CHAT_MESSAGE)
                        .join(ACCOUNT).on(ACCOUNT.ID.eq(CHAT_MESSAGE.AUTHOR_ID)).where(CHAT_MESSAGE.ID.eq(id)).fetchOne();
                return r == null ? gone() : new Preview(r.get(ACCOUNT.NICK), mentions.render(r.get(CHAT_MESSAGE.BODY)), null,
                        "загальний чат", null, null, null, r.get(CHAT_MESSAGE.HIDDEN_AT) != null);
            }
            case "message" -> {
                Record r = db.select(ACCOUNT.NICK, MESSAGE.BODY, MESSAGE.HIDDEN_AT).from(MESSAGE)
                        .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(MESSAGE.AUTHOR_ID)).where(MESSAGE.ID.eq(id)).fetchOne();
                return r == null ? gone() : new Preview(r.get(ACCOUNT.NICK), mentions.render(r.get(MESSAGE.BODY)), null,
                        "особисті повідомлення", null, null, null, r.get(MESSAGE.HIDDEN_AT) != null);
            }
            case "image" -> {
                Record r = db.select(ACCOUNT.NICK, IMAGE.HIDDEN_AT, IMAGE.KIND).from(IMAGE).join(ACCOUNT).on(ACCOUNT.ID.eq(IMAGE.OWNER_ACCOUNT_ID))
                        .where(IMAGE.ID.eq(id)).fetchOne();
                // A hidden picture has no address any more; the moderator sees whose it was and what for.
                String url = r == null ? null : images.find(id).map(image -> image.url(640)).orElse(null);
                return r == null ? gone() : new Preview(r.get(ACCOUNT.NICK), null, url, kindName(r.get(IMAGE.KIND)), null, null, null,
                        r.get(IMAGE.HIDDEN_AT) != null);
            }
            case "edition" -> {
                Record r = db.select(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), NOVEL.SLUG, TEAM.HANDLE, EDITION.HIDDEN_AT).from(EDITION)
                        .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID)).where(EDITION.ID.eq(id)).fetchOne();
                return r == null ? gone() : new Preview(r.get(TEAM.HANDLE), r.get(0, String.class), null, "переклад", r.get(NOVEL.SLUG),
                        null, r.get(TEAM.HANDLE), r.get(EDITION.HIDDEN_AT) != null);
            }
            default -> {
                return gone();
            }
        }
    }

    private static String kindName(String kind) {
        return switch (kind) {
            case "avatar" -> "аватарка";
            case "group_avatar" -> "картинка групи";
            case "cover" -> "обкладинка";
            case "message" -> "картинка в повідомленні";
            default -> "ілюстрація";
        };
    }

    private static Preview gone() {
        return new Preview(null, "Цього вже немає.", null, null, null, null, null, false);
    }

    private boolean exists(String target, long id) {
        return switch (target) {
            case "comment" -> db.fetchExists(COMMENT, COMMENT.ID.eq(id));
            case "chat" -> db.fetchExists(CHAT_MESSAGE, CHAT_MESSAGE.ID.eq(id));
            case "message" -> db.fetchExists(MESSAGE, MESSAGE.ID.eq(id));
            default -> db.fetchExists(IMAGE, IMAGE.ID.eq(id));
        };
    }

    private Map<Long, String> nicks(List<Long> ids) {
        return ids.isEmpty() ? new HashMap<>() : db.select(ACCOUNT.ID, ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.in(ids))
                .fetchMap(ACCOUNT.ID, ACCOUNT.NICK);
    }

    static void requireAdmin(Viewer viewer) {
        if (!viewer.role().atLeast(SiteRole.ADMIN)) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Приховувати переклади можуть адміністратори.");
        }
    }
}
