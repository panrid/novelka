package panrid.space.novelka.server.library;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;
import panrid.space.novelka.server.repository.LibraryRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Personal reading lists. Each account keeps at most one shelf per novel; nobody else sees it. */
@Service
public final class LibraryService {
    /** Shelves in display order. */
    public static final List<String> STATUSES = List.of("reading", "planned", "completed", "on_hold", "dropped");
    private final ReaderDatabase database;

    public LibraryService(ReaderDatabase database) { this.database = database; }

    public LibraryStatus set(Account account, String novel, String status) throws Exception {
        String value = status == null ? "" : status;
        if (!value.isEmpty() && !STATUSES.contains(value)) throw new IllegalArgumentException("Невідомий список бібліотеки.");
        try (var jdbc = database.open()) {
            String id = resolve(new NovelRepository(jdbc), novel);
            new LibraryRepository(jdbc).set(account.id(), id, value);
            return new LibraryStatus(value.isEmpty() ? null : value);
        }
    }

    public ListPage<LibraryItem> list(Account account, String status, ListQuery query) throws Exception {
        String value = status == null ? "" : status;
        if (!value.isEmpty() && !STATUSES.contains(value)) throw new IllegalArgumentException("Невідомий список бібліотеки.");
        try (var jdbc = database.open()) {
            var page = new LibraryRepository(jdbc).list(account.id(), value, query);
            var items = page.items().stream().map(row -> {
                var novel = Json.decode(row.get("data").toString(), Novel.class);
                return new LibraryItem(novel.id(), novel.displayTitle(), novel.displayAuthor(), novel.chapterCount(),
                        ((Number) row.get("ready_chapters")).intValue(), (String) row.get("status"),
                        row.get("updated_at") instanceof Timestamp time ? time.toInstant().toString() : String.valueOf(row.get("updated_at")));
            }).toList();
            return ListPage.of(items, query, page.total());
        }
    }

    /** Unknown novels are 404, as in the reader, rather than a validation error. */
    private static String resolve(NovelRepository novels, String reference) throws Exception {
        try {
            return novels.resolveNovel(reference);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public Map<String, Long> counts(Account account) throws Exception {
        try (var jdbc = database.open()) {
            return new LibraryRepository(jdbc).counts(account.id(), STATUSES);
        }
    }
}
