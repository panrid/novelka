package space.panrid.novelka.telegram;

import java.util.List;

/** The Telegram Bot API, as much of it as the site uses. Tests replace it with a fake. */
public interface TelegramApi {

    /** A text someone wrote to the bot. */
    record Update(long updateId, long chatId, String text, String username) {
    }

    /** False when the site has no bot: the settings do not offer Telegram. */
    boolean configured();

    /** The bot's @username, for t.me links. */
    String botUsername();

    /**
     * Sends a message with HTML markup.
     *
     * @return false when the chat is gone for good (the person blocked the bot or deleted it)
     */
    boolean send(long chatId, String html);

    /**
     * Sends a picture Telegram fetches from {@code photoUrl}, with an HTML caption. When Telegram
     * cannot fetch it (a site it cannot reach), the caption goes as a plain message.
     *
     * @return false when the chat is gone for good
     */
    boolean sendPhoto(long chatId, String photoUrl, String html);

    /** How a message from the queue went. */
    enum Outcome {
        SENT,
        /** The chat is gone for good: the person blocked the bot or deleted it. */
        GONE,
        /** Not now (too many messages, Telegram or the network down): try again later. */
        RETRY,
        /** Telegram refused this message; sending it again would not help. */
        REFUSED
    }

    /** @param retryAfter for {@link Outcome#RETRY}: how long Telegram asked to wait, or {@code null} */
    record Delivery(Outcome outcome, java.time.Duration retryAfter) {

        public static Delivery of(Outcome outcome) {
            return new Delivery(outcome, null);
        }
    }

    /**
     * {@link #sendPhoto} (or {@link #send} without a picture) for the queue, which needs to know
     * whether to try again.
     */
    default Delivery deliver(long chatId, String photoUrl, String html) {
        boolean kept = photoUrl == null ? send(chatId, html) : sendPhoto(chatId, photoUrl, html);
        return Delivery.of(kept ? Outcome.SENT : Outcome.GONE);
    }

    /** Telegram posts updates to {@code url} with {@code secret} in a header. */
    void setWebhook(String url, String secret);

    /** Long polling, for development where Telegram cannot reach the site. Waits up to ~25 s. */
    List<Update> updates(long offset);

    /** Stops the webhook, so polling works. */
    void deleteWebhook();
}
