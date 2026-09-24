package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.novel.NovelAccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.correction.CorrectionEdit;
import panrid.space.novelka.server.correction.CorrectionRequest;
import panrid.space.novelka.server.correction.ReplaceRequest;
import panrid.space.novelka.server.correction.SubmitRequest;
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
    private final NovelAccessService novels;

    public CorrectionsController(AccessService access, CorrectionService service, ReaderDatabase database, NovelAccessService novels) {
        this.access = access; this.service = service; this.database = database; this.novels = novels;
    }

    /** The queue shows corrections of novels the account may review; null means every novel (administrators). */
    private String reviewer(Account account) throws Exception {
        if (!novels.reviewsAnything(account)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return account.role().includes(Role.ADMIN) ? null : account.id();
    }

    @GetMapping
    public Object list(Principal principal, @RequestParam(defaultValue = "false") boolean queue,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "created") String sort,
            @RequestParam(defaultValue = "desc") String direction, @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String novel, @RequestParam(defaultValue = "0") int chapter,
            @RequestParam(defaultValue = "") String authorId, @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo) throws Exception {
        var account = access.require(principal, Role.READER);
        String reviewer = queue ? reviewer(account) : null;
        try (var jdbc = database.open()) {
            return Json.M.convertValue(new CorrectionRepository(jdbc).list(queue ? null : account.id(), reviewer,
                    new ListQuery(page, size, q, sort, direction), state, novel, chapter, authorId, dateFrom, dateTo), Object.class);
        }
    }

    @GetMapping("/authors")
    public Object authors(Principal principal, @RequestParam(defaultValue = "") String q) throws Exception {
        String reviewer = reviewer(access.require(principal, Role.READER));
        try (var jdbc = database.open()) {
            return Map.of("items", new CorrectionRepository(jdbc).authors(null, reviewer, q));
        }
    }

    @GetMapping("/{id}")
    public Object detail(Principal principal, @PathVariable String id) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            var detail = new CorrectionRepository(jdbc).detail(id);
            if (detail == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            String novel = (String) detail.get("novel_id");
            if (!account.id().equals(detail.get("author_id")) && !novels.canReview(jdbc, account, novel))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            var result = new java.util.LinkedHashMap<>(detail);
            result.put("can_review", "pending".equals(detail.get("state")) && novels.mayDecide(jdbc, account, novel, (String) detail.get("author_id")));
            return result;
        }
    }

    @PostMapping
    public Map<String, String> propose(Principal principal, @RequestBody CorrectionRequest request) throws Exception {
        return Map.of("id", service.propose(access.require(principal, Role.READER), request));
    }

    @PostMapping("/replace")
    public Map<String, String> replace(Principal principal, @RequestBody ReplaceRequest request) throws Exception {
        return Map.of("id", service.proposeReplace(access.require(principal, Role.READER), request));
    }

    @GetMapping("/replace-preview")
    public Map<String, Object> preview(Principal principal, @RequestParam String novel, @RequestParam(defaultValue = "0") int chapter,
            @RequestParam String find, @RequestParam(defaultValue = "chapter") String scope) throws Exception {
        access.require(principal, Role.READER);
        return service.preview(novel, chapter, find, scope);
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(Principal principal, @RequestBody SubmitRequest request) throws Exception {
        return service.submit(access.require(principal, Role.READER), request);
    }

    @PostMapping("/{id}")
    public Map<String, String> edit(Principal principal, @PathVariable String id, @RequestBody CorrectionEdit request) throws Exception {
        service.edit(access.require(principal, Role.READER), id, request);
        return Map.of("message", "Правку змінено.");
    }

    @DeleteMapping("/{id}")
    public Map<String, String> withdraw(Principal principal, @PathVariable String id) throws Exception {
        service.withdraw(access.require(principal, Role.READER), id);
        return Map.of("message", "Правку відкликано.");
    }

    @PostMapping("/batches/{batch}/review")
    public Map<String, String> reviewBatch(Principal principal, @PathVariable String batch, @RequestBody ReviewRequest request) throws Exception {
        service.reviewBatch(access.require(principal, Role.READER), batch, request);
        return Map.of("message", "Рішення щодо пакета збережено.");
    }

    @PostMapping("/{id}/review")
    public Map<String, String> review(Principal principal, @PathVariable String id, @RequestBody ReviewRequest request) throws Exception {
        service.review(access.require(principal, Role.READER), id, request);
        return Map.of("message", "Рішення збережено.");
    }
}
