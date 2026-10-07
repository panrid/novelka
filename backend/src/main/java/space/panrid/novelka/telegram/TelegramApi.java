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

    /** Telegram posts updates to {@code url} with {@code secret} in a header. */
    void setWebhook(String url, String secret);

    /** Long polling, for development where Telegram cannot reach the site. Waits up to ~25 s. */
    List<Update> updates(long offset);

    /** Stops the webhook, so polling works. */
    void deleteWebhook();
}
