package panrid.space.novelka.server.chat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.ChatRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Community chat for signed-in users. Authors delete their messages; ADMIN and OWNER moderate. */
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
            return Map.of("items", decorate(viewer, rows), "nextCursor", more ? ((Number) rows.getLast().get("id")).longValue() : 0);
        }
    }

    public Map<String, Object> updates(Account viewer, long after) throws Exception {
        if (after < 0) throw new IllegalArgumentException("Некоректний курсор.");
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            return Map.of("items", decorate(viewer, chat.after(after, 100)), "deleted", chat.recentlyDeleted());
        }
    }

    public long send(Account author, ChatMessageRequest request) throws Exception {
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty() || body.length() > 1000) throw new IllegalArgumentException("Повідомлення: від 1 до 1000 символів.");
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            if (chat.recentlyPosted(author.id())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Надто часто. Зачекайте секунду.");
            return chat.create(author.id(), body);
        }
    }

    public void delete(Account actor, long id) throws Exception {
        try (var jdbc = database.open()) {
            var chat = new ChatRepository(jdbc);
            var message = chat.get(id);
            if (message == null || message.get("deleted_at") != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            boolean own = actor.id().equals(message.get("author_id"));
            if (!own && !actor.role().includes(Role.ADMIN)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            jdbc.transaction(() -> {
                chat.delete(id, actor.id());
                if (!own) new AuditRepository(jdbc).add(actor.id(), "chat.moderate", String.valueOf(id), Map.of("author", message.get("author_id")));
                return null;
            });
        }
    }

    private static List<Map<String, Object>> decorate(Account viewer, List<Map<String, Object>> rows) {
        var items = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var item = new LinkedHashMap<>(row);
            item.put("can_delete", viewer.id().equals(row.get("author_id")) || viewer.role().includes(Role.ADMIN));
            items.add(item);
        }
        return items;
    }
}
