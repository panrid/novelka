package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAT_CHANNEL;
import static space.panrid.novelka.jooq.Tables.CHAT_CHANNEL_READ;
import static space.panrid.novelka.jooq.Tables.CHAT_MESSAGE;
import static space.panrid.novelka.jooq.Tables.CHAT_UNSEEN;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.community.ChatMentioned;
import space.panrid.novelka.community.Mentions;
import space.panrid.novelka.jooq.tables.records.ChatChannelRecord;
import space.panrid.novelka.jooq.tables.records.ChatMessageRecord;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * The site-wide chat, in channels by topic (етап 20, крок 6): «загальний», «переклад-і-словники»
 * and so on. Guests read it; signed-in people write. Each line shows who wrote it — the site's
 * owner, a moderator, a translator or a reader — and «Зараз на сайті» lists who has the site open.
 */
@Service
class ChatService {

    static final String GENERAL = "general";
    private static final int PAGE = 50;
    /** More than this many unread lines in a channel shows as «99+». */
    private static final int UNREAD_CAP = 100;
    private static final int ONLINE_SHOWN = 30;

    private final DSLContext db;
    private final Mentions mentions;
    private final People people;
    private final ApplicationEventPublisher events;
    private final LiveEvents live;
    private final Clock clock;
    private final RateLimiter writing;
    private final space.panrid.novelka.platform.audit.AuditLog audit;

    ChatService(DSLContext db, Mentions mentions, People people, ApplicationEventPublisher events, LiveEvents live, Clock clock,
            space.panrid.novelka.platform.audit.AuditLog audit) {
        this.audit = audit;
        this.db = db;
        this.mentions = mentions;
        this.people = people;
        this.events = events;
        this.live = live;
        this.clock = clock;
        this.writing = new RateLimiter(15, Duration.ofMinutes(1), clock);
    }

    /**
     * @param authorRole  owner, admin, moderator, translator (works in a team with a translation) or reader
     * @param mentionsMe  the line names the viewer: it stands out
     */
    record Line(long id, String authorNick, String authorAvatarUrl, int authorLevel, String authorRole, String body,
            OffsetDateTime createdAt, Long replyTo, String replyExcerpt, boolean mine, boolean mentionsMe) {
    }

    /** @param unread lines by others the viewer has not seen yet, up to 100; always 0 for guests */
    record Channel(String slug, String name, String about, int unread) {
    }

    List<Channel> channels(Long viewerId) {
        List<ChatChannelRecord> all = db.selectFrom(CHAT_CHANNEL).orderBy(CHAT_CHANNEL.POSITION, CHAT_CHANNEL.ID).fetch();
        if (viewerId == null) {
            return all.stream().map(c -> new Channel(c.getSlug(), c.getName(), c.getAbout(), 0)).toList();
        }
        Map<Long, Long> read = db.select(CHAT_CHANNEL_READ.CHANNEL_ID, CHAT_CHANNEL_READ.LAST_READ_ID).from(CHAT_CHANNEL_READ)
                .where(CHAT_CHANNEL_READ.ACCOUNT_ID.eq(viewerId)).fetchMap(CHAT_CHANNEL_READ.CHANNEL_ID, CHAT_CHANNEL_READ.LAST_READ_ID);
        // A channel never opened counts what was said since the person joined the site.
        OffsetDateTime joined = db.select(ACCOUNT.CREATED_AT).from(ACCOUNT).where(ACCOUNT.ID.eq(viewerId)).fetchOne(ACCOUNT.CREATED_AT);
        return all.stream().map(c -> {
            Long upTo = read.get(c.getId());
            var unseen = db.selectOne().from(CHAT_MESSAGE)
                    .where(CHAT_MESSAGE.CHANNEL_ID.eq(c.getId()), CHAT_MESSAGE.DELETED_AT.isNull(), CHAT_MESSAGE.HIDDEN_AT.isNull(),
                            CHAT_MESSAGE.AUTHOR_ID.ne(viewerId),
                            upTo == null ? DSL.noCondition() : CHAT_MESSAGE.ID.gt(upTo),
                            upTo != null || joined == null ? DSL.noCondition() : CHAT_MESSAGE.CREATED_AT.gt(joined))
                    .limit(UNREAD_CAP);
            return new Channel(c.getSlug(), c.getName(), c.getAbout(), db.fetchCount(unseen));
        }).toList();
    }

