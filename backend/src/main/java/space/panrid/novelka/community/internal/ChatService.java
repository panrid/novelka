package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.CHAT_MESSAGE;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.community.ChatMentioned;
import space.panrid.novelka.community.Mentions;
import space.panrid.novelka.jooq.tables.records.ChatMessageRecord;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;

/** The one site-wide chat. Guests read it; signed-in people write. */
@Service
class ChatService {

    private static final int PAGE = 50;

    private final DSLContext db;
    private final Mentions mentions;
    private final People people;
    private final ApplicationEventPublisher events;
    private final LiveEvents live;
    private final Clock clock;
    private final RateLimiter writing;

    ChatService(DSLContext db, Mentions mentions, People people, ApplicationEventPublisher events, LiveEvents live, Clock clock) {
        this.db = db;
        this.mentions = mentions;
        this.people = people;
        this.events = events;
        this.live = live;
        this.clock = clock;
        this.writing = new RateLimiter(15, Duration.ofMinutes(1), clock);
    }

    record Line(long id, String authorNick, String authorAvatarUrl, String body, OffsetDateTime createdAt, Long replyTo,
            String replyExcerpt, boolean mine) {
    }

    /** The latest lines, or older ones before {@code before}, oldest first. */
    List<Line> lines(Long before, Long viewerId) {
        List<ChatMessageRecord> rows = db.selectFrom(CHAT_MESSAGE)
                .where(CHAT_MESSAGE.DELETED_AT.isNull(), CHAT_MESSAGE.HIDDEN_AT.isNull(),
                        before == null ? org.jooq.impl.DSL.noCondition() : CHAT_MESSAGE.ID.lt(before))
                .orderBy(CHAT_MESSAGE.ID.desc()).limit(PAGE).fetch();
        List<ChatMessageRecord> ordered = rows.reversed();
        Set<Long> authors = new HashSet<>();
        ordered.forEach(r -> authors.add(r.getAuthorId()));
        Map<Long, People.Person> names = people.of(authors);
        List<Long> replyIds = ordered.stream().map(ChatMessageRecord::getReplyTo).filter(java.util.Objects::nonNull).toList();
        Map<Long, String> replied = replyIds.isEmpty() ? Map.of()
                : db.select(CHAT_MESSAGE.ID, CHAT_MESSAGE.BODY).from(CHAT_MESSAGE)
                        .where(CHAT_MESSAGE.ID.in(replyIds), CHAT_MESSAGE.DELETED_AT.isNull(), CHAT_MESSAGE.HIDDEN_AT.isNull())
                        .fetchMap(CHAT_MESSAGE.ID, CHAT_MESSAGE.BODY);
        List<String> bodies = mentions.render(ordered.stream().map(ChatMessageRecord::getBody).toList());
        return java.util.stream.IntStream.range(0, ordered.size()).mapToObj(i -> {
            ChatMessageRecord r = ordered.get(i);
            People.Person author = names.get(r.getAuthorId());
            String answered = r.getReplyTo() == null ? null : replied.get(r.getReplyTo());
            return new Line(r.getId(), author.nick(), author.avatarUrl(), bodies.get(i), r.getCreatedAt(), r.getReplyTo(),
                    answered == null ? null : mentions.excerpt(answered, 80), viewerId != null && viewerId == r.getAuthorId());
        }).toList();
    }

    @Transactional
    long post(Viewer author, String text, Long replyTo) {
        if (!writing.tryAcquire("chat:" + author.accountId())) {
            throw UserFacingException.tooManyRequests();
        }
        String clean = text == null ? "" : text.replace("\r", "").strip();
        if (clean.isEmpty() || clean.length() > 2000) {
            throw UserFacingException.badRequest(clean.isEmpty() ? "Повідомлення порожнє." : "Повідомлення задовге: до 2000 знаків.");
        }
        if (replyTo != null && !db.fetchExists(CHAT_MESSAGE, CHAT_MESSAGE.ID.eq(replyTo))) {
            throw UserFacingException.notFound("Такого повідомлення немає.");
        }
        Mentions.Encoded encoded = mentions.encode(clean);
        long id = db.insertInto(CHAT_MESSAGE)
                .set(CHAT_MESSAGE.AUTHOR_ID, author.accountId())
                .set(CHAT_MESSAGE.REPLY_TO, replyTo)
                .set(CHAT_MESSAGE.BODY, encoded.body())
                .set(CHAT_MESSAGE.CREATED_AT, now())
                .returning(CHAT_MESSAGE.ID).fetchSingle().getId();
        if (!encoded.accounts().isEmpty() || !encoded.teams().isEmpty()) {
            events.publishEvent(new ChatMentioned(id, author.accountId(), encoded.accounts(), encoded.teams(),
                    mentions.excerpt(encoded.body(), 140)));
        }
        AfterCommit.run(() -> live.sendAll("chat", Map.of("id", id)));
        return id;
    }

    @Transactional
    void remove(Viewer viewer, long id) {
        ChatMessageRecord line = db.selectFrom(CHAT_MESSAGE).where(CHAT_MESSAGE.ID.eq(id)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого повідомлення немає."));
        if (line.getAuthorId() == viewer.accountId()) {
            db.update(CHAT_MESSAGE).set(CHAT_MESSAGE.DELETED_AT, now()).where(CHAT_MESSAGE.ID.eq(id)).execute();
        } else if (viewer.role().atLeast(SiteRole.MODERATOR)) {
            db.update(CHAT_MESSAGE).set(CHAT_MESSAGE.HIDDEN_AT, now()).set(CHAT_MESSAGE.HIDDEN_BY, viewer.accountId())
                    .where(CHAT_MESSAGE.ID.eq(id)).execute();
        } else {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Видаляти можна лише свої повідомлення.");
        }
        AfterCommit.run(() -> live.sendAll("chat", Map.of("removed", id)));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
