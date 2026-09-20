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

    public ReaderController(ReaderService service) {
        this.service = service;
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
            @PathVariable("chapter") int chapter) throws Exception {
        return service.chapter(novel, chapter);
    }
}
