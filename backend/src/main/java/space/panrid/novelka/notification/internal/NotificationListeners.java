package space.panrid.novelka.notification.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.LIBRARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import space.panrid.novelka.community.ChatMentioned;
import space.panrid.novelka.community.CommentPosted;
import space.panrid.novelka.suggestion.SuggestionsReviewed;
import space.panrid.novelka.suggestion.SuggestionsSubmitted;
import space.panrid.novelka.text.ChaptersPublished;

/**
 * Who hears about what. A person gets one row per event even when several reasons meet
 * (a reply that also mentions them is a reply), and never hears about their own actions.
 */
@Component
class NotificationListeners {

    private final DSLContext db;
    private final Inbox inbox;

    NotificationListeners(DSLContext db, Inbox inbox) {
        this.db = db;
        this.inbox = inbox;
    }

    @ApplicationModuleListener
    void on(CommentPosted comment) {
        Map<String, Object> base = place(comment.editionId(), comment.chapterNumber());
        base.put("actorNick", nick(comment.authorId()));
        base.put("excerpt", comment.excerpt());
        base.put("commentId", comment.commentId());
        base.put("where", "comment");
        Set<Long> told = new LinkedHashSet<>();
        told.add(comment.authorId());
        if (comment.replyToAuthorId() != null && told.add(comment.replyToAuthorId())) {
            inbox.add(comment.replyToAuthorId(), "reply", base);
        }
        mentions(comment.mentionedAccounts(), comment.mentionedTeams(), base, told);
    }

    @ApplicationModuleListener
    void on(ChatMentioned line) {
        Map<String, Object> base = new HashMap<>();
        base.put("actorNick", nick(line.authorId()));
        base.put("excerpt", line.excerpt());
        base.put("messageId", line.messageId());
        base.put("where", "chat");
        Set<Long> told = new LinkedHashSet<>();
        told.add(line.authorId());
        mentions(line.mentionedAccounts(), line.mentionedTeams(), base, told);
    }

    /** Readers who keep the translation in «Читаю» or «В планах» — not everyone (as in v1). */
    @ApplicationModuleListener
    void on(ChaptersPublished published) {
        Map<String, Object> base = place(published.editionId(), null);
        List<Long> readers = db.select(LIBRARY_ENTRY.ACCOUNT_ID).from(LIBRARY_ENTRY)
                .where(LIBRARY_ENTRY.EDITION_ID.eq(published.editionId()), LIBRARY_ENTRY.LIST.in("reading", "planned"))
                .fetch(LIBRARY_ENTRY.ACCOUNT_ID);
        for (long reader : readers) {
            inbox.addGrouped(reader, "new_chapters", "chapters:" + published.editionId(), base, published.first(), published.last());
        }
    }

    @ApplicationModuleListener
    void on(SuggestionsReviewed reviewed) {
        Map<String, Object> base = place(reviewed.editionId(), reviewed.chapterNumber());
        base.put("accepted", reviewed.accepted());
        base.put("rejected", reviewed.rejected());
        inbox.add(reviewed.authorId(), "suggestions_reviewed", base);
    }

    /** Everyone in the team may review, so everyone hears; one unread row per translation counts them up. */
    @ApplicationModuleListener
    void on(SuggestionsSubmitted submitted) {
        Map<String, Object> base = place(submitted.editionId(), null);
        base.put("actorNick", nick(submitted.authorId()));
        long team = db.select(EDITION.TEAM_ID).from(EDITION).where(EDITION.ID.eq(submitted.editionId())).fetchSingle(EDITION.TEAM_ID);
        Set<Long> people = new LinkedHashSet<>();
        people.add(db.select(TEAM.OWNER_ID).from(TEAM).where(TEAM.ID.eq(team)).fetchSingle(TEAM.OWNER_ID));
        people.addAll(db.select(TEAM_MEMBER.ACCOUNT_ID).from(TEAM_MEMBER).where(TEAM_MEMBER.TEAM_ID.eq(team)).fetch(TEAM_MEMBER.ACCOUNT_ID));
        people.remove(submitted.authorId());
        for (long person : people) {
            inbox.addCounted(person, "suggestions_submitted", "suggestions:" + submitted.editionId(), base, submitted.count());
        }
    }

    private void mentions(List<Long> accounts, List<Long> teams, Map<String, Object> base, Set<Long> told) {
        for (long account : accounts) {
            if (told.add(account)) {
                inbox.add(account, "mention", base);
            }
        }
        for (long team : teams) {
            Record row = db.select(TEAM.HANDLE, TEAM.OWNER_ID).from(TEAM).where(TEAM.ID.eq(team)).fetchOne();
            if (row == null) {
                continue;
            }
            // $команда reaches the owner and translators (рішення 11), not editors.
            List<Long> people = new java.util.ArrayList<>(List.of(row.get(TEAM.OWNER_ID)));
            people.addAll(db.select(TEAM_MEMBER.ACCOUNT_ID).from(TEAM_MEMBER)
                    .where(TEAM_MEMBER.TEAM_ID.eq(team), TEAM_MEMBER.ROLE.in("owner", "translator")).fetch(TEAM_MEMBER.ACCOUNT_ID));
            Map<String, Object> payload = new HashMap<>(base);
            payload.put("teamHandle", row.get(TEAM.HANDLE));
            for (long person : people) {
                if (told.add(person)) {
                    inbox.add(person, "team_mention", payload);
                }
            }
        }
    }

    private Map<String, Object> place(long editionId, Integer chapter) {
        Record row = db.select(NOVEL.SLUG, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), TEAM.HANDLE)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.ID.eq(editionId)).fetchSingle();
        Map<String, Object> payload = new HashMap<>();
        payload.put("editionId", editionId);
        payload.put("slug", row.get(NOVEL.SLUG));
        payload.put("novelTitle", row.get(1, String.class));
        payload.put("teamHandle", row.get(TEAM.HANDLE));
        if (chapter != null) {
            payload.put("chapterNumber", chapter);
            String label = db.select(CHAPTER.LABEL).from(CHAPTER)
                    .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(chapter)).fetchOne(CHAPTER.LABEL);
            payload.put("chapterLabel", label == null ? String.valueOf(chapter) : label);
        }
        return payload;
    }

    private String nick(long accountId) {
        return db.select(ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOne(ACCOUNT.NICK);
    }
}
