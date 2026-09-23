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

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public final class TasksController {
    private final ReaderDatabase database;
    private final AccessService access;
    private final TaskService service;

    public TasksController(ReaderDatabase database, AccessService access, TaskService service) {
        this.database = database; this.access = access; this.service = service;
    }

    @GetMapping
    public Object list(Principal principal, @RequestParam(defaultValue = "0") int offset) throws Exception {
        access.require(principal, Role.ADMIN);
        if (offset < 0) throw new IllegalArgumentException("Некоректна сторінка.");
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new TaskRepository(jdbc).list(offset), Object.class);
        }
    }

    @PostMapping
    public Map<String, String> create(Principal principal, @RequestBody TaskRequest request) throws Exception {
        return Map.of("id", service.enqueue(access.require(principal, Role.ADMIN), request));
    }

    @GetMapping("/{id}")
    public Object read(Principal principal, @PathVariable String id) throws Exception {
        access.require(principal, Role.ADMIN);
        try (var jdbc = database.open()) {
            var task = new TaskRepository(jdbc).find(id);
            if (task == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            return Json.M.convertValue(task, Object.class);
        }
    }

    @PostMapping("/{id}/cancel")
    public Map<String, String> cancel(Principal principal, @PathVariable String id) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        try (var jdbc = database.open()) {
            jdbc.transaction(() -> {
                new TaskRepository(jdbc).cancel(id);
                new AuditRepository(jdbc).add(actor.id(), "task.cancel", id, Map.of());
                return null;
            });
        }
        return Map.of("message", "Запит на зупинку збережено. Поточний HTTP-виклик може завершитися.");
    }
}
