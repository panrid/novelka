package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.publication.ManualChapterRequest;
import panrid.space.novelka.server.publication.ManualNovelRequest;
import panrid.space.novelka.server.publication.ManualPublicationService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/manage")
public final class ManualPublicationController {
    private final AccessService access;
    private final ManualPublicationService service;

    public ManualPublicationController(AccessService access, ManualPublicationService service) {
        this.access = access;
        this.service = service;
    }

    @PostMapping("/novels")
    public Map<String, String> create(Principal principal, @RequestBody ManualNovelRequest request) throws Exception {
        return Map.of("id", service.createNovel(access.require(principal, Role.ADMIN), request));
    }

    @GetMapping("/{novel}/manual")
    public Map<String, Object> overview(Principal principal, @PathVariable String novel) throws Exception {
        access.require(principal, Role.ADMIN);
        return service.overview(novel);
    }

    @GetMapping("/{novel}/manual/{chapter}")
    public Map<String, Object> chapter(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        access.require(principal, Role.ADMIN);
        return service.chapter(novel, chapter);
    }

    @PostMapping("/{novel}/manual/{chapter}")
    public Map<String, String> draft(Principal principal, @PathVariable String novel, @PathVariable int chapter,
            @RequestBody ManualChapterRequest request) throws Exception {
        service.saveDraft(access.require(principal, Role.ADMIN), novel, chapter, request);
        return Map.of("message", "Чернетку збережено. Читачі її не бачать, доки ви не опублікуєте главу.");
    }

    @DeleteMapping("/{novel}/manual/{chapter}")
    public Map<String, String> discard(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        service.discardDraft(access.require(principal, Role.ADMIN), novel, chapter);
        return Map.of("message", "Чернетку видалено.");
    }

    @PostMapping("/{novel}/manual/{chapter}/publish")
    public Map<String, Object> publish(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        int revision = service.publish(access.require(principal, Role.ADMIN), novel, chapter);
        return Map.of("message", "Главу опубліковано як ревізію " + revision + ".", "revision", revision);
    }
}
