package panrid.space.novelka.server.comment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.CommentRepository;
import panrid.space.novelka.server.repository.VoteRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Novel and chapter discussions. Authors edit and delete their own comments; ADMIN and OWNER moderate.
 * Deletion is soft, so audit and votes keep valid references, and a nickname change never breaks a comment.
 */
@Service
public final class CommentService {
    static final int PAGE = 20;
    private final ReaderDatabase database;

    public CommentService(ReaderDatabase database) { this.database = database; }

    public Map<String, Object> page(String reference, int chapter, long before, Account viewer) throws Exception {
        if (chapter < 0 || before < 0) throw new IllegalArgumentException("Некоректна глава або курсор.");
        try (var jdbc = database.open()) {
            String novel = new NovelRepository(jdbc).resolveNovel(reference);
            var rows = new CommentRepository(jdbc).page(novel, chapter, before, PAGE + 1);
            boolean more = rows.size() > PAGE;
            if (more) rows = rows.subList(0, PAGE);
            var votes = new VoteRepository(jdbc).summaries("comment",
                    rows.stream().map(row -> String.valueOf(row.get("id"))).toList(), viewer == null ? null : viewer.id());
            var items = new ArrayList<Map<String, Object>>();
            for (var row : rows) {
                var item = new LinkedHashMap<>(row);
                boolean own = viewer != null && viewer.id().equals(row.get("author_id"));
                item.put("rating", votes.get(String.valueOf(row.get("id"))));
                item.put("can_edit", own);
                item.put("can_delete", own || viewer != null && viewer.role().includes(Role.ADMIN));
                items.add(item);
            }
            return Map.of("items", items, "nextCursor", more ? ((Number) rows.getLast().get("id")).longValue() : 0);
        }
    }

    public long create(Account author, String reference, CommentRequest request) throws Exception {
        int chapter = request.chapter() == null ? 0 : request.chapter();
        String body = body(request.body());
        try (var jdbc = database.open()) {
            var novels = new NovelRepository(jdbc);
            String novel = novels.resolveNovel(reference);
            if (chapter < 0 || chapter > novels.novel(novel).chapterCount()) throw new IllegalArgumentException("Некоректний номер глави.");
            var comments = new CommentRepository(jdbc);
            if (comments.recentlyPosted(author.id()))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Зачекайте кілька секунд перед наступним коментарем.");
            return comments.create(novel, chapter, author.id(), body);
        }
    }

    public void edit(Account author, long id, CommentRequest request) throws Exception {
        String body = body(request.body());
        try (var jdbc = database.open()) {
            var comments = new CommentRepository(jdbc);
            var comment = live(comments, id);
            if (!author.id().equals(comment.get("author_id"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Редагувати можна лише власний коментар.");
            comments.edit(id, body);
        }
    }

    public void delete(Account actor, long id) throws Exception {
        try (var jdbc = database.open()) {
            var comments = new CommentRepository(jdbc);
            var comment = live(comments, id);
            boolean own = actor.id().equals(comment.get("author_id"));
            if (!own && !actor.role().includes(Role.ADMIN)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            jdbc.transaction(() -> {
                comments.delete(id, actor.id());
                if (!own) new AuditRepository(jdbc).add(actor.id(), "comment.moderate", String.valueOf(id), Map.of("author", comment.get("author_id")));
                return null;
            });
        }
    }

    private static Map<String, Object> live(CommentRepository comments, long id) throws Exception {
        var comment = comments.get(id);
        if (comment == null || comment.get("deleted_at") != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return comment;
    }

    private static String body(String value) {
        String body = value == null ? "" : value.strip();
        if (body.isEmpty() || body.length() > 5000) throw new IllegalArgumentException("Коментар: від 1 до 5000 символів.");
        return body;
    }
}
