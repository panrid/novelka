package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.novel.NovelAccessService;
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
    private final NovelAccessService novels;
    private final ReaderDatabase database;

    public ManualPublicationController(AccessService access, ManualPublicationService service, NovelAccessService novels, ReaderDatabase database) {
        this.access = access;
        this.service = service;
        this.novels = novels;
        this.database = database;
    }

    /** The translator of the novel or an administrator; unknown novels are 404. */
    private Account manage(Principal principal, String novel) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) { novels.manageable(jdbc, account, novel); }
        return account;
    }

    @PostMapping("/novels")
    public Map<String, String> create(Principal principal, @RequestBody ManualNovelRequest request) throws Exception {
        return Map.of("id", service.createNovel(access.require(principal, Role.READER), request));
    }

    @GetMapping("/{novel}/manual")
    public Map<String, Object> overview(Principal principal, @PathVariable String novel) throws Exception {
        manage(principal, novel);
        return service.overview(novel);
    }

    @GetMapping("/{novel}/manual/{chapter}")
    public Map<String, Object> chapter(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        manage(principal, novel);
        return service.chapter(novel, chapter);
    }

    @PostMapping("/{novel}/manual/{chapter}")
    public Map<String, String> draft(Principal principal, @PathVariable String novel, @PathVariable int chapter,
            @RequestBody ManualChapterRequest request) throws Exception {
        service.saveDraft(manage(principal, novel), novel, chapter, request);
        return Map.of("message", "Чернетку збережено. Читачі її не бачать, доки ви не опублікуєте главу.");
    }

    @DeleteMapping("/{novel}/manual/{chapter}")
    public Map<String, String> discard(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        service.discardDraft(manage(principal, novel), novel, chapter);
        return Map.of("message", "Чернетку видалено.");
    }

    @PostMapping("/{novel}/manual/{chapter}/publish")
    public Map<String, Object> publish(Principal principal, @PathVariable String novel, @PathVariable int chapter) throws Exception {
        int revision = service.publish(manage(principal, novel), novel, chapter);
        return Map.of("message", "Главу опубліковано як ревізію " + revision + ".", "revision", revision);
    }
}
