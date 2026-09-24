package panrid.space.novelka.server.chat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.mention.Mentions;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.ChatRepository;
import panrid.space.novelka.server.repository.PersonalNotificationRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Community chat for signed-in users. Authors delete their messages; moderators hide messages, which readers can still reveal. */
@Service
public final class ChatService {
    static final int PAGE = 30;
    private final ReaderDatabase database;

    public ChatService(ReaderDatabase database) { this.database = database; }

    public Map<String, Object> history(Account viewer, long before) throws Exception {
        if (before < 0) throw new IllegalArgumentException("Некоректний курсор.");
        try (var jdbc = database.open()) {
            var rows = new ChatRepository(jdbc).history(before, PAGE + 1);
            boolean more = rows.size() > PAGE;
            if (more) rows = rows.subList(0, PAGE);
            return Map.of("items", decorate(viewer, rows), "nextCursor", more ? ((Number) rows.getLast().get("id")).longValue() : 0,
                    "names", names(jdbc, rows));
        }
    }

    public Map<String, Object> updates(Account viewer, long after) throws Exception {
        if (after < 0) throw new IllegalArgumentException("Некоректний курсор.");
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            var items = chat.after(after, 100);
            var moderated = chat.recentlyModerated();
            var all = new ArrayList<>(items);
            all.addAll(moderated);
            return Map.of("items", decorate(viewer, items), "deleted", chat.recentlyDeleted(),
                    "moderated", decorate(viewer, moderated), "names", names(jdbc, all));
        }
    }

    public long send(Account author, ChatMessageRequest request) throws Exception {
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty() || body.length() > 1000) throw new IllegalArgumentException("Повідомлення: від 1 до 1000 символів.");
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            if (chat.recentlyPosted(author.id())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Надто часто. Зачекайте секунду.");
            String parentAuthor = null;
            if (request.replyTo() != null) {
                var parent = chat.get(request.replyTo());
                if (parent == null || parent.get("deleted_at") != null) throw new IllegalArgumentException("Повідомлення, на яке ви відповідаєте, не знайдено.");
                parentAuthor = (String) parent.get("author_id");
            }
            String encoded = Mentions.encode(jdbc, body);
            String replied = parentAuthor;
            return jdbc.transaction(() -> {
                long id = chat.create(author.id(), encoded, request.replyTo());
                var notifications = new PersonalNotificationRepository(jdbc);
                var mentioned = Mentions.mentioned(encoded);
                for (var account : mentioned) if (!account.equals(author.id())) notifications.chat("mention", account, author.id(), id);
                if (replied != null && !replied.equals(author.id()) && !mentioned.contains(replied))
                    notifications.chat("reply", replied, author.id(), id);
                return id;
            });
        }
    }

    public void delete(Account actor, long id) throws Exception {
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            var message = chat.get(id);
            if (message == null || message.get("deleted_at") != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!actor.id().equals(message.get("author_id")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Видалити можна лише власне повідомлення. Модератори приховують чужі.");
            chat.delete(id, actor.id());
        }
    }

    public void hide(Account moderator, long id, boolean hidden, String reason) throws Exception {
        if (!moderator.role().includes(Role.MODERATOR)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            var message = chat.get(id);
            if (message == null || message.get("deleted_at") != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            jdbc.transaction(() -> {
                chat.hide(id, moderator.id(), hidden, reason);
                new AuditRepository(jdbc).add(moderator.id(), hidden ? "chat.hide" : "chat.unhide", String.valueOf(id),
                        Map.of("author", message.get("author_id"), "reason", reason));
                return null;
            });
        }
    }

    private static Map<String, String> names(panrid.space.novelka.core.persistence.JdbcSession jdbc, List<Map<String, Object>> rows) throws Exception {
        var bodies = new ArrayList<String>();
        for (var row : rows) { bodies.add((String) row.get("body")); bodies.add((String) row.get("reply_body")); }
        return Mentions.names(jdbc, bodies);
    }

    private static List<Map<String, Object>> decorate(Account viewer, List<Map<String, Object>> rows) {
        var items = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var item = new LinkedHashMap<>(row);
            item.put("can_delete", viewer.id().equals(row.get("author_id")));
            item.put("can_moderate", viewer.role().includes(Role.MODERATOR));
            items.add(item);
        }
        return items;
    }
}