    private ChatChannelRecord channel(String slug) {
        return db.selectFrom(CHAT_CHANNEL).where(CHAT_CHANNEL.SLUG.eq(slug == null || slug.isBlank() ? GENERAL : slug)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого каналу немає."));
    }

    /** The latest lines of a channel, or older ones before {@code before}, oldest first. */
    List<Line> lines(String slug, Long before, Long viewerId) {
        ChatChannelRecord channel = channel(slug);
        List<ChatMessageRecord> rows = db.selectFrom(CHAT_MESSAGE)
                .where(CHAT_MESSAGE.CHANNEL_ID.eq(channel.getId()), CHAT_MESSAGE.DELETED_AT.isNull(), CHAT_MESSAGE.HIDDEN_AT.isNull(),
                        before == null ? DSL.noCondition() : CHAT_MESSAGE.ID.lt(before))
                .orderBy(CHAT_MESSAGE.ID.desc()).limit(PAGE).fetch();
        List<ChatMessageRecord> ordered = rows.reversed();
        Set<Long> authors = new HashSet<>();
        ordered.forEach(r -> authors.add(r.getAuthorId()));
        Map<Long, People.Person> names = people.of(authors);
        Map<Long, String> roles = roles(authors);
        List<Long> replyIds = ordered.stream().map(ChatMessageRecord::getReplyTo).filter(java.util.Objects::nonNull).toList();
        Map<Long, String> replied = replyIds.isEmpty() ? Map.of()
                : db.select(CHAT_MESSAGE.ID, CHAT_MESSAGE.BODY).from(CHAT_MESSAGE)
                        .where(CHAT_MESSAGE.ID.in(replyIds), CHAT_MESSAGE.DELETED_AT.isNull(), CHAT_MESSAGE.HIDDEN_AT.isNull())
                        .fetchMap(CHAT_MESSAGE.ID, CHAT_MESSAGE.BODY);
        List<String> bodies = mentions.render(ordered.stream().map(ChatMessageRecord::getBody).toList());
        String me = viewerId == null ? null : "<@u:" + viewerId + ">";
        return java.util.stream.IntStream.range(0, ordered.size()).mapToObj(i -> {
            ChatMessageRecord r = ordered.get(i);
            People.Person author = names.get(r.getAuthorId());
            String answered = r.getReplyTo() == null ? null : replied.get(r.getReplyTo());
            boolean mine = viewerId != null && viewerId.equals(r.getAuthorId());
            return new Line(r.getId(), author.nick(), author.avatarUrl(), author.level(), roles.getOrDefault(r.getAuthorId(), "reader"),
                    bodies.get(i), r.getCreatedAt(), r.getReplyTo(), answered == null ? null : mentions.excerpt(answered, 80), mine,
                    !mine && me != null && r.getBody().contains(me));
        }).toList();
    }

    /** What each author is on the site: their site role, or «translator» for those with a translation in a team of theirs. */
    private Map<Long, String> roles(Set<Long> authors) {
        if (authors.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roles = new java.util.HashMap<>();
        Set<Long> translators = new HashSet<>(db.selectDistinct(TEAM.OWNER_ID).from(EDITION).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(TEAM.OWNER_ID.in(authors)).fetch(TEAM.OWNER_ID));
        translators.addAll(db.selectDistinct(TEAM_MEMBER.ACCOUNT_ID).from(TEAM_MEMBER).join(EDITION).on(EDITION.TEAM_ID.eq(TEAM_MEMBER.TEAM_ID))
                .where(TEAM_MEMBER.ACCOUNT_ID.in(authors)).fetch(TEAM_MEMBER.ACCOUNT_ID));
        db.select(ACCOUNT.ID, ACCOUNT.SITE_ROLE).from(ACCOUNT).where(ACCOUNT.ID.in(authors)).forEach(r -> {
            String site = r.value2();
            roles.put(r.value1(), !"reader".equals(site) ? site : translators.contains(r.value1()) ? "translator" : "reader");
        });
        return roles;
    }

    @Transactional
    long post(Viewer author, String slug, String text, Long replyTo) {
        if (!writing.tryAcquire("chat:" + author.accountId())) {
            throw UserFacingException.tooManyRequests();
        }
        ChatChannelRecord channel = channel(slug);
        String clean = text == null ? "" : text.replace("\r", "").strip();
        if (clean.isEmpty() || clean.length() > 2000) {
            throw UserFacingException.badRequest(clean.isEmpty() ? "Повідомлення порожнє." : "Повідомлення задовге: до 2000 знаків.");
        }
        if (replyTo != null && !db.fetchExists(CHAT_MESSAGE, CHAT_MESSAGE.ID.eq(replyTo).and(CHAT_MESSAGE.CHANNEL_ID.eq(channel.getId())))) {
            throw UserFacingException.notFound("Такого повідомлення немає.");
        }
        Mentions.Encoded encoded = mentions.encode(clean);
        long id = db.insertInto(CHAT_MESSAGE)
                .set(CHAT_MESSAGE.AUTHOR_ID, author.accountId())
                .set(CHAT_MESSAGE.CHANNEL_ID, channel.getId())
                .set(CHAT_MESSAGE.REPLY_TO, replyTo)
                .set(CHAT_MESSAGE.BODY, encoded.body())
                .set(CHAT_MESSAGE.CREATED_AT, now())
                .returning(CHAT_MESSAGE.ID).fetchSingle().getId();
        // What one writes, one has read.
        markRead(author.accountId(), channel.getSlug(), id);
        if (!encoded.accounts().isEmpty() || !encoded.teams().isEmpty()) {
            events.publishEvent(new ChatMentioned(id, author.accountId(), encoded.accounts(), encoded.teams(),
                    mentions.excerpt(encoded.body(), 140), channel.getSlug()));
        }
        String where = channel.getSlug();
        AfterCommit.run(() -> live.sendAll("chat", Map.of("id", id, "channel", where)));
        return id;
    }

    /** The viewer has seen the channel up to {@code upTo}; never moves back. */
    @Transactional
    void markRead(long accountId, String slug, long upTo) {
        ChatChannelRecord channel = channel(slug);
        db.insertInto(CHAT_CHANNEL_READ)
                .set(CHAT_CHANNEL_READ.ACCOUNT_ID, accountId)
                .set(CHAT_CHANNEL_READ.CHANNEL_ID, channel.getId())
                .set(CHAT_CHANNEL_READ.LAST_READ_ID, upTo)
                .onConflict(CHAT_CHANNEL_READ.ACCOUNT_ID, CHAT_CHANNEL_READ.CHANNEL_ID).doUpdate()
                .set(CHAT_CHANNEL_READ.LAST_READ_ID, DSL.greatest(CHAT_CHANNEL_READ.LAST_READ_ID, DSL.val(upTo)))
                .execute();
    }

    record Person(String nick, String avatarUrl) {
    }

    /** @param count everyone listed, though only the first 30 come by name */
    record Online(int count, List<Person> people) {
    }

    /** Who has the site open now, except those who chose to stay unseen. */
    Online online() {
        Set<Long> open = live.online();
        if (open.isEmpty()) {
            return new Online(0, List.of());
        }
        Set<Long> unseen = new HashSet<>(db.select(CHAT_UNSEEN.ACCOUNT_ID).from(CHAT_UNSEEN).where(CHAT_UNSEEN.ACCOUNT_ID.in(open))
                .fetch(CHAT_UNSEEN.ACCOUNT_ID));
        List<Long> shown = open.stream().filter(id -> !unseen.contains(id)).toList();
        Map<Long, People.Person> names = people.of(shown);
        List<Person> listed = names.values().stream().sorted(Comparator.comparing(People.Person::nick, String.CASE_INSENSITIVE_ORDER))
                .limit(ONLINE_SHOWN).map(p -> new Person(p.nick(), p.avatarUrl())).toList();
        return new Online(names.size(), listed);
    }

    boolean shownOnline(long accountId) {
        return !db.fetchExists(CHAT_UNSEEN, CHAT_UNSEEN.ACCOUNT_ID.eq(accountId));
    }

    @Transactional
    void showOnline(long accountId, boolean shown) {
        if (shown) {
            db.deleteFrom(CHAT_UNSEEN).where(CHAT_UNSEEN.ACCOUNT_ID.eq(accountId)).execute();
        } else {
            db.insertInto(CHAT_UNSEEN).set(CHAT_UNSEEN.ACCOUNT_ID, accountId).onConflictDoNothing().execute();
        }
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
            audit.record(viewer.accountId(), "hide", "chat", id, java.util.Map.of());
        } else {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Видаляти можна лише свої повідомлення.");
        }
        AfterCommit.run(() -> live.sendAll("chat", Map.of("removed", id)));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
