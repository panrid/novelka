package space.panrid.novelka.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import space.panrid.novelka.telegram.TelegramApi;

/** Telegram in tests: keeps what the bot sent; a chat can «block» the bot. */
public class FakeTelegram implements TelegramApi {

    public record Sent(long chatId, String html) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<Long> blocked = ConcurrentHashMap.newKeySet();

    /** Everything the bot wrote to one chat, oldest first. */
    public List<String> to(long chatId) {
        return sent.stream().filter(message -> message.chatId() == chatId).map(Sent::html).toList();
    }

    public void block(long chatId) {
        blocked.add(chatId);
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String botUsername() {
        return "novelka_test_bot";
    }

    @Override
    public boolean send(long chatId, String html) {
        if (blocked.contains(chatId)) {
            return false;
        }
        sent.add(new Sent(chatId, html));
        return true;
    }

    @Override
    public void setWebhook(String url, String secret) {
    }

    @Override
    public List<Update> updates(long offset) {
        return List.of();
    }

    @Override
    public void deleteWebhook() {
    }
}
