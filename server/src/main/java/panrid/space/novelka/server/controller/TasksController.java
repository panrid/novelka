package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.*;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.TaskRepository;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.task.TaskRequest;
import panrid.space.novelka.server.task.TaskService;
import panrid.space.novelka.server.list.ListQuery;
import panrid.space.novelka.server.settings.SettingsService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public final class TasksController {
    private final ReaderDatabase database;
    private final AccessService access;
    private final TaskService service;
    private final SettingsService settings;

    public TasksController(ReaderDatabase database, AccessService access, TaskService service, SettingsService settings) {
        this.database = database; this.access = access; this.service = service; this.settings = settings;
    }

    /** Administrators see every task; everyone else only their own. */
    private static String own(panrid.space.novelka.server.account.Account account) {
        return account.role().includes(Role.ADMIN) ? null : account.id();
    }

    /** What a new task uses unless overridden: administrators cannot read the owner's full settings. */
    @GetMapping("/defaults")
    public Map<String, Object> defaults(Principal principal) throws Exception {
        access.require(principal, Role.READER);
        var snapshot = settings.read();
        var models = new java.util.LinkedHashMap<String, String>();
        snapshot.stages().forEach(stage -> models.put(stage.stage(), stage.model()));
        return Map.of("models", models, "maxBudgetUsd", snapshot.maxBudgetUsd());
    }

    @GetMapping
    public Object list(Principal principal, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "created") String sort, @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "") String state, @RequestParam(defaultValue = "") String operation,
            @RequestParam(defaultValue = "") String novel) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new TaskRepository(jdbc).list(own(account), new ListQuery(page, size, q, sort, direction), state, operation, novel), Object.class);
        }
    }

    @PostMapping
    public Map<String, String> create(Principal principal, @RequestBody TaskRequest request) throws Exception {
        return Map.of("id", service.enqueue(access.require(principal, Role.READER), request));
    }

    @GetMapping("/{id}")
    public Object read(Principal principal, @PathVariable String id) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            var task = new TaskRepository(jdbc).find(id);
            if (task == null || own(account) != null && !account.id().equals(task.get("actor_id"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            return Json.M.convertValue(task, Object.class);
        }
    }

    @PostMapping("/{id}/cancel")
    public Map<String, String> cancel(Principal principal, @PathVariable String id) throws Exception {
        var actor = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            var task = new TaskRepository(jdbc).find(id);
            if (task == null || own(actor) != null && !actor.id().equals(task.get("actor_id")))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            jdbc.transaction(() -> {
                new TaskRepository(jdbc).cancel(id);
                new AuditRepository(jdbc).add(actor.id(), "task.cancel", id, Map.of());
                return null;
            });
        }
        return Map.of("message", "Запит на зупинку збережено. Поточний HTTP-виклик може завершитися.");
    }
}
