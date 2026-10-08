package space.panrid.novelka.telegram.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_LINK;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_LINK_CODE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.platform.live.LiveEvents;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.telegram.TelegramApi;

/**
 * Tying a Telegram chat to an account: the settings hand out a one-time link
 * {@code t.me/<bot>?start=<code>}, the person presses «Start», and the bot gets the code.
 */
@Service
class TelegramLinks {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What the settings show. */
    record Status(boolean available, boolean linked, String username, String botUsername,
            boolean notifyInbox, boolean notifyChapters, boolean notifyMessages) {
    }

    /** A tied chat and what it wants to hear about. */
    record Link(long accountId, long chatId, boolean notifyInbox, boolean notifyChapters, boolean notifyMessages) {
    }

    private final DSLContext db;
    private final TelegramApi telegram;
    private final Texts texts;
    private final LiveEvents live;
    private final Clock clock;

    TelegramLinks(DSLContext db, TelegramApi telegram, Texts texts, LiveEvents live, Clock clock) {
        this.db = db;
        this.telegram = telegram;
        this.texts = texts;
        this.live = live;
        this.clock = clock;
    }

    Status status(long accountId) {
        Optional<Link> link = of(accountId);
        String username = db.select(TELEGRAM_LINK.USERNAME).from(TELEGRAM_LINK).where(TELEGRAM_LINK.ACCOUNT_ID.eq(accountId))
                .fetchOptional(TELEGRAM_LINK.USERNAME).orElse(null);
        return new Status(telegram.configured(), link.isPresent(), username, telegram.configured() ? telegram.botUsername() : null,
                link.map(Link::notifyInbox).orElse(true), link.map(Link::notifyChapters).orElse(true),
                link.map(Link::notifyMessages).orElse(true));
    }

    /** A fresh one-time link; older ones of the person stop working. */
    @Transactional
    String startLink(long accountId) {
        db.deleteFrom(TELEGRAM_LINK_CODE).where(TELEGRAM_LINK_CODE.ACCOUNT_ID.eq(accountId)
                .or(TELEGRAM_LINK_CODE.EXPIRES_AT.lt(now()))).execute();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.insertInto(TELEGRAM_LINK_CODE).set(TELEGRAM_LINK_CODE.CODE_HASH, hash(code))
                .set(TELEGRAM_LINK_CODE.ACCOUNT_ID, accountId).set(TELEGRAM_LINK_CODE.EXPIRES_AT, now().plus(CODE_LIFETIME)).execute();
        return "https://t.me/" + telegram.botUsername() + "?start=" + code;
    }

    @Transactional
    void settings(long accountId, Boolean inbox, Boolean chapters, Boolean messages) {
        java.util.Map<org.jooq.Field<?>, Object> changes = new java.util.HashMap<>();
        if (inbox != null) {
            changes.put(TELEGRAM_LINK.NOTIFY_INBOX, inbox);
        }
        if (chapters != null) {
            changes.put(TELEGRAM_LINK.NOTIFY_CHAPTERS, chapters);
        }
        if (messages != null) {
            changes.put(TELEGRAM_LINK.NOTIFY_MESSAGES, messages);
        }
        if (!changes.isEmpty()) {
            db.update(TELEGRAM_LINK).set(changes).where(TELEGRAM_LINK.ACCOUNT_ID.eq(accountId)).execute();
        }
    }

    @Transactional
    void unlink(long accountId) {
        of(accountId).ifPresent(link -> {
            db.deleteFrom(TELEGRAM_LINK).where(TELEGRAM_LINK.ACCOUNT_ID.eq(accountId)).execute();
            AfterCommit.run(() -> telegram.send(link.chatId(), "Сповіщення з Новелки сюди більше не приходитимуть."));
        });
    }

    Optional<Link> of(long accountId) {
        return db.selectFrom(TELEGRAM_LINK).where(TELEGRAM_LINK.ACCOUNT_ID.eq(accountId)).fetchOptional(r -> new Link(
                r.getAccountId(), r.getChatId(), r.getNotifyInbox(), r.getNotifyChapters(), r.getNotifyMessages()));
    }

