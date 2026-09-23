package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.TagRepository;

import java.util.Map;

/** Public tag list for the catalog filter and suggestions in the workshop. */
@RestController
@RequestMapping("/api/tags")
public final class TagsController {
    private final ReaderDatabase database;

    public TagsController(ReaderDatabase database) { this.database = database; }

    @GetMapping
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q) throws Exception {
        if (q.length() > 40) throw new IllegalArgumentException("Пошук тегу: до 40 символів.");
        try (var jdbc = database.open()) {
            return Map.of("items", new TagRepository(jdbc).search(q, 50));
        }
    }
}
