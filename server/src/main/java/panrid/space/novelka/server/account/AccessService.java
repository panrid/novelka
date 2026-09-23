package panrid.space.novelka.server.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;

import java.security.Principal;

@Service
public final class AccessService {
    private final ReaderDatabase database;

    public AccessService(ReaderDatabase database) { this.database = database; }

    public Account current(Principal principal) throws Exception {
        if (principal == null) return null;
        try (var jdbc = database.open()) {
            return new AccountRepository(jdbc).byId(principal.getName());
        }
    }

    /** Read roles from DB on every operation: demotions affect existing sessions immediately. */
    public Account require(Principal principal, Role minimum) throws Exception {
        var account = current(principal);
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!account.role().includes(minimum)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return account;
    }
}
