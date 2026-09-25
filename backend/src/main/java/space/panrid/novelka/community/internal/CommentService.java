package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.COMMENT;
import static space.panrid.novelka.jooq.Tables.COMMENT_VOTE;
import static space.panrid.novelka.jooq.Tables.EDITION;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.community.CommentPosted;
import space.panrid.novelka.community.Mentions;
import space.panrid.novelka.jooq.tables.records.CommentRecord;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Threads one level deep: a reply always hangs under the top-level comment, and answering
 * a reply mentions its author instead. A removed comment with replies stays as a stub.
 */
@Service
class CommentService {

    static final int PAGE = 20;
    private static final int MAX_REPLIES = 100;

    private final DSLContext db;
    private final Mentions mentions;
    private final People people;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RateLimiter posting;
    private final space.panrid.novelka.platform.audit.AuditLog audit;

    CommentService(DSLContext db, Mentions mentions, People people, ApplicationEventPublisher events, Clock clock,
            space.panrid.novelka.platform.audit.AuditLog audit) {
        this.audit = audit;
        this.db = db;
        this.mentions = mentions;
        this.people = people;
        this.events = events;
        this.clock = clock;
        this.posting = new RateLimiter(8, Duration.ofMinutes(1), clock);
    }

    record Item(long id, String authorNick, String authorAvatarUrl, String body, OffsetDateTime createdAt,
            OffsetDateTime editedAt, String removed, int score, int myVote, boolean mine, Long replyTo, List<Item> replies) {
    }

    record Thread(List<Item> items, int total, int page, boolean hasMore) {
    }

    Thread list(long editionId, Integer chapter, String sort, int page, Long viewerId) {
        requireEdition(editionId);
        Condition place = place(editionId, chapter);
        Condition shown = COMMENT.DELETED_AT.isNull().and(COMMENT.HIDDEN_AT.isNull());
        // A removed top-level comment stays only while it has replies to hold together.
        Condition rootVisible = shown.or(DSL.exists(DSL.selectOne().from(COMMENT.as("r"))
                .where(COMMENT.as("r").REPLY_TO.eq(COMMENT.ID), COMMENT.as("r").DELETED_AT.isNull(), COMMENT.as("r").HIDDEN_AT.isNull())));
        int total = db.fetchCount(COMMENT, place.and(shown));
        List<CommentRecord> roots = db.selectFrom(COMMENT)
                .where(place, COMMENT.REPLY_TO.isNull(), rootVisible)
                .orderBy("top".equals(sort) ? List.of(COMMENT.SCORE.desc(), COMMENT.ID.desc()) : List.of(COMMENT.ID.desc()))
                .limit(PAGE + 1).offset((Math.max(1, page) - 1) * PAGE)
                .fetch();
        boolean more = roots.size() > PAGE;
        if (more) {
            roots = roots.subList(0, PAGE);
        }
        List<Long> rootIds = roots.stream().map(CommentRecord::getId).toList();
        List<CommentRecord> replies = rootIds.isEmpty() ? List.of() : db.selectFrom(COMMENT)
                .where(COMMENT.REPLY_TO.in(rootIds), shown).orderBy(COMMENT.ID).fetch();
        List<CommentRecord> all = new ArrayList<>(roots);
        all.addAll(replies);
        return new Thread(items(roots, replies, all, viewerId), total, Math.max(1, page), more);
    }

