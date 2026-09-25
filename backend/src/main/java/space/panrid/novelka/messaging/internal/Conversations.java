package space.panrid.novelka.messaging.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CONVERSATION;
import static space.panrid.novelka.jooq.Tables.CONVERSATION_MEMBER;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.MESSAGE;
import static space.panrid.novelka.jooq.Tables.MESSAGE_IMAGE;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.community.Mentions;
import space.panrid.novelka.jooq.tables.records.ConversationRecord;
import space.panrid.novelka.jooq.tables.records.MessageRecord;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.MyTeam;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.Teams;

/**
 * Who may talk to whom, and the messages. Members of direct chats and groups are rows;
 * a team chat follows the team itself, so whoever joins or leaves the team joins or
 * leaves its chat without anyone keeping two lists in step.
 */
@Service
class Conversations {

    private static final int PAGE = 50;
    private static final int MAX_IMAGES = 10;
    private static final int MAX_GROUP = 100;

    private final DSLContext db;
    private final Teams teams;
    private final Blocks blocks;
    private final Mentions mentions;
    private final Images images;
    private final LiveEvents live;
    private final Clock clock;
    private final RateLimiter sending;

    Conversations(DSLContext db, Teams teams, Blocks blocks, Mentions mentions, Images images, LiveEvents live, Clock clock) {
        this.db = db;
        this.teams = teams;
        this.blocks = blocks;
        this.mentions = mentions;
        this.images = images;
        this.live = live;
        this.clock = clock;
        this.sending = new RateLimiter(30, Duration.ofMinutes(1), clock);
    }

    // ---- the list ------------------------------------------------------------------------------

    record Summary(long id, String kind, String title, String avatarUrl, String teamHandle, String otherNick,
            String lastText, String lastAuthorNick, OffsetDateTime lastAt, int unread, boolean muted) {
    }

    record Inbox(List<Summary> items, int unread) {
    }