    /** The chat is gone for good: the person blocked the bot. */
    @Transactional
    void forget(long chatId) {
        db.deleteFrom(TELEGRAM_LINK).where(TELEGRAM_LINK.CHAT_ID.eq(chatId)).execute();
    }

    /** What someone wrote to the bot. */
    @Transactional
    void handle(TelegramApi.Update update) {
        String text = update.text().strip();
        if (text.startsWith("/start ")) {
            String code = text.substring("/start ".length()).strip();
            Long accountId = db.deleteFrom(TELEGRAM_LINK_CODE)
                    .where(TELEGRAM_LINK_CODE.CODE_HASH.eq(hash(code)), TELEGRAM_LINK_CODE.EXPIRES_AT.gt(now()))
                    .returning(TELEGRAM_LINK_CODE.ACCOUNT_ID).fetchOptional(TELEGRAM_LINK_CODE.ACCOUNT_ID).orElse(null);
            if (accountId == null) {
                reply(update.chatId(), "Посилання вже не діє. Відкрийте на Новелці «Налаштування → Telegram» і натисніть «Прив’язати» ще раз.");
                return;
            }
            tie(accountId, update);
            return;
        }
        if (text.equals("/stop")) {
            Long accountId = db.deleteFrom(TELEGRAM_LINK).where(TELEGRAM_LINK.CHAT_ID.eq(update.chatId()))
                    .returning(TELEGRAM_LINK.ACCOUNT_ID).fetchOptional(TELEGRAM_LINK.ACCOUNT_ID).orElse(null);
            reply(update.chatId(), accountId == null ? "Цей чат і так не прив’язано до Новелки."
                    : "Готово: сповіщення сюди більше не приходитимуть. Прив’язати знову можна в налаштуваннях на сайті.");
            if (accountId != null) {
                nudge(accountId);
            }
            return;
        }
        reply(update.chatId(), "Це бот сповіщень Новелки. Щоб отримувати їх тут, відкрийте на сайті «Налаштування → Telegram» "
                + "і натисніть «Прив’язати».\n" + texts.link("/me/settings/notifications") + "\n\nВідв’язати: /stop");
    }

    private void tie(long accountId, TelegramApi.Update update) {
        // One chat serves one account, and one account has one chat.
        db.deleteFrom(TELEGRAM_LINK).where(TELEGRAM_LINK.CHAT_ID.eq(update.chatId())
                .and(TELEGRAM_LINK.ACCOUNT_ID.ne(accountId))).execute();
        db.insertInto(TELEGRAM_LINK).set(TELEGRAM_LINK.ACCOUNT_ID, accountId).set(TELEGRAM_LINK.CHAT_ID, update.chatId())
                .set(TELEGRAM_LINK.USERNAME, update.username().isEmpty() ? null : update.username())
                .onConflict(TELEGRAM_LINK.ACCOUNT_ID).doUpdate()
                .set(TELEGRAM_LINK.CHAT_ID, update.chatId())
                .set(TELEGRAM_LINK.USERNAME, update.username().isEmpty() ? null : update.username())
                .set(TELEGRAM_LINK.LINKED_AT, now())
                .execute();
        String nick = db.select(ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchSingle(ACCOUNT.NICK);
        reply(update.chatId(), "Готово! Сповіщення Новелки для " + nick + " тепер приходитимуть сюди. "
                + "Що саме надсилати, можна вибрати в налаштуваннях:\n" + texts.link("/me/settings/notifications") + "\n\nВідв’язати: /stop");
        nudge(accountId);
    }

    private void reply(long chatId, String text) {
        AfterCommit.run(() -> telegram.send(chatId, Texts.escape(text)));
    }

    /** The settings page waiting for the tie hears about it at once. */
    private void nudge(long accountId) {
        AfterCommit.run(() -> live.send(accountId, "telegram", java.util.Map.of()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
