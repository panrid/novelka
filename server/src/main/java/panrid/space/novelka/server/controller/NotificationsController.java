package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.NotificationRepository;

import java.security.Principal;

@RestController
@RequestMapping("/api/notifications")
public final class NotificationsController {
    private final AccessService access;
    private final ReaderDatabase database;

    public NotificationsController(AccessService access, ReaderDatabase database) {
        this.access = access;
        this.database = database;
    }

    @GetMapping
    public Object list(Principal principal, @RequestParam(defaultValue = "0") long before) throws Exception {
        var account = access.require(principal, Role.READER);
        if (before < 0) throw new IllegalArgumentException("Некоректний курсор сповіщень.");
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new NotificationRepository(jdbc).list(account, before), Object.class);
        }
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Principal principal, @PathVariable long id) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            if (!new NotificationRepository(jdbc).read(account, id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Principal principal, @RequestParam long through) throws Exception {
        var account = access.require(principal, Role.READER);
        if (through < 0) throw new IllegalArgumentException("Некоректний номер сповіщення.");
        try (var jdbc = database.open()) { new NotificationRepository(jdbc).readThrough(account, through); }
    }
}
