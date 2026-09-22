package panrid.space.novelka.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.export.BookExporter;
import panrid.space.novelka.core.integration.source.text.PlainText;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.service.glossary.Dictionary;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.dto.GlossaryUpdate;
import panrid.space.novelka.server.dto.MetadataRequest;
import panrid.space.novelka.server.dto.TextImportRequest;
import panrid.space.novelka.server.repository.AuditRepository;

import java.nio.file.Files;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manage")
public final class ManagementController {
    private final ReaderDatabase database;
    private final AccessService access;

    public ManagementController(ReaderDatabase database, AccessService access) { this.database = database; this.access = access; }

    @GetMapping("/{novel}")
    public Object detail(Principal principal, @PathVariable String novel) throws Exception {
        access.require(principal, Role.ADMIN);
        try (var db = database.openDatabase()) {
            String id = db.novels().resolveNovel(novel);
            return Json.M.convertValue(Map.of("novel", db.novels().novel(id), "aliases", db.novels().aliases(id),
                    "chapters", db.chapters().list(id), "jobs", db.jobs().status(id),
                    "glossary", db.glossaries().glossary(id), "proposals", db.glossaries().proposals(id)), Object.class);
        }
    }

    @GetMapping("/costs")
    public Object costs(Principal principal, @RequestParam(required = false) String novel,
            @RequestParam(defaultValue = "false") boolean details) throws Exception {
        access.require(principal, Role.ADMIN);
        try (var db = database.openDatabase()) {
            return Json.M.convertValue(db.calls().costs(novel == null || novel.isBlank() ? null : db.novels().resolveNovel(novel), details), Object.class);
        }
    }

