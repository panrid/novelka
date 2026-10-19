package space.panrid.novelka.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER_NEWS;
import static space.panrid.novelka.jooq.Tables.CHAPTER_NEWS_RECIPIENT;
import static space.panrid.novelka.jooq.Tables.EDITION_SUBSCRIPTION;
import static space.panrid.novelka.jooq.Tables.NOTIFICATION;
import static space.panrid.novelka.jooq.Tables.NOTIFICATION_CHOICE;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_LINK;
import static space.panrid.novelka.jooq.Tables.TELEGRAM_OUTBOX;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.jooq.DSLContext;
import org.jooq.InsertValuesStep5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.jooq.tables.records.AccountRecord;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.Accounts.Person;
import space.panrid.novelka.support.Browser.Response;
import space.panrid.novelka.support.FakeTelegram;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import space.panrid.novelka.telegram.TelegramQueue;
import space.panrid.novelka.text.ChapterFiles;
import space.panrid.novelka.text.Chapters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** План 21.9: news of a new chapter goes to thousands of subscribers through a queue, once each. */
@IntegrationTest
class ChapterNewsQueueTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** More than two batches of the worker (500). */
    private static final int READERS = 1_200;

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    ChapterNewsQueue news;

    @Autowired
    TelegramQueue outbox;

    @Autowired
    FakeTelegram telegram;

    @Autowired
    Chapters chapters;

    Person translator;
    long edition;
    List<Long> readers;

    @BeforeEach
    void aTranslationWithManySubscribers() {
        translator = Accounts.signedIn(port, mailbox);
        edition = read(translator.browser().post("/api/studio/editions", """
                {"kind":"human","title":"Популярний переклад %s"}""".formatted(translator.nick()))).path("editionId").asLong();
        publish(1);
        news.drain();
        readers = manyReaders(READERS);
        var subscribe = db.insertInto(EDITION_SUBSCRIPTION, EDITION_SUBSCRIPTION.ACCOUNT_ID, EDITION_SUBSCRIPTION.EDITION_ID);
        for (long reader : readers) {
            subscribe = subscribe.values(reader, edition);
        }
        subscribe.execute();
    }

    @Test
    void publishingQueuesOneJobAndTheWorkerTellsEveryoneOnceAcrossRetries() {
        long anna = readers.getFirst();
        long bohdan = readers.get(1);
        long vira = readers.get(2);
        long halyna = readers.get(READERS - 2);
        long dmytro = readers.getLast();
        long annaChat = tie(anna);
        long bohdanChat = tie(bohdan);
        long viraChat = tie(vira);
        long halynaChat = tie(halyna);
        long dmytroChat = tie(dmytro);
        // Bohdan wants chapters on the site only, Halyna in Telegram only; Vira blocked the bot.
        db.insertInto(NOTIFICATION_CHOICE).set(NOTIFICATION_CHOICE.ACCOUNT_ID, bohdan).set(NOTIFICATION_CHOICE.TOPIC, "chapters")
                .set(NOTIFICATION_CHOICE.ON_SITE, true).set(NOTIFICATION_CHOICE.IN_TELEGRAM, false).execute();
        db.insertInto(NOTIFICATION_CHOICE).set(NOTIFICATION_CHOICE.ACCOUNT_ID, halyna).set(NOTIFICATION_CHOICE.TOPIC, "chapters")
                .set(NOTIFICATION_CHOICE.ON_SITE, false).set(NOTIFICATION_CHOICE.IN_TELEGRAM, true).execute();
        telegram.block(viraChat);
        // Telegram is down for Dmytro twice.
        telegram.failNext(dmytroChat, 2);

        publish(2);
        assertThat(rows()).as("publishing only queues the news").isZero();
        assertThat(db.fetchCount(CHAPTER_NEWS, CHAPTER_NEWS.EDITION_ID.eq(edition).and(CHAPTER_NEWS.STATE.eq("pending"))))
                .isEqualTo(1);

        assertThat(news.runOne()).isTrue();
        assertThat(rows()).as("one batch").isEqualTo(500);
        // The first batch was seen; then the job runs again from the start, as if its cursor were lost.
        db.update(NOTIFICATION).set(NOTIFICATION.READ_AT, OffsetDateTime.now()).where(NOTIFICATION.RECIPIENT_ID.in(readers)).execute();
        db.update(CHAPTER_NEWS).set(CHAPTER_NEWS.AFTER_ACCOUNT_ID, 0L).where(CHAPTER_NEWS.EDITION_ID.eq(edition)).execute();
        news.drain();

        assertThat(rows()).as("everyone but Halyna, once").isEqualTo(READERS - 1);
        assertThat(db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(halyna))).isZero();
        var row = db.selectFrom(NOTIFICATION).where(NOTIFICATION.RECIPIENT_ID.eq(dmytro)).fetchSingle();
        assertThat(row.getKind()).isEqualTo("new_chapters");
        JsonNode payload = JSON.readTree(row.getPayload().data());
        assertThat(payload.path("first").asInt()).isEqualTo(2);
        assertThat(payload.path("last").asInt()).isEqualTo(2);
        assertThat(payload.path("chapterTitle").asString()).as("a single chapter is named").isNotBlank();
        assertThat(db.fetchCount(CHAPTER_NEWS, CHAPTER_NEWS.EDITION_ID.eq(edition).and(CHAPTER_NEWS.STATE.eq("done")))).isEqualTo(2);
        assertThat(db.fetchCount(CHAPTER_NEWS_RECIPIENT, CHAPTER_NEWS_RECIPIENT.ACCOUNT_ID.in(readers))).as("cleared when done").isZero();
        assertThat(news.drain()).isZero();

        outbox.drain();
        assertThat(telegram.to(annaChat)).singleElement().asString().contains("Нова глава 2");
        assertThat(telegram.to(bohdanChat)).as("chapters stay on the site").isEmpty();
        assertThat(telegram.to(viraChat)).isEmpty();
        assertThat(db.fetchExists(TELEGRAM_LINK, TELEGRAM_LINK.ACCOUNT_ID.eq(vira))).as("a blocked bot forgets the chat").isFalse();
        assertThat(telegram.to(halynaChat)).hasSize(1);
        assertThat(telegram.to(dmytroChat)).as("Telegram is down").isEmpty();

        var waiting = db.selectFrom(TELEGRAM_OUTBOX).where(TELEGRAM_OUTBOX.ACCOUNT_ID.eq(dmytro)).fetchSingle();
        assertThat(waiting.getState()).isEqualTo("pending");
        assertThat(waiting.getAttempts()).isEqualTo(1);
        assertThat(waiting.getNotBefore()).as("a pause before the next try").isAfter(OffsetDateTime.now());
        outbox.drain();
        assertThat(telegram.to(dmytroChat)).as("not before the pause").isEmpty();
        due(dmytro);
        outbox.drain();
        due(dmytro);
        outbox.drain();
        assertThat(telegram.to(dmytroChat)).as("the third try").hasSize(1);

        outbox.drain();
        news.drain();
        outbox.drain();
        assertThat(telegram.to(annaChat)).as("once").hasSize(1);
        assertThat(telegram.to(dmytroChat)).hasSize(1);
        assertThat(rows()).isEqualTo(READERS - 1);
    }

    @Test
    void aMessageLeftSendingByACrashIsNotSentAgain() {
        long anna = readers.getFirst();
        long annaChat = tie(anna);
        publish(2);
        news.drain();
        db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.STATE, "sending").set(TELEGRAM_OUTBOX.NOT_BEFORE, OffsetDateTime.now().minusMinutes(1))
                .where(TELEGRAM_OUTBOX.ACCOUNT_ID.eq(anna)).execute();
        outbox.drain();
        assertThat(telegram.to(annaChat)).as("it may have gone before the crash").isEmpty();
        assertThat(db.select(TELEGRAM_OUTBOX.STATE).from(TELEGRAM_OUTBOX).where(TELEGRAM_OUTBOX.ACCOUNT_ID.eq(anna))
                .fetchSingle(TELEGRAM_OUTBOX.STATE)).isEqualTo("unknown");
    }

    @Test
    void chaptersUnpublishedMidwayStopTheRest() {
        long first = readers.getFirst();
        long last = readers.getLast();
        long firstChat = tie(first);
        long lastChat = tie(last);
        publish(2);
        assertThat(news.runOne()).isTrue();
        assertThat(rows()).isEqualTo(500);

        long translatorId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(translator.nick())).fetchSingle(ACCOUNT.ID);
        chapters.trashChapter(edition, 2, translatorId);
        news.drain();
        outbox.drain();

        assertThat(rows()).as("the rest never heard").isEqualTo(500);
        assertThat(db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.eq(last))).isZero();
        assertThat(db.select(CHAPTER_NEWS.STATE).from(CHAPTER_NEWS).where(CHAPTER_NEWS.EDITION_ID.eq(edition))
                .orderBy(CHAPTER_NEWS.ID.desc()).limit(1).fetchSingle(CHAPTER_NEWS.STATE)).isEqualTo("cancelled");
        assertThat(telegram.to(firstChat)).as("queued before, dropped at sending").isEmpty();
        assertThat(telegram.to(lastChat)).isEmpty();
        assertThat(db.select(TELEGRAM_OUTBOX.STATE).from(TELEGRAM_OUTBOX).where(TELEGRAM_OUTBOX.ACCOUNT_ID.eq(first))
                .fetchSingle(TELEGRAM_OUTBOX.STATE)).isEqualTo("cancelled");
    }

    @Test
    void chaptersPublishedTogetherAreOneJobOneRowAndOneMessage() {
        long anna = readers.getFirst();
        long annaChat = tie(anna);
        List<Block> text = List.of(Block.paragraph("b1", List.of(new Span("Текст.", List.of()))));
        chapters.publishNew(edition, List.of(new ChapterFiles.ParsedChapter("Друга", text), new ChapterFiles.ParsedChapter("Третя", text)),
                "import", null);
        assertThat(db.fetchCount(CHAPTER_NEWS, CHAPTER_NEWS.EDITION_ID.eq(edition).and(CHAPTER_NEWS.STATE.eq("pending"))))
                .isEqualTo(1);
        news.drain();
        outbox.drain();
        var row = db.selectFrom(NOTIFICATION).where(NOTIFICATION.RECIPIENT_ID.eq(anna)).fetchSingle();
        JsonNode payload = JSON.readTree(row.getPayload().data());
        assertThat(payload.path("first").asInt()).isEqualTo(2);
        assertThat(payload.path("last").asInt()).isEqualTo(3);
        assertThat(telegram.to(annaChat)).singleElement().asString().contains("Нові глави 2–3");
    }

    /** Readers made straight in the database: signing up a thousand people takes too long. */
    private List<Long> manyReaders(int count) {
        String run = UUID.randomUUID().toString().substring(0, 8);
        InsertValuesStep5<AccountRecord, String, String, String, String, String> insert = db.insertInto(ACCOUNT,
                ACCOUNT.NICK, ACCOUNT.NICK_KEY, ACCOUNT.EMAIL, ACCOUNT.EMAIL_KEY, ACCOUNT.PASSWORD_HASH);
        for (int i = 0; i < count; i++) {
            String nick = "r" + run + "_" + i;
            String email = nick + "@example.com";
            insert = insert.values(nick, nick, email, email, "-");
        }
        return insert.returning(ACCOUNT.ID).fetch(ACCOUNT.ID).stream().sorted().toList();
    }

    private long tie(long accountId) {
        long chat = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 2_000_000_000_000L);
        db.insertInto(TELEGRAM_LINK).set(TELEGRAM_LINK.ACCOUNT_ID, accountId).set(TELEGRAM_LINK.CHAT_ID, chat).execute();
        return chat;
    }

    private void due(long accountId) {
        db.update(TELEGRAM_OUTBOX).set(TELEGRAM_OUTBOX.NOT_BEFORE, OffsetDateTime.now().minusSeconds(1))
                .where(TELEGRAM_OUTBOX.ACCOUNT_ID.eq(accountId), TELEGRAM_OUTBOX.STATE.eq("pending")).execute();
    }

    /** Inbox rows of the subscribers. */
    private int rows() {
        return db.fetchCount(NOTIFICATION, NOTIFICATION.RECIPIENT_ID.in(readers));
    }

    private void publish(int number) {
        translator.browser().post("/api/studio/editions/" + edition + "/chapters", "{}");
        publishDraft(number);
    }

    private void publishDraft(int number) {
        Response published = translator.browser().post("/api/studio/editions/" + edition + "/chapters/" + number + "/publish", """
                {"title":"Глава %d","blocks":[{"id":"b1","type":"paragraph","content":[{"text":"Текст.","marks":[]}]}]}""".formatted(number));
        assertThat(published.status()).as(published.body()).isBetween(200, 299);
    }

    private static JsonNode read(Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 299);
        return JSON.readTree(response.body());
    }
}
