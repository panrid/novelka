package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;

import java.security.Principal;
import java.util.Map;

/** Public nickname lookup for picking people (editors, mentions). Never exposes email or role. */
@RestController
@RequestMapping("/api/users")
public final class UsersController {
    private final AccessService access;
    private final ReaderDatabase database;

    public UsersController(AccessService access, ReaderDatabase database) {
        this.access = access;
        this.database = database;
    }

    @GetMapping("/search")
    public Map<String, Object> search(Principal principal, @RequestParam(defaultValue = "") String q) throws Exception {
        access.require(principal, Role.READER);
        if (q.strip().length() > 40) throw new IllegalArgumentException("Нік має містити до 40 символів.");
        try (var jdbc = database.open()) {
            return Map.of("items", new AccountRepository(jdbc).nicknames(q.strip()));
        }
    }
}
