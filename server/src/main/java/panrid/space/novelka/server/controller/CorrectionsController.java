package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.correction.CorrectionRequest;
import panrid.space.novelka.server.correction.CorrectionService;
import panrid.space.novelka.server.correction.ReviewRequest;
import panrid.space.novelka.server.repository.CorrectionRepository;
import panrid.space.novelka.server.list.ListQuery;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/corrections")
public final class CorrectionsController {
    private final AccessService access;
    private final CorrectionService service;
    private final ReaderDatabase database;

    public CorrectionsController(AccessService access, CorrectionService service, ReaderDatabase database) {
        this.access = access; this.service = service; this.database = database;
    }

    @GetMapping
    public Object list(Principal principal, @RequestParam(defaultValue = "false") boolean queue,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "created") String sort,
            @RequestParam(defaultValue = "desc") String direction, @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String novel, @RequestParam(defaultValue = "0") int chapter,
            @RequestParam(defaultValue = "") String authorId, @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo) throws Exception {
        var account = access.require(principal, queue ? Role.EDITOR : Role.READER);
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new CorrectionRepository(jdbc).list(queue ? null : account.id(),
                    new ListQuery(page, size, q, sort, direction), state, novel, chapter, authorId, dateFrom, dateTo), Object.class);
        }
    }

    @GetMapping("/authors")
    public Object authors(Principal principal, @RequestParam(defaultValue = "") String q) throws Exception {
        access.require(principal, Role.EDITOR);
        try (var jdbc = database.open()) {
            return Map.of("items", new CorrectionRepository(jdbc).authors(null, q));
        }
    }

    @GetMapping("/{id}")
    public Object detail(Principal principal, @PathVariable String id) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            var detail = new CorrectionRepository(jdbc).detail(id);
            if (detail == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!account.id().equals(detail.get("author_id"))) access.require(principal, Role.EDITOR);
            var result = new java.util.LinkedHashMap<>(detail);
            result.put("can_review", account.role().includes(Role.EDITOR) && "pending".equals(detail.get("state"))
                    && service.mayReview(account, (String) detail.get("author_id")));
            return result;
        }
    }

    @PostMapping
    public Map<String, String> propose(Principal principal, @RequestBody CorrectionRequest request) throws Exception {
        return Map.of("id", service.propose(access.require(principal, Role.READER), request));
    }

    @PostMapping("/{id}/review")
    public Map<String, String> review(Principal principal, @PathVariable String id, @RequestBody ReviewRequest request) throws Exception {
        service.review(access.require(principal, Role.EDITOR), id, request);
        return Map.of("message", "Рішення збережено.");
    }
}