    @PostMapping("/{novel}/title")
    public Map<String, String> title(Principal principal, @PathVariable String novel, @RequestBody MetadataRequest request) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        if (request.titleUk() == null || request.titleUk().isBlank() || request.titleUk().length() > 500)
            throw new IllegalArgumentException("Вкажіть українську назву до 500 символів.");
        return updateMetadata(actor, novel, request, true);
    }

    @PostMapping("/{novel}/metadata")
    public Map<String, String> metadata(Principal principal, @PathVariable String novel, @RequestBody MetadataRequest request) throws Exception {
        return updateMetadata(access.require(principal, Role.ADMIN), novel, request, false);
    }

    private Map<String, String> updateMetadata(Account actor, String novel, MetadataRequest request, boolean preserveMissing) throws Exception {
        try (var jdbc = database.open()) {
            var repository = new panrid.space.novelka.core.repository.NovelRepository(jdbc);
            String id = repository.resolveNovel(novel);
            try (var lock = jdbc.lock(id)) { jdbc.transaction(() -> {
                var old = repository.novel(id);
                String titleUk = localized(request.titleUk(), 500, "українську назву");
                if (preserveMissing && titleUk == null) titleUk = old.titleUk();
                String authorUk = preserveMissing ? old.authorUk() : localized(request.authorUk(), 500, "українського автора");
                String descriptionUk = preserveMissing ? old.descriptionUk()
                        : localized(request.descriptionUk(), 10000, "український опис");
                repository.save(new Novel(id, old.title(), titleUk, old.author(), authorUk, descriptionUk,
                        old.url(), old.chapterCount(), old.shortStory()));
                new AuditRepository(jdbc).add(actor.id(), "novel.metadata", id, request);
                return null;
            }); }
        }
        return Map.of("message", "Українські дані новели збережено.");
    }

    private String localized(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maximum) throw new IllegalArgumentException("Вкажіть " + field + " до " + maximum + " символів.");
        return normalized;
    }

    @PostMapping("/{novel}/aliases")
    public Map<String, String> alias(Principal principal, @PathVariable String novel,
            @RequestBody Map<String, String> request) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        try (var jdbc = database.open()) {
            var repository = new panrid.space.novelka.core.repository.NovelRepository(jdbc);
            String id = repository.resolveNovel(novel);
            jdbc.transaction(() -> {
                repository.saveAlias(id, request.get("alias"));
                new AuditRepository(jdbc).add(actor.id(), "novel.alias.add", id, request);
                return null;
            });
        }
        return Map.of("message", "Аліас додано.");
    }

    @DeleteMapping("/{novel}/aliases/{alias}")
    public Map<String, String> removeAlias(Principal principal, @PathVariable String novel, @PathVariable String alias) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        try (var jdbc = database.open()) {
            var repository = new panrid.space.novelka.core.repository.NovelRepository(jdbc);
            jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728616)");
                String id = repository.resolveNovel(novel);
                if (!repository.aliases(id).stream().anyMatch(row -> row.get("alias").equals(alias)))
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                repository.removeAlias(alias);
                new AuditRepository(jdbc).add(actor.id(), "novel.alias.remove", id, Map.of("alias", alias));
                return null;
            });
        }
        return Map.of("message", "Аліас видалено.");
    }

    @PostMapping("/{novel}/text")
    public Map<String, String> text(Principal principal, @PathVariable String novel, @RequestBody TextImportRequest request) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        if (request.text() == null || request.text().isBlank() || request.text().length() > 300000) throw new IllegalArgumentException("Вкажіть текст до 300000 символів.");
        try (var jdbc = database.open()) {
            var novels = new panrid.space.novelka.core.repository.NovelRepository(jdbc);
            String id = novels.resolveNovel(novel);
            try (var lock = jdbc.lock(id)) {
                if (request.chapter() < 1 || request.chapter() > novels.novel(id).chapterCount())
                    throw new IllegalArgumentException("Некоректний номер глави.");
                jdbc.transaction(() -> {
                    new panrid.space.novelka.core.repository.ChapterRepository(jdbc).save(id,
                            PlainText.parse(request.chapter(), "manual:" + id + "/" + request.chapter(), request.text()));
                    new AuditRepository(jdbc).add(actor.id(), "chapter.import-text", id, Map.of("chapter", request.chapter()));
                    return null;
                });
            }
        }
        return Map.of("message", "Оригінал збережено.");
    }

    @PostMapping("/{novel}/glossary")
    public Map<String, String> glossary(Principal principal, @PathVariable String novel, @RequestBody GlossaryUpdate request) throws Exception {
        var actor = access.require(principal, Role.ADMIN);
        if (request.entries() == null || request.entries().size() > 10000) throw new IllegalArgumentException("Некоректний словник.");
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            var glossaries = new panrid.space.novelka.core.repository.GlossaryRepository(jdbc);
            var service = new panrid.space.novelka.core.service.glossary.GlossaryService(jdbc, glossaries,
                    new panrid.space.novelka.core.repository.JobRepository(jdbc), new panrid.space.novelka.core.repository.AiCallRepository(jdbc));
            try (var lock = jdbc.lock(id)) { jdbc.transaction(() -> {
                var old = glossaries.glossary(id);
                if (old.revision() != request.revision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Словник уже змінено.");
                var entries = new LinkedHashMap<String, Entry>();
                old.entries().forEach(entry -> entries.put(entry.key(), entry));
                for (var entry : request.entries()) {
                    Dictionary.validate(entry);
                    entries.put(entry.key(), new Entry(entry.key(), entry.kind(), entry.japanese(), entry.reading(),
                            entry.ukrainian(), entry.aliases(), entry.gender(), entry.facts(), entry.certainty(), entry.sourceChapter(), true));
                }
                service.update(id, new Glossary(old.revision() + 1, List.copyOf(entries.values())));
                new AuditRepository(jdbc).add(actor.id(), "glossary.update", id,
                        Map.of("revision", old.revision() + 1, "keys", request.entries().stream().map(Entry::key).toList()));
                return null;
            }); }
        }
        return Map.of("message", "Словник оновлено. Залежні переклади позначено на перевірку.");
    }

    @GetMapping("/{novel}/export")
    public ResponseEntity<byte[]> export(Principal principal, @PathVariable String novel,
            @RequestParam(defaultValue = "epub") String format) throws Exception {
        access.require(principal, Role.ADMIN);
        if (!List.of("html", "epub").contains(format)) throw new IllegalArgumentException("Оберіть HTML або EPUB.");
        var file = Files.createTempFile("novelka-export-", "." + format);
        try (var db = database.openDatabase()) {
            String id = db.novels().resolveNovel(novel);
            var jobs = db.jobs().completed(id);
            if (jobs.isEmpty()) throw new IllegalArgumentException("Немає готових глав для експорту.");
            new BookExporter().export(db.novels().novel(id), jobs, file, format);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=novelka." + format)
                    .header("Content-Type", format.equals("epub") ? "application/epub+zip" : "text/html;charset=UTF-8")
                    .body(Files.readAllBytes(file));
        } finally { Files.deleteIfExists(file); }
    }
}
