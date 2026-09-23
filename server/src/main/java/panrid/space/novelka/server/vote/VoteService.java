package panrid.space.novelka.server.vote;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.VoteRepository;

/** Shared voting rules for every votable target: one active vote per account, change or remove at any time. */
@Service
public final class VoteService {
    private final ReaderDatabase database;

    public VoteService(ReaderDatabase database) { this.database = database; }

    public VoteSummary vote(Account account, String type, String target, int value) throws Exception {
        if (value < -1 || value > 1) throw new IllegalArgumentException("Голос: 1, -1 або 0, щоб прибрати.");
        try (var jdbc = database.open()) {
            String id = canonical(jdbc, type, target);
            var votes = new VoteRepository(jdbc);
            votes.set(type, id, account.id(), value);
            return votes.summary(type, id, account.id());
        }
    }

    /** Resolves aliases and rejects missing or removed targets, so votes never point at nothing. */
    private static String canonical(JdbcSession jdbc, String type, String target) throws Exception {
        switch (type) {
            case "novel" -> { return new NovelRepository(jdbc).resolveNovel(target); }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