    @Transactional
    Inbox list(long me) {
        Set<Long> myTeams = new HashSet<>();
        for (MyTeam team : teams.teamsOf(me)) {
            // A team of one (everyone's personal team at first) has nobody to talk to.
            if (db.fetchExists(TEAM_MEMBER, TEAM_MEMBER.TEAM_ID.eq(team.team().id()))) {
                myTeams.add(team.team().id());
                join(teamConversation(team.team().id()), me, null);
            }
        }
        var rows = db.select(CONVERSATION.asterisk(), CONVERSATION_MEMBER.LAST_READ_MESSAGE_ID, CONVERSATION_MEMBER.MUTED)
                .from(CONVERSATION).join(CONVERSATION_MEMBER).on(CONVERSATION_MEMBER.CONVERSATION_ID.eq(CONVERSATION.ID))
                .where(CONVERSATION_MEMBER.ACCOUNT_ID.eq(me), CONVERSATION_MEMBER.LEFT_AT.isNull())
                .fetch().stream()
                .filter(r -> r.get(CONVERSATION.TEAM_ID) == null || myTeams.contains(r.get(CONVERSATION.TEAM_ID)))
                .toList();
        List<Long> ids = rows.stream().map(r -> r.get(CONVERSATION.ID)).toList();
        Map<Long, MessageRecord> last = new HashMap<>();
        if (!ids.isEmpty()) {
            db.select(MESSAGE.fields()).distinctOn(MESSAGE.CONVERSATION_ID).from(MESSAGE)
                    .where(MESSAGE.CONVERSATION_ID.in(ids))
                    .orderBy(MESSAGE.CONVERSATION_ID, MESSAGE.ID.desc())
                    .fetchInto(MESSAGE)
                    .forEach(message -> last.put(message.getConversationId(), message));
        }
        Map<Long, Integer> unread = new HashMap<>();
        for (Record r : rows) {
            unread.put(r.get(CONVERSATION.ID), unread(r.get(CONVERSATION.ID), me, r.get(CONVERSATION_MEMBER.LAST_READ_MESSAGE_ID)));
        }
        Map<Long, Long> others = directOthers(ids, me);
        Set<Long> people = new HashSet<>(others.values());
        last.values().forEach(m -> { if (m.getAuthorId() != null) people.add(m.getAuthorId()); });
        Map<Long, Person> names = people(people);
        Map<Long, StoredImage> avatars = images.findAll(rows.stream().map(r -> r.get(CONVERSATION.AVATAR_IMAGE_ID))
                .filter(Objects::nonNull).toList());
        List<String> texts = mentions.render(ids.stream().map(id -> last.containsKey(id) && last.get(id).getDeletedAt() == null
                ? last.get(id).getBody() : "").toList());
        List<Summary> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Record r = rows.get(i);
            long id = r.get(CONVERSATION.ID);
            MessageRecord message = last.get(id);
            String kind = r.get(CONVERSATION.KIND);
            String title;
            String avatar = null;
            String handle = null;
            String other = null;
            switch (kind) {
                case "direct" -> {
                    Person person = names.get(others.get(id));
                    title = person == null ? "Розмова" : person.nick();
                    avatar = person == null ? null : person.avatarUrl();
                    other = title;
                }
                case "team" -> {
                    TeamInfo team = teams.find(r.get(CONVERSATION.TEAM_ID)).orElseThrow();
                    title = team.name();
                    handle = team.handle();
                }
                default -> {
                    title = r.get(CONVERSATION.TITLE);
                    StoredImage picture = r.get(CONVERSATION.AVATAR_IMAGE_ID) == null ? null : avatars.get(r.get(CONVERSATION.AVATAR_IMAGE_ID));
                    avatar = picture == null ? null : picture.url(256);
                }
            }
            String text = message == null ? null : message.getDeletedAt() != null || message.getHiddenAt() != null ? "Повідомлення видалено"
                    : texts.get(i).isEmpty() ? "Картинка" : mentions.excerpt(message.getBody(), 90);
            items.add(new Summary(id, kind, title, avatar, handle, other, text,
                    message == null || message.getAuthorId() == null ? null : names.get(message.getAuthorId()).nick(),
                    message == null ? r.get(CONVERSATION.CREATED_AT) : message.getCreatedAt(), unread.get(id),
                    r.get(CONVERSATION_MEMBER.MUTED)));
        }
        items.sort((a, b) -> b.lastAt().compareTo(a.lastAt()));
        int total = items.stream().filter(s -> !s.muted()).mapToInt(Summary::unread).sum();
        return new Inbox(items, total);
    }

    int unreadTotal(long me) {
        return list(me).unread();
    }

    private int unread(long conversationId, long me, Long lastRead) {
        return db.fetchCount(MESSAGE, MESSAGE.CONVERSATION_ID.eq(conversationId)
                .and(MESSAGE.ID.gt(lastRead == null ? 0L : lastRead))
                .and(MESSAGE.AUTHOR_ID.isNull().or(MESSAGE.AUTHOR_ID.ne(me)))
                .and(MESSAGE.DELETED_AT.isNull()));
    }

    // ---- starting conversations ---------------------------------------------------------------

    /** The one direct conversation between two people, created on the first message. */
    @Transactional
    long direct(Viewer me, String nick) {
        Record other = person(nick);
        long otherId = other.get(ACCOUNT.ID);
        if (otherId == me.accountId()) {
            throw UserFacingException.badRequest("Писати самому собі не можна.");
        }
        String key = Math.min(me.accountId(), otherId) + ":" + Math.max(me.accountId(), otherId);
        Long existing = db.select(CONVERSATION.ID).from(CONVERSATION).where(CONVERSATION.DIRECT_KEY.eq(key)).fetchOne(CONVERSATION.ID);
        if (existing != null) {
            db.update(CONVERSATION_MEMBER).setNull(CONVERSATION_MEMBER.LEFT_AT)
                    .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(existing), CONVERSATION_MEMBER.ACCOUNT_ID.eq(me.accountId())).execute();
            return existing;
        }
        requireReachable(me.accountId(), other);
        long id = db.insertInto(CONVERSATION).set(CONVERSATION.KIND, "direct").set(CONVERSATION.DIRECT_KEY, key)
                .set(CONVERSATION.CREATED_BY, me.accountId()).returning(CONVERSATION.ID).fetchSingle().getId();
        join(id, me.accountId(), null);
        join(id, otherId, me.accountId());
        return id;
    }

    @Transactional
    long group(Viewer me, String title, List<String> nicks) {
        String name = title(title);
        List<Record> people = new ArrayList<>();
        for (String nick : new LinkedHashSet<>(nicks == null ? List.<String>of() : nicks)) {
            Record person = person(nick);
            if (person.get(ACCOUNT.ID) != me.accountId()) {
                requireReachable(me.accountId(), person);
                people.add(person);
            }
        }
        if (people.size() + 1 > MAX_GROUP) {
            throw UserFacingException.badRequest("У групі — до %d людей.".formatted(MAX_GROUP));
        }
        long id = db.insertInto(CONVERSATION).set(CONVERSATION.KIND, "group").set(CONVERSATION.TITLE, name)
                .set(CONVERSATION.CREATED_BY, me.accountId()).returning(CONVERSATION.ID).fetchSingle().getId();
        join(id, me.accountId(), null);
        db.update(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.ROLE, "admin")
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(id), CONVERSATION_MEMBER.ACCOUNT_ID.eq(me.accountId())).execute();
        system(id, "%s створює групу «%s»".formatted(me.nick(), name));
        for (Record person : people) {
            join(id, person.get(ACCOUNT.ID), me.accountId());
        }
        if (!people.isEmpty()) {
            system(id, "%s додає: %s".formatted(me.nick(), String.join(", ", people.stream().map(p -> p.get(ACCOUNT.NICK)).toList())));
        }
        return id;
    }

    /** A team's chat, made the first time anyone in the team opens their messages. */
    long teamConversation(long teamId) {
        Long id = db.select(CONVERSATION.ID).from(CONVERSATION).where(CONVERSATION.TEAM_ID.eq(teamId)).fetchOne(CONVERSATION.ID);
        if (id != null) {
            return id;
        }
        db.insertInto(CONVERSATION).set(CONVERSATION.KIND, "team").set(CONVERSATION.TEAM_ID, teamId).onConflictDoNothing().execute();
        return db.select(CONVERSATION.ID).from(CONVERSATION).where(CONVERSATION.TEAM_ID.eq(teamId)).fetchSingle().value1();
    }

    // ---- inside a conversation ----------------------------------------------------------------

    record Picture(long id, String url, String thumbUrl) {
    }

    /** @param hidden a moderator hid it after a report; like {@code deleted}, nothing of it is shown */
    record Line(long id, String kind, String authorNick, String authorAvatarUrl, String body, List<Picture> pictures,
            Long replyTo, String replyExcerpt, OffsetDateTime createdAt, OffsetDateTime editedAt, boolean deleted, boolean mine,
            boolean hidden) {
    }

    record Member(String nick, String avatarUrl, String role) {
    }

    record Details(long id, String kind, String title, String avatarUrl, String teamHandle, boolean admin, boolean muted,
            List<Member> members, List<Line> lines, boolean hasOlder, boolean canWrite, String cannotWrite) {
    }

    @Transactional
    Details open(Viewer me, long conversationId, Long before) {
        ConversationRecord conversation = requireMember(conversationId, me.accountId());
        List<MessageRecord> rows = db.selectFrom(MESSAGE)
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId), before == null ? DSL.noCondition() : MESSAGE.ID.lt(before))
                .orderBy(MESSAGE.ID.desc()).limit(PAGE + 1).fetch();
        boolean older = rows.size() > PAGE;
        List<MessageRecord> page = (older ? rows.subList(0, PAGE) : rows).reversed();
        List<Member> members = members(conversation);
        var mine = db.selectFrom(CONVERSATION_MEMBER)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(me.accountId()))
                .fetchSingle();
        String title = conversation.getTitle();
        String avatar = null;
        String handle = null;
        String blocked = null;
        if ("direct".equals(conversation.getKind())) {
            Member other = members.stream().filter(m -> !m.nick().equals(me.nick())).findFirst().orElse(null);
            title = other == null ? "Розмова" : other.nick();
            avatar = other == null ? null : other.avatarUrl();
            Long otherId = directOthers(List.of(conversationId), me.accountId()).get(conversationId);
            if (otherId != null && blocks.between(me.accountId(), otherId)) {
                blocked = "Ви не можете писати одне одному: хтось із вас заблокував іншого.";
            }
        } else if ("team".equals(conversation.getKind())) {
            TeamInfo team = teams.find(conversation.getTeamId()).orElseThrow();
            title = team.name();
            handle = team.handle();
        } else if (conversation.getAvatarImageId() != null) {
            avatar = images.find(conversation.getAvatarImageId()).map(image -> image.url(256)).orElse(null);
        }
        return new Details(conversationId, conversation.getKind(), title, avatar, handle, "admin".equals(mine.getRole()),
                mine.getMuted(), members, lines(page, me.accountId()), older, blocked == null, blocked);
    }

    private List<Line> lines(List<MessageRecord> page, long me) {
        Set<Long> authors = new HashSet<>();
        page.forEach(m -> { if (m.getAuthorId() != null) authors.add(m.getAuthorId()); });
        Map<Long, Person> names = people(authors);
        List<Long> ids = page.stream().map(MessageRecord::getId).toList();
        Map<Long, List<Long>> pictureIds = new HashMap<>();
        if (!ids.isEmpty()) {
            db.selectFrom(MESSAGE_IMAGE).where(MESSAGE_IMAGE.MESSAGE_ID.in(ids)).orderBy(MESSAGE_IMAGE.POSITION)
                    .forEach(r -> pictureIds.computeIfAbsent(r.getMessageId(), k -> new ArrayList<>()).add(r.getImageId()));
        }
        Map<Long, StoredImage> stored = images.findAll(pictureIds.values().stream().flatMap(List::stream).toList());
        List<Long> replyIds = page.stream().map(MessageRecord::getReplyTo).filter(Objects::nonNull).toList();
        Map<Long, MessageRecord> replied = replyIds.isEmpty() ? Map.of()
                : db.selectFrom(MESSAGE).where(MESSAGE.ID.in(replyIds)).fetchMap(MESSAGE.ID);
        List<String> bodies = mentions.render(page.stream().map(MessageRecord::getBody).toList());
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < page.size(); i++) {
            MessageRecord m = page.get(i);
            boolean hidden = m.getHiddenAt() != null;
            boolean deleted = m.getDeletedAt() != null || hidden;
            Person author = m.getAuthorId() == null ? null : names.get(m.getAuthorId());
            List<Picture> pictures = deleted ? List.of() : pictureIds.getOrDefault(m.getId(), List.of()).stream()
                    .map(stored::get).filter(Objects::nonNull)
                    .map(image -> new Picture(image.id(), image.url(1280), image.url(640))).toList();
            MessageRecord answered = m.getReplyTo() == null ? null : replied.get(m.getReplyTo());
            String excerpt = answered == null ? null : answered.getDeletedAt() != null || answered.getHiddenAt() != null ? "Повідомлення видалено"
                    : mentions.excerpt(answered.getBody(), 80);
            out.add(new Line(m.getId(), m.getKind(), author == null ? null : author.nick(), author == null ? null : author.avatarUrl(),
                    deleted ? "" : bodies.get(i), pictures, m.getReplyTo(), excerpt, m.getCreatedAt(), m.getEditedAt(), deleted,
                    m.getAuthorId() != null && m.getAuthorId() == me, hidden));
        }
        return out;
    }

    @Transactional
    long send(Viewer me, long conversationId, String text, Long replyTo, List<Long> imageIds) {
        ConversationRecord conversation = requireMember(conversationId, me.accountId());
        if (!sending.tryAcquire("message:" + me.accountId())) {
            throw UserFacingException.tooManyRequests();
        }
        if ("direct".equals(conversation.getKind())) {
            Long other = directOthers(List.of(conversationId), me.accountId()).get(conversationId);
            if (other != null && blocks.between(me.accountId(), other)) {
                throw new UserFacingException(HttpStatus.FORBIDDEN, "Ви не можете писати одне одному: хтось із вас заблокував іншого.");
            }
        }
        String body = text == null ? "" : text.replace("\r", "").strip();
        List<Long> pictures = imageIds == null ? List.of() : imageIds.stream().distinct().toList();
        if (body.isEmpty() && pictures.isEmpty()) {
            throw UserFacingException.badRequest("Повідомлення порожнє.");
        }
        if (body.length() > 4000) {
            throw UserFacingException.badRequest("Повідомлення задовге: до 4000 знаків.");
        }
        if (pictures.size() > MAX_IMAGES) {
            throw UserFacingException.badRequest("До %d картинок за раз.".formatted(MAX_IMAGES));
        }
        if (!pictures.isEmpty() && db.fetchCount(IMAGE, IMAGE.ID.in(pictures).and(IMAGE.OWNER_ACCOUNT_ID.eq(me.accountId()))
                .and(IMAGE.KIND.eq("message"))) != pictures.size()) {
            throw UserFacingException.badRequest("Однієї з картинок немає. Завантажте її ще раз.");
        }
        if (replyTo != null && !db.fetchExists(MESSAGE, MESSAGE.ID.eq(replyTo).and(MESSAGE.CONVERSATION_ID.eq(conversationId)))) {
            throw UserFacingException.notFound("Такого повідомлення немає.");
        }
        OffsetDateTime now = now();
        long id = db.insertInto(MESSAGE).set(MESSAGE.CONVERSATION_ID, conversationId).set(MESSAGE.AUTHOR_ID, me.accountId())
                .set(MESSAGE.REPLY_TO, replyTo).set(MESSAGE.BODY, mentions.encode(body).body()).set(MESSAGE.CREATED_AT, now)
                .returning(MESSAGE.ID).fetchSingle().getId();
        for (int i = 0; i < pictures.size(); i++) {
            db.insertInto(MESSAGE_IMAGE).set(MESSAGE_IMAGE.MESSAGE_ID, id).set(MESSAGE_IMAGE.IMAGE_ID, pictures.get(i))
                    .set(MESSAGE_IMAGE.POSITION, (short) i).execute();
        }
        db.update(CONVERSATION).set(CONVERSATION.LAST_MESSAGE_AT, now).where(CONVERSATION.ID.eq(conversationId)).execute();
        markRead(me.accountId(), conversationId, id);
        // Someone who closed a direct chat sees it again when a new message arrives.
        db.update(CONVERSATION_MEMBER).setNull(CONVERSATION_MEMBER.LEFT_AT)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), DSL.val("direct").eq(conversation.getKind())).execute();
        nudge(conversation, "message");
        return id;
    }

    @Transactional
    void edit(Viewer me, long messageId, String text) {
        MessageRecord message = ownMessage(me, messageId);
        String body = text == null ? "" : text.replace("\r", "").strip();
        if (body.isEmpty() || body.length() > 4000) {
            throw UserFacingException.badRequest(body.isEmpty() ? "Повідомлення порожнє." : "Повідомлення задовге: до 4000 знаків.");
        }
        db.update(MESSAGE).set(MESSAGE.BODY, mentions.encode(body).body()).set(MESSAGE.EDITED_AT, now())
                .where(MESSAGE.ID.eq(messageId)).execute();
        nudge(conversation(message.getConversationId()), "message");
    }

    /** Hidden from everyone; the text stays in the database only for a report about it. */
    @Transactional
    void delete(Viewer me, long messageId) {
        MessageRecord message = ownMessage(me, messageId);
        db.update(MESSAGE).set(MESSAGE.DELETED_AT, now()).where(MESSAGE.ID.eq(messageId)).execute();
        nudge(conversation(message.getConversationId()), "message");
    }

    @Transactional
    void read(long me, long conversationId, long upTo) {
        requireMember(conversationId, me);
        markRead(me, conversationId, upTo);
        AfterCommit.run(() -> live.send(me, "messages", Map.of("unread", unreadTotal(me))));
    }

    @Transactional
    void mute(long me, long conversationId, boolean muted) {
        requireMember(conversationId, me);
        db.update(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.MUTED, muted)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(me)).execute();
    }

    // ---- managing a group ---------------------------------------------------------------------

    @Transactional
    void rename(Viewer me, long conversationId, String title, Long avatarImageId, boolean changeAvatar) {
        ConversationRecord group = requireGroupAdmin(me, conversationId);
        if (title != null) {
            String name = title(title);
            if (!name.equals(group.getTitle())) {
                db.update(CONVERSATION).set(CONVERSATION.TITLE, name).where(CONVERSATION.ID.eq(conversationId)).execute();
                system(conversationId, "%s змінює назву на «%s»".formatted(me.nick(), name));
            }
        }
        if (changeAvatar) {
            if (avatarImageId != null && !db.fetchExists(IMAGE, IMAGE.ID.eq(avatarImageId)
                    .and(IMAGE.OWNER_ACCOUNT_ID.eq(me.accountId())).and(IMAGE.KIND.eq("group_avatar")))) {
                throw UserFacingException.badRequest("Картинки немає. Завантажте її ще раз.");
            }
            db.update(CONVERSATION).set(CONVERSATION.AVATAR_IMAGE_ID, avatarImageId).where(CONVERSATION.ID.eq(conversationId)).execute();
            system(conversationId, "%s змінює картинку групи".formatted(me.nick()));
        }
        nudge(conversation(conversationId), "message");
    }

    @Transactional
    void add(Viewer me, long conversationId, String nick) {
        requireGroupAdmin(me, conversationId);
        Record person = person(nick);
        long id = person.get(ACCOUNT.ID);
        if (db.fetchExists(CONVERSATION_MEMBER, CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId)
                .and(CONVERSATION_MEMBER.ACCOUNT_ID.eq(id)).and(CONVERSATION_MEMBER.LEFT_AT.isNull()))) {
            throw UserFacingException.conflict("%s уже в групі.".formatted(person.get(ACCOUNT.NICK)));
        }
        requireReachable(me.accountId(), person);
        if (db.fetchCount(CONVERSATION_MEMBER, CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId)
                .and(CONVERSATION_MEMBER.LEFT_AT.isNull())) >= MAX_GROUP) {
            throw UserFacingException.badRequest("У групі — до %d людей.".formatted(MAX_GROUP));
        }
        join(conversationId, id, me.accountId());
        system(conversationId, "%s додає %s".formatted(me.nick(), person.get(ACCOUNT.NICK)));
        nudge(conversation(conversationId), "message");
    }

    /** An admin removes someone, or anyone leaves; the last admin leaving hands over to the oldest member. */
    @Transactional
    void remove(Viewer me, long conversationId, String nick) {
        ConversationRecord group = conversation(conversationId);
        if (!"group".equals(group.getKind())) {
            throw UserFacingException.badRequest(group.getKind().equals("team")
                    ? "Чат команди можна покинути лише разом із командою." : "Особисту розмову не можна покинути.");
        }
        Record person = person(nick);
        long id = person.get(ACCOUNT.ID);
        boolean self = id == me.accountId();
        if (self) {
            requireMember(conversationId, me.accountId());
        } else {
            requireGroupAdmin(me, conversationId);
        }
        int changed = db.update(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.LEFT_AT, now()).set(CONVERSATION_MEMBER.ROLE, "member")
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(id),
                        CONVERSATION_MEMBER.LEFT_AT.isNull()).execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такої людини в групі немає.");
        }
        system(conversationId, self ? "%s виходить із групи".formatted(me.nick()) : "%s видаляє %s".formatted(me.nick(), person.get(ACCOUNT.NICK)));
        boolean adminLeft = !db.fetchExists(CONVERSATION_MEMBER, CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId)
                .and(CONVERSATION_MEMBER.LEFT_AT.isNull()).and(CONVERSATION_MEMBER.ROLE.eq("admin")));
        if (adminLeft) {
            db.update(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.ROLE, "admin")
                    .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(
                            DSL.select(CONVERSATION_MEMBER.ACCOUNT_ID).from(CONVERSATION_MEMBER)
                                    .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.LEFT_AT.isNull())
                                    .orderBy(CONVERSATION_MEMBER.JOINED_AT).limit(1)))
                    .execute();
        }
        nudge(group, "message");
    }

    @Transactional
    void setRole(Viewer me, long conversationId, String nick, String role) {
        requireGroupAdmin(me, conversationId);
        if (!"admin".equals(role) && !"member".equals(role)) {
            throw UserFacingException.badRequest("Невідома роль.");
        }
        long id = person(nick).get(ACCOUNT.ID);
        if (id == me.accountId() && "member".equals(role) && db.fetchCount(CONVERSATION_MEMBER, CONVERSATION_MEMBER.CONVERSATION_ID
                .eq(conversationId).and(CONVERSATION_MEMBER.LEFT_AT.isNull()).and(CONVERSATION_MEMBER.ROLE.eq("admin"))) == 1) {
            throw UserFacingException.badRequest("У групі має лишитися хоча б один адміністратор.");
        }
        db.update(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.ROLE, role)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(id),
                        CONVERSATION_MEMBER.LEFT_AT.isNull()).execute();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private record Person(String nick, String avatarUrl) {
    }

    private Map<Long, Person> people(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var rows = db.select(ACCOUNT.ID, ACCOUNT.NICK, ACCOUNT.AVATAR_IMAGE_ID).from(ACCOUNT).where(ACCOUNT.ID.in(ids)).fetch();
        Map<Long, StoredImage> avatars = images.findAll(rows.stream().map(r -> r.value3()).filter(Objects::nonNull).toList());
        Map<Long, Person> out = new HashMap<>();
        rows.forEach(r -> {
            StoredImage avatar = r.value3() == null ? null : avatars.get(r.value3());
            out.put(r.value1(), new Person(r.value2(), avatar == null ? null : avatar.url(96)));
        });
        return out;
    }

    private List<Member> members(ConversationRecord conversation) {
        if ("team".equals(conversation.getKind())) {
            return List.of();
        }
        var rows = db.select(CONVERSATION_MEMBER.ACCOUNT_ID, CONVERSATION_MEMBER.ROLE).from(CONVERSATION_MEMBER)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversation.getId()), CONVERSATION_MEMBER.LEFT_AT.isNull())
                .orderBy(CONVERSATION_MEMBER.JOINED_AT).fetch();
        Map<Long, Person> names = people(new HashSet<>(rows.map(r -> r.value1())));
        return rows.map(r -> new Member(names.get(r.value1()).nick(), names.get(r.value1()).avatarUrl(), r.value2()));
    }

    private Map<Long, Long> directOthers(List<Long> conversationIds, long me) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return db.select(CONVERSATION_MEMBER.CONVERSATION_ID, CONVERSATION_MEMBER.ACCOUNT_ID).from(CONVERSATION_MEMBER)
                .join(CONVERSATION).on(CONVERSATION.ID.eq(CONVERSATION_MEMBER.CONVERSATION_ID))
                .where(CONVERSATION.KIND.eq("direct"), CONVERSATION_MEMBER.CONVERSATION_ID.in(conversationIds),
                        CONVERSATION_MEMBER.ACCOUNT_ID.ne(me))
                .fetchMap(CONVERSATION_MEMBER.CONVERSATION_ID, CONVERSATION_MEMBER.ACCOUNT_ID);
    }

    private Record person(String nick) {
        String key = nick == null ? "" : nick.strip().replaceFirst("^@", "").toLowerCase(java.util.Locale.ROOT);
        return db.select(ACCOUNT.ID, ACCOUNT.NICK, ACCOUNT.DM_POLICY).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(key)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Людини з ніком «%s» немає.".formatted(nick)));
    }

    /** They allow messages and neither blocked the other. */
    private void requireReachable(long me, Record other) {
        if (blocks.between(me, other.get(ACCOUNT.ID))) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "%s недоступний для вас.".formatted(other.get(ACCOUNT.NICK)));
        }
        if ("nobody".equals(other.get(ACCOUNT.DM_POLICY))) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "%s не приймає повідомлень.".formatted(other.get(ACCOUNT.NICK)));
        }
    }

    private ConversationRecord conversation(long id) {
        return db.selectFrom(CONVERSATION).where(CONVERSATION.ID.eq(id)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої розмови немає."));
    }

    /** A current member; a team chat checks the team itself. Outsiders get 404, not a hint it exists. */
    private ConversationRecord requireMember(long conversationId, long me) {
        ConversationRecord conversation = conversation(conversationId);
        if ("team".equals(conversation.getKind())) {
            if (teams.roleOf(conversation.getTeamId(), me).isEmpty()) {
                throw UserFacingException.notFound("Такої розмови немає.");
            }
            join(conversationId, me, null);
            return conversation;
        }
        if (!db.fetchExists(CONVERSATION_MEMBER, CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId)
                .and(CONVERSATION_MEMBER.ACCOUNT_ID.eq(me)).and(CONVERSATION_MEMBER.LEFT_AT.isNull()))) {
            throw UserFacingException.notFound("Такої розмови немає.");
        }
        return conversation;
    }

    private ConversationRecord requireGroupAdmin(Viewer me, long conversationId) {
        ConversationRecord conversation = requireMember(conversationId, me.accountId());
        if (!"group".equals(conversation.getKind()) || !db.fetchExists(CONVERSATION_MEMBER,
                CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId).and(CONVERSATION_MEMBER.ACCOUNT_ID.eq(me.accountId()))
                        .and(CONVERSATION_MEMBER.ROLE.eq("admin")))) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Це можуть лише адміністратори групи.");
        }
        return conversation;
    }

    private MessageRecord ownMessage(Viewer me, long messageId) {
        MessageRecord message = db.selectFrom(MESSAGE).where(MESSAGE.ID.eq(messageId), MESSAGE.DELETED_AT.isNull()).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого повідомлення немає."));
        requireMember(message.getConversationId(), me.accountId());
        if (!Objects.equals(message.getAuthorId(), me.accountId())) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Змінювати можна лише свої повідомлення.");
        }
        return message;
    }

    private void join(long conversationId, long accountId, Long addedBy) {
        db.insertInto(CONVERSATION_MEMBER).set(CONVERSATION_MEMBER.CONVERSATION_ID, conversationId)
                .set(CONVERSATION_MEMBER.ACCOUNT_ID, accountId).set(CONVERSATION_MEMBER.ADDED_BY, addedBy)
                .onConflict(CONVERSATION_MEMBER.CONVERSATION_ID, CONVERSATION_MEMBER.ACCOUNT_ID).doUpdate()
                .setNull(CONVERSATION_MEMBER.LEFT_AT)
                .where(CONVERSATION_MEMBER.LEFT_AT.isNotNull().and(DSL.val(addedBy).isNotNull()))
                .execute();
    }

    private void markRead(long me, long conversationId, long upTo) {
        db.update(CONVERSATION_MEMBER)
                .set(CONVERSATION_MEMBER.LAST_READ_MESSAGE_ID, DSL.greatest(DSL.coalesce(CONVERSATION_MEMBER.LAST_READ_MESSAGE_ID, 0L), DSL.val(upTo)))
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversationId), CONVERSATION_MEMBER.ACCOUNT_ID.eq(me)).execute();
    }

    private void system(long conversationId, String text) {
        db.insertInto(MESSAGE).set(MESSAGE.CONVERSATION_ID, conversationId).set(MESSAGE.KIND, "system").set(MESSAGE.BODY, text)
                .set(MESSAGE.CREATED_AT, now()).execute();
        db.update(CONVERSATION).set(CONVERSATION.LAST_MESSAGE_AT, now()).where(CONVERSATION.ID.eq(conversationId)).execute();
    }

    /** Every current member's open tabs hear that the conversation changed. */
    private void nudge(ConversationRecord conversation, String type) {
        List<Long> people = "team".equals(conversation.getKind())
                ? teamPeople(conversation.getTeamId())
                : db.select(CONVERSATION_MEMBER.ACCOUNT_ID).from(CONVERSATION_MEMBER)
                        .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(conversation.getId()), CONVERSATION_MEMBER.LEFT_AT.isNull())
                        .fetch(CONVERSATION_MEMBER.ACCOUNT_ID);
        long id = conversation.getId();
        AfterCommit.run(() -> live.send(people, type, Map.of("conversationId", id)));
    }

    private List<Long> teamPeople(long teamId) {
        return db.select(CONVERSATION_MEMBER.ACCOUNT_ID).from(CONVERSATION_MEMBER)
                .where(CONVERSATION_MEMBER.CONVERSATION_ID.eq(teamConversation(teamId))).fetch(CONVERSATION_MEMBER.ACCOUNT_ID)
                .stream().filter(id -> teams.roleOf(teamId, id).isPresent()).toList();
    }

    private static String title(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 60) {
            throw UserFacingException.badRequest("Назва групи — від 1 до 60 знаків.");
        }
        return name;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
