package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.dto.NovelCard;
import panrid.space.novelka.server.dto.NovelDetail;
import panrid.space.novelka.server.dto.ReaderChapter;
import panrid.space.novelka.server.service.ReaderService;

import java.util.List;

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
    public List<NovelCard> catalog() throws Exception {
        return service.catalog();
    }

    @GetMapping("/{novel}")
    public NovelDetail novel(@PathVariable("novel") String novel) throws Exception {
        return service.novel(novel);
    }

    @GetMapping("/{novel}/chapters/{chapter}")
    public ReaderChapter chapter(@PathVariable("novel") String novel,
            @PathVariable("chapter") int chapter, java.security.Principal principal) throws Exception {
        var account = access.current(principal);
        return service.chapter(novel, chapter, account == null ? null : account.id());
    }
}
