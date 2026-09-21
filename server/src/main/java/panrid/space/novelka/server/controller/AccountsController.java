package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.account.RoleChange;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.core.support.Json;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public final class AccountsController {
    private final ReaderDatabase database;
    private final AccessService access;

    public AccountsController(ReaderDatabase database, AccessService access) {
        this.database = database;
        this.access = access;
    }

    @GetMapping
    public List<Account> list(Principal principal, @RequestParam(defaultValue = "0") int offset) throws Exception {
        access.require(principal, Role.ADMIN);
        if (offset < 0) throw new IllegalArgumentException("Некоректна сторінка.");
        try (var jdbc = database.open()) { return new AccountRepository(jdbc).list(offset); }
    }

    @PostMapping("/{id}/role")
    public Account role(Principal principal, @PathVariable String id, @RequestBody RoleChange request) throws Exception {
        access.require(principal, Role.ADMIN);
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728618)");
                var accounts = new AccountRepository(jdbc);
                var actor = accounts.find(principal.getName());
                var target = accounts.byId(id);
                if (target == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                Role role = request.role();
                if (actor == null || !actor.role().includes(Role.ADMIN) || role == null || role == Role.OWNER
                        || target.role() == Role.OWNER || actor.id().equals(id)
                        || (actor.role() == Role.ADMIN && (target.role().includes(Role.ADMIN) || role.includes(Role.ADMIN))))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                accounts.role(id, role);
                new AuditRepository(jdbc).add(actor.id(), "account.role", id,
                        Map.of("from", target.role(), "to", role));
                return accounts.byId(id);
            });
        }
    }

    @GetMapping("/audit")
    public Object audit(Principal principal, @RequestParam(defaultValue = "0") int offset) throws Exception {
        access.require(principal, Role.OWNER);
        if (offset < 0) throw new IllegalArgumentException("Некоректна сторінка.");
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new AuditRepository(jdbc).list(offset), Object.class);
        }
    }
}
