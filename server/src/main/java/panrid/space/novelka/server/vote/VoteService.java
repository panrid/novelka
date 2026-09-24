package panrid.space.novelka.server.vote;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.novel.NovelAccessService;
import panrid.space.novelka.server.repository.VoteRepository;

/** Shared voting rules for every votable target: one active vote per account, change or remove at any time. */
@Service
public final class VoteService {
    private final ReaderDatabase database;
    private final NovelAccessService novels;

    public VoteService(ReaderDatabase database, NovelAccessService novels) { this.database = database; this.novels = novels; }

    public VoteSummary vote(Account account, String type, String target, int value) throws Exception {
        if (value < -1 || value > 1) throw new IllegalArgumentException("Голос: 1, -1 або 0, щоб прибрати.");
        try (var jdbc = database.open()) {
            String id = type.equals("novel") ? novels.visible(jdbc, account, target) : canonical(jdbc, type, target);
            var votes = new VoteRepository(jdbc);
            votes.set(type, id, account.id(), value);
            return votes.summary(type, id, account.id());
        }
    }

    /** Resolves aliases and rejects missing or removed targets, so votes never point at nothing. */
    private static String canonical(JdbcSession jdbc, String type, String target) throws Exception {
        switch (type) {
            case "comment" -> {
                if (!target.matches("[1-9]\\d{0,17}") || jdbc.rows("SELECT 1 FROM comments WHERE id=?::bigint AND deleted_at IS NULL", target).isEmpty())
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                return target;
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
