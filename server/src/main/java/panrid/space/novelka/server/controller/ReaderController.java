package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import panrid.space.novelka.server.list.ListQuery;
import panrid.space.novelka.server.dto.NovelDetail;
import panrid.space.novelka.server.dto.ReaderChapter;
import panrid.space.novelka.server.service.ReaderService;


@RestController
@RequestMapping("/api/novels")
public final class ReaderController {
    private final ReaderService service;
    private final panrid.space.novelka.server.account.AccessService access;

    public ReaderController(ReaderService service, panrid.space.novelka.server.account.AccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public Object catalog(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "title") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "false") boolean readyOnly,
            @RequestParam(required = false) java.util.List<String> tag) throws Exception {
        return service.catalog(new ListQuery(page, size, q, sort, direction), readyOnly, tag);
    }

    @GetMapping("/search")
    public Object search(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "title") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "false") boolean readyOnly,
            @RequestParam(required = false) java.util.List<String> tag) throws Exception {
        return service.catalog(new ListQuery(page, size, q, sort, direction), readyOnly, tag);
    }

    @GetMapping("/{novel}")
    public NovelDetail novel(@PathVariable("novel") String novel, @RequestParam(required = false) Integer resume,
            java.security.Principal principal) throws Exception {
        var account = access.current(principal);
        return service.novel(novel, resume, account == null ? null : account.id());
    }

    @GetMapping("/{novel}/contents")
    public Object contents(@PathVariable String novel, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "number") String sort, @RequestParam(defaultValue = "asc") String direction) throws Exception {
        return service.contents(novel, new ListQuery(page, size, q, sort, direction));
    }

    @GetMapping("/{novel}/chapters/{chapter}")
    public ReaderChapter chapter(@PathVariable("novel") String novel,
            @PathVariable("chapter") int chapter, java.security.Principal principal) throws Exception {
        var account = access.current(principal);
        return service.chapter(novel, chapter, account == null ? null : account.id());
    }
}