    private List<Item> items(List<CommentRecord> roots, List<CommentRecord> replies, List<CommentRecord> all, Long viewerId) {
        Set<Long> authors = new HashSet<>();
        all.forEach(c -> authors.add(c.getAuthorId()));
        Map<Long, People.Person> names = people.of(authors);
        List<String> bodies = mentions.render(all.stream().map(CommentRecord::getBody).toList());
        Map<Long, String> rendered = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i++) {
            rendered.put(all.get(i).getId(), bodies.get(i));
        }
        Map<Long, Integer> myVotes = viewerId == null || all.isEmpty() ? Map.of()
                : db.select(COMMENT_VOTE.COMMENT_ID, COMMENT_VOTE.VALUE).from(COMMENT_VOTE)
                        .where(COMMENT_VOTE.ACCOUNT_ID.eq(viewerId), COMMENT_VOTE.COMMENT_ID.in(rendered.keySet()))
                        .fetchMap(r -> r.value1(), r -> r.value2().intValue());
        Map<Long, List<Item>> byRoot = new LinkedHashMap<>();
        for (CommentRecord reply : replies) {
            List<Item> list = byRoot.computeIfAbsent(reply.getReplyTo(), key -> new ArrayList<>());
            if (list.size() < MAX_REPLIES) {
                list.add(item(reply, names, rendered, myVotes, viewerId, List.of()));
            }
        }
        return roots.stream().map(root -> item(root, names, rendered, myVotes, viewerId, byRoot.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    private static Item item(CommentRecord c, Map<Long, People.Person> names, Map<Long, String> bodies, Map<Long, Integer> votes,
            Long viewerId, List<Item> replies) {
        String removed = c.getHiddenAt() != null ? "hidden" : c.getDeletedAt() != null ? "deleted" : null;
        People.Person author = names.get(c.getAuthorId());
        return new Item(c.getId(), removed == null ? author.nick() : null, removed == null ? author.avatarUrl() : null,
                removed == null ? bodies.get(c.getId()) : "", c.getCreatedAt(), c.getEditedAt(), removed, c.getScore(),
                votes.getOrDefault(c.getId(), 0), viewerId != null && viewerId == c.getAuthorId(), c.getReplyTo(), replies);
    }

    @Transactional
    long post(Viewer author, long editionId, Integer chapter, String text, Long replyTo) {
        requireEdition(editionId);
        if (!posting.tryAcquire("comment:" + author.accountId())) {
            throw UserFacingException.tooManyRequests();
        }
        String clean = body(text);
        Long rootId = null;
        Long answeredAuthor = null;
        if (replyTo != null) {
            CommentRecord answered = db.selectFrom(COMMENT).where(COMMENT.ID.eq(replyTo)).fetchOptional()
                    .filter(c -> c.getEditionId() == editionId && java.util.Objects.equals(c.getChapterNumber(), chapter))
                    .orElseThrow(() -> UserFacingException.notFound("Такого коментаря немає."));
            rootId = answered.getReplyTo() != null ? answered.getReplyTo() : answered.getId();
            answeredAuthor = answered.getAuthorId();
        }
        Mentions.Encoded encoded = mentions.encode(clean);
        long id = db.insertInto(COMMENT)
                .set(COMMENT.EDITION_ID, editionId)
                .set(COMMENT.CHAPTER_NUMBER, chapter)
                .set(COMMENT.AUTHOR_ID, author.accountId())
                .set(COMMENT.REPLY_TO, rootId)
                .set(COMMENT.BODY, encoded.body())
                .set(COMMENT.CREATED_AT, now())
                .returning(COMMENT.ID).fetchSingle().getId();
        events.publishEvent(new CommentPosted(id, editionId, chapter, author.accountId(), answeredAuthor,
                encoded.accounts(), encoded.teams(), mentions.excerpt(encoded.body(), 140)));
        return id;
    }

    @Transactional
    void edit(Viewer author, long commentId, String text) {
        CommentRecord comment = existing(commentId);
        if (comment.getAuthorId() != author.accountId()) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Змінювати можна лише свої коментарі.");
        }
        db.update(COMMENT).set(COMMENT.BODY, mentions.encode(body(text)).body()).set(COMMENT.EDITED_AT, now())
                .where(COMMENT.ID.eq(commentId)).execute();
    }

    /** The author deletes; a moderator hides, with a reason kept for the record. */
    @Transactional
    void remove(Viewer viewer, long commentId, String reason) {
        CommentRecord comment = existing(commentId);
        if (comment.getAuthorId() == viewer.accountId()) {
            db.update(COMMENT).set(COMMENT.DELETED_AT, now()).where(COMMENT.ID.eq(commentId)).execute();
        } else if (viewer.role().atLeast(SiteRole.MODERATOR)) {
            String why = reason == null || reason.isBlank() ? null : reason.strip();
            db.update(COMMENT).set(COMMENT.HIDDEN_AT, now()).set(COMMENT.HIDDEN_BY, viewer.accountId())
                    .set(COMMENT.HIDDEN_REASON, why)
                    .where(COMMENT.ID.eq(commentId)).execute();
            audit.record(viewer.accountId(), "hide", "comment", commentId, why == null ? java.util.Map.of() : java.util.Map.of("reason", why));
        } else {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Видаляти можна лише свої коментарі.");
        }
    }

    @Transactional
    int vote(Viewer voter, long commentId, int value) {
        CommentRecord comment = existing(commentId);
        if (comment.getAuthorId() == voter.accountId()) {
            throw UserFacingException.badRequest("За свій коментар голосувати не можна.");
        }
        if (value == 0) {
            db.deleteFrom(COMMENT_VOTE).where(COMMENT_VOTE.COMMENT_ID.eq(commentId), COMMENT_VOTE.ACCOUNT_ID.eq(voter.accountId())).execute();
        } else {
            short vote = (short) Integer.signum(value);
            db.insertInto(COMMENT_VOTE).set(COMMENT_VOTE.COMMENT_ID, commentId).set(COMMENT_VOTE.ACCOUNT_ID, voter.accountId())
                    .set(COMMENT_VOTE.VALUE, vote)
                    .onConflict(COMMENT_VOTE.COMMENT_ID, COMMENT_VOTE.ACCOUNT_ID).doUpdate().set(COMMENT_VOTE.VALUE, vote)
                    .execute();
        }
        int score = db.select(DSL.coalesce(DSL.sum(COMMENT_VOTE.VALUE), 0).cast(Integer.class)).from(COMMENT_VOTE)
                .where(COMMENT_VOTE.COMMENT_ID.eq(commentId)).fetchSingle().value1();
        db.update(COMMENT).set(COMMENT.SCORE, score).where(COMMENT.ID.eq(commentId)).execute();
        return score;
    }

    private CommentRecord existing(long commentId) {
        return db.selectFrom(COMMENT).where(COMMENT.ID.eq(commentId), COMMENT.DELETED_AT.isNull(), COMMENT.HIDDEN_AT.isNull())
                .fetchOptional().orElseThrow(() -> UserFacingException.notFound("Такого коментаря немає."));
    }

    private void requireEdition(long editionId) {
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId).and(EDITION.HIDDEN_AT.isNull()))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
    }

    private static Condition place(long editionId, Integer chapter) {
        return COMMENT.EDITION_ID.eq(editionId)
                .and(chapter == null ? COMMENT.CHAPTER_NUMBER.isNull() : COMMENT.CHAPTER_NUMBER.eq(chapter));
    }

    static String body(String text) {
        String clean = text == null ? "" : text.replace("\r", "").strip();
        if (clean.isEmpty()) {
            throw UserFacingException.badRequest("Коментар порожній.");
        }
        if (clean.length() > 4000) {
            throw UserFacingException.badRequest("Коментар задовгий: до 4000 знаків.");
        }
        return clean;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
