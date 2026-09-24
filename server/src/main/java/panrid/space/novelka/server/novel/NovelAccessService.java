package panrid.space.novelka.server.novel;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.NovelAccessRepository;
import panrid.space.novelka.server.settings.SettingsService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rights that belong to a novel rather than to a global role. The translator (owner) and administrators manage the novel;
 * corrections are reviewed by them, by editors the translator picked, or by everyone when review is open.
 * The translator may approve their own corrections; administrators only when the owner enabled self-approval in settings.
 */
@Service
public final class NovelAccessService {
    private final ReaderDatabase database;
    private final SettingsService settings;

    public NovelAccessService(ReaderDatabase database, SettingsService settings) {
        this.database = database; this.settings = settings;
    }

    public boolean canManage(JdbcSession jdbc, Account account, String novel) throws Exception {
        return account.role().includes(Role.ADMIN) || account.id().equals(new NovelAccessRepository(jdbc).novel(novel).get("owner_id"));
    }

    public boolean canReview(JdbcSession jdbc, Account account, String novel) throws Exception {
        if (account.role().includes(Role.ADMIN)) return true;
        var access = new NovelAccessRepository(jdbc);
        var row = access.novel(novel);
        return account.id().equals(row.get("owner_id")) || Boolean.TRUE.equals(row.get("open_review")) || access.isEditor(novel, account.id());
    }

    /** Whether the reviewer may decide on a correction written by {@code author}. */
    public boolean mayDecide(JdbcSession jdbc, Account reviewer, String novel, String author) throws Exception {
        if (!canReview(jdbc, reviewer, novel)) return false;
        if (!reviewer.id().equals(author)) return true;
        return reviewer.id().equals(new NovelAccessRepository(jdbc).novel(novel).get("owner_id"))
                || reviewer.role().includes(Role.ADMIN) && settings.read().adminSelfApproval();
    }

    /** Whether the account may open the correction queue at all. */
    public boolean reviewsAnything(Account account) throws Exception {
        if (account.role().includes(Role.ADMIN)) return true;
        try (var jdbc = database.open()) {
            return new NovelAccessRepository(jdbc).reviewsAnything(account.id());
        }
    }

    public NovelEditors editors(Account account, String reference) throws Exception {
        try (var jdbc = database.open()) {
            String novel = manageable(jdbc, account, reference);
            return view(jdbc, novel);
        }
    }

    public NovelEditors addEditor(Account account, String reference, String editor) throws Exception {
        try (var jdbc = database.open()) {
            String novel = manageable(jdbc, account, reference);
            var access = new NovelAccessRepository(jdbc);
            if (editor == null || new AccountRepository(jdbc).byId(editor) == null) throw new IllegalArgumentException("Користувача не знайдено.");
            if (editor.equals(access.novel(novel).get("owner_id"))) throw new IllegalArgumentException("Перекладач і так вирішує щодо правок.");
            if (access.addEditor(novel, editor, account.id()))
                new AuditRepository(jdbc).add(account.id(), "novel.editor.add", novel, Map.of("accountId", editor));
            return view(jdbc, novel);
        }
    }

    public NovelEditors removeEditor(Account account, String reference, String editor) throws Exception {
        try (var jdbc = database.open()) {
            String novel = manageable(jdbc, account, reference);
            if (new NovelAccessRepository(jdbc).removeEditor(novel, editor))
                new AuditRepository(jdbc).add(account.id(), "novel.editor.remove", novel, Map.of("accountId", editor));
            return view(jdbc, novel);
        }
    }

    public NovelEditors openReview(Account account, String reference, boolean open) throws Exception {
        try (var jdbc = database.open()) {
            String novel = manageable(jdbc, account, reference);
            new NovelAccessRepository(jdbc).openReview(novel, open);
            new AuditRepository(jdbc).add(account.id(), "novel.review." + (open ? "open" : "close"), novel, Map.of());
            return view(jdbc, novel);
        }
    }

    /**
     * Resolves an ID or alias for reading. A hidden novel exists only for its translator and administrators;
     * for everyone else it is 404, exactly like an unknown one.
     */
    public String visible(JdbcSession jdbc, Account viewer, String reference) throws Exception {
        String novel = resolve(jdbc, reference);
        if (Boolean.TRUE.equals(new NovelAccessRepository(jdbc).novel(novel).get("hidden")) && (viewer == null || !canManage(jdbc, viewer, novel)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return novel;
    }

    /** Only administrators hide or restore a novel; the reason is shown to its translator. */
    public void hide(Account admin, String reference, boolean hidden, String reason) throws Exception {
        if (!admin.role().includes(Role.ADMIN)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try (var jdbc = database.open()) {
            String novel = resolve(jdbc, reference);
            jdbc.transaction(() -> {
                new NovelAccessRepository(jdbc).hide(novel, admin.id(), hidden, reason);
                new AuditRepository(jdbc).add(admin.id(), hidden ? "novel.hide" : "novel.unhide", novel, Map.of("reason", reason));
                return null;
            });
        }
    }

    private static String resolve(JdbcSession jdbc, String reference) throws Exception {
        try {
            return new NovelRepository(jdbc).resolveNovel(reference);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Resolves an ID or alias to the canonical ID and requires management rights; unknown novels are 404. */
    public String manageable(JdbcSession jdbc, Account account, String reference) throws Exception {
        String novel = resolve(jdbc, reference);
        if (!canManage(jdbc, account, novel)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return novel;
    }

    private NovelEditors view(JdbcSession jdbc, String novel) throws Exception {
        var access = new NovelAccessRepository(jdbc);
        var row = access.novel(novel);
        Map<String, Object> owner = null;
        if (row.get("owner_id") != null) {
            owner = new LinkedHashMap<>();
            owner.put("id", row.get("owner_id"));
            owner.put("username", row.get("owner_name"));
        }
        return new NovelEditors(owner, Boolean.TRUE.equals(row.get("open_review")), access.editors(novel));
    }
}
