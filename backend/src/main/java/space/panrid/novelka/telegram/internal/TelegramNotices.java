package space.panrid.novelka.telegram.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.AccountMessenger;
import space.panrid.novelka.messaging.MessagePosted;
import space.panrid.novelka.notification.NotificationAdded;
import space.panrid.novelka.notification.NotificationChoices;
import space.panrid.novelka.telegram.TelegramApi;

/** Repeats in Telegram what the person chose: the inbox, new chapters, messages; account news always. */
@Component
class TelegramNotices implements AccountMessenger {

    private final TelegramApi telegram;
    private final TelegramLinks links;
    private final Texts texts;
    private final NotificationChoices choices;
    /** Account news is told from the request that changed it; the request does not wait for Telegram. */
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    TelegramNotices(TelegramApi telegram, TelegramLinks links, Texts texts, NotificationChoices choices) {
        this.telegram = telegram;
        this.links = links;
        this.texts = texts;
        this.choices = choices;
    }

    @ApplicationModuleListener
    void on(NotificationAdded added) {
        if (!telegram.configured() || !choices.inTelegram(added.recipientId(), NotificationChoices.Topic.of(added.kind()))) {
            return;
        }
        links.of(added.recipientId())
                .ifPresent(link -> {
                    String html = texts.notification(added.kind(), added.payload());
                    String photo = texts.photo(added.kind(), added.payload());
                    if (photo == null) {
                        send(link.chatId(), html);
                    } else if (!telegram.sendPhoto(link.chatId(), photo, html)) {
                        links.forget(link.chatId());
                    }
                });
    }

    @ApplicationModuleListener
    void on(MessagePosted posted) {
        if (!telegram.configured()) {
            return;
        }
        String html = texts.message(posted.kind(), posted.title(), posted.authorNick(), posted.excerpt(), posted.conversationId());
        for (long person : posted.firstUnread()) {
            if (choices.inTelegram(person, NotificationChoices.Topic.MESSAGES)) {
                links.of(person).ifPresent(link -> send(link.chatId(), html));
            }
        }
    }

    @Override
    public void tell(long accountId, String text, String link) {
        if (!telegram.configured()) {
            return;
        }
        background.submit(() -> links.of(accountId).ifPresent(tied -> send(tied.chatId(), texts.account(text, link))));
    }

    private void send(long chatId, String html) {
        if (!telegram.send(chatId, html)) {
            links.forget(chatId);
        }
    }
}
