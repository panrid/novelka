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
import panrid.space.novelka.server.dto.GlossaryMerge;
import panrid.space.novelka.server.dto.MetadataRequest;
import panrid.space.novelka.server.dto.TextImportRequest;
import panrid.space.novelka.server.novel.NovelAccessService;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.NovelAccessRepository;
import panrid.space.novelka.server.repository.TagRepository;
import panrid.space.novelka.server.tag.TagNames;
import panrid.space.novelka.server.tag.TagUpdate;
import panrid.space.novelka.server.repository.CostRepository;
import panrid.space.novelka.server.repository.JobListRepository;
import panrid.space.novelka.server.repository.GlossaryEntryRepository;
import panrid.space.novelka.server.list.ListQuery;
import panrid.space.novelka.server.service.GlossaryProposalService;

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
    private final NovelAccessService novels;

    public ManagementController(ReaderDatabase database, AccessService access, NovelAccessService novels) {
        this.database = database; this.access = access; this.novels = novels;
    }

    /** The translator of the novel or an administrator; unknown novels are 404. */
    private Account manage(Principal principal, String novel) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) { novels.manageable(jdbc, account, novel); }
        return account;
    }

    /** Novels the workshop may open: the account's own translations, or every novel for administrators. */
    @GetMapping("/novels")
    public Map<String, Object> manageable(Principal principal, @RequestParam(defaultValue = "") String q) throws Exception {
        var account = access.require(principal, Role.READER);
        if (q.length() > 200) throw new IllegalArgumentException("Пошук має містити до 200 символів.");
        try (var jdbc = database.open()) {
            return Map.of("items", new NovelAccessRepository(jdbc).manageable(account.role().includes(Role.ADMIN) ? null : account.id(), q.strip(), 25));
        }
    }

    @GetMapping("/{novel}")
    public Object detail(Principal principal, @PathVariable String novel) throws Exception {
        manage(principal, novel);
        try (var db = database.openDatabase(); var jdbc = database.open()) {
            String id = db.novels().resolveNovel(novel);
            return Json.M.convertValue(Map.of("novel", db.novels().novel(id), "aliases", db.novels().aliases(id),
                    "importedChapters", ((Number) jdbc.rows("SELECT count(*) total FROM chapters WHERE novel_id=?", id).getFirst().get("total")).longValue(),
                    "glossary", new Glossary(db.glossaries().glossary(id).revision(), List.of()), "proposals", List.of(),
                    "tags", new TagRepository(jdbc).forNovel(id),
                    // Novelka produced at least one translation: the UI suggests the machine translation tag, never sets it silently.
                    "aiTranslated", !jdbc.rows("SELECT 1 FROM jobs WHERE novel_id=? AND state IN ('complete','needs-review') LIMIT 1", id).isEmpty()),
                    Object.class);
        }
    }

    @GetMapping("/{novel}/glossary/entries")
    public Object glossaryEntries(Principal principal, @PathVariable String novel,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "japanese") String sort,
            @RequestParam(defaultValue = "asc") String direction, @RequestParam(defaultValue = "") String kind) throws Exception {
        manage(principal, novel);
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            return new GlossaryEntryRepository(jdbc).list(id, new ListQuery(page, size, q, sort, direction), kind);
        }
    }

    @GetMapping("/{novel}/proposals")
    public Object proposals(Principal principal, @PathVariable String novel,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "created") String sort,
            @RequestParam(defaultValue = "desc") String direction) throws Exception {
        manage(principal, novel);
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            return new GlossaryProposalService(jdbc).page(id, new ListQuery(page, size, q, sort, direction));
        }
    }

    @GetMapping("/{novel}/glossary/similar")
    public Object similarEntries(Principal principal, @PathVariable String novel, @RequestParam String japanese,
            @RequestParam String ukrainian, @RequestParam String kind) throws Exception {
        manage(principal, novel);
        if (japanese.length() > 1000 || ukrainian.length() > 1000 || kind.length() > 50)
            throw new IllegalArgumentException("Запит на пошук схожих записів завеликий.");
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            var probe = new Entry("", kind, japanese, "", ukrainian, List.of(), "unknown", "", "unknown", 1, false);
            return new panrid.space.novelka.core.repository.GlossaryRepository(jdbc).glossary(id).entries().stream()
                    .filter(entry -> panrid.space.novelka.core.service.glossary.EntryIdentity.possible(entry, probe)).limit(20).toList();
        }
    }

    @GetMapping("/{novel}/jobs")
    public Object jobs(Principal principal, @PathVariable String novel, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "updated") String sort, @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "") String state) throws Exception {
        manage(principal, novel);
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            return Json.M.convertValue(new JobListRepository(jdbc).list(id, new ListQuery(page, size, q, sort, direction), state), Object.class);
        }
    }

    @GetMapping("/costs")
    public Object costs(Principal principal, @RequestParam(required = false) String novel,
            @RequestParam(defaultValue = "false") boolean details, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String sort, @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "") String stage) throws Exception {
        var account = access.require(principal, Role.READER);
        try (var jdbc = database.open()) {
            String id = novel == null || novel.isBlank() ? null : novels.manageable(jdbc, account, novel);
            // Without a novel, translators see costs of their own novels; administrators see everything.
            String owner = account.role().includes(Role.ADMIN) ? null : account.id();
            return Json.M.convertValue(new CostRepository(jdbc).list(id, owner, details, new ListQuery(page, size, q, sort, direction), stage), Object.class);
        }
    }

    @PostMapping("/{novel}/proposals/{proposal}/dismiss")
    public Map<String, String> dismissProposal(Principal principal, @PathVariable String novel, @PathVariable long proposal) throws Exception {
        var actor = manage(principal, novel);
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            jdbc.transaction(() -> {
                new GlossaryProposalService(jdbc).dismiss(id, proposal);
                new AuditRepository(jdbc).add(actor.id(), "glossary.proposal.dismiss", id, Map.of("proposal", proposal));
                return null;
            });
        }
        return Map.of("message", "Пропозицію та її однакові повтори відхилено.");
    }

    @PostMapping("/{novel}/title")
    public Map<String, String> title(Principal principal, @PathVariable String novel, @RequestBody MetadataRequest request) throws Exception {
        var actor = manage(principal, novel);
        if (request.titleUk() == null || request.titleUk().isBlank() || request.titleUk().length() > 500)
            throw new IllegalArgumentException("Вкажіть українську назву до 500 символів.");
        return updateMetadata(actor, novel, request, true);
    }

    @PostMapping("/{novel}/metadata")
    public Map<String, String> metadata(Principal principal, @PathVariable String novel, @RequestBody MetadataRequest request) throws Exception {
        return updateMetadata(manage(principal, novel), novel, request, false);
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

    @PostMapping("/{novel}/tags")
    public Map<String, Object> tags(Principal principal, @PathVariable String novel, @RequestBody TagUpdate request) throws Exception {
        var actor = manage(principal, novel);
        var names = TagNames.names(request.tags());
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            return jdbc.transaction(() -> {
                var tags = new TagRepository(jdbc).replace(id, names);
                new AuditRepository(jdbc).add(actor.id(), "novel.tags", id, Map.of("tags", names));
                return Map.of("tags", tags);
            });
        }
    }

    @PostMapping("/{novel}/aliases")
    public Map<String, String> alias(Principal principal, @PathVariable String novel,
            @RequestBody Map<String, String> request) throws Exception {
        var actor = manage(principal, novel);
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
        var actor = manage(principal, novel);
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
        var actor = manage(principal, novel);
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
        var actor = manage(principal, novel);
        if (request.entries() == null || request.entries().size() > 10000) throw new IllegalArgumentException("Некоректний словник.");
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            var glossaries = new panrid.space.novelka.core.repository.GlossaryRepository(jdbc);
            var service = new panrid.space.novelka.core.service.glossary.GlossaryService(jdbc, glossaries,
                    new panrid.space.novelka.core.repository.JobRepository(jdbc), new panrid.space.novelka.core.repository.AiCallRepository(jdbc));
            jdbc.transaction(() -> {
                boolean available = (Boolean) jdbc.rows("SELECT pg_try_advisory_xact_lock(hashtext(?)) AS available", id).getFirst().get("available");
                if (!available) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Словник новели зараз зайнятий перекладом. Зупиніть завдання в черзі, дочекайтеся завершення поточного AI-запиту й повторіть збереження.");
                var old = glossaries.glossary(id);
                if (old.revision() != request.revision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Словник уже змінено.");
                var entries = new LinkedHashMap<String, Entry>();
                old.entries().forEach(entry -> entries.put(entry.key(), entry));
                for (var entry : request.entries()) {
                    Dictionary.validate(entry);
                    var duplicate = entries.values().stream().filter(existing -> !existing.key().equals(entry.key())
                            && panrid.space.novelka.core.service.glossary.EntryIdentity.possible(existing, entry)).findFirst();
                    if (duplicate.isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Схожий запис уже є в словнику: " + duplicate.get().ukrainian() + " (" + duplicate.get().japanese()
                                    + "). Відредагуйте його або об’єднайте записи.");
                    entries.put(entry.key(), new Entry(entry.key(), entry.kind(), entry.japanese(), entry.reading(),
                            entry.ukrainian(), entry.aliases(), entry.gender(), entry.facts(), entry.certainty(), entry.sourceChapter(), true));
                }
                service.update(id, new Glossary(old.revision() + 1, List.copyOf(entries.values())));
                new AuditRepository(jdbc).add(actor.id(), "glossary.update", id,
                        Map.of("revision", old.revision() + 1, "keys", request.entries().stream().map(Entry::key).toList()));
                return null;
            });
        }
        return Map.of("message", "Словник оновлено. Залежні переклади позначено на перевірку.");
    }

    @PostMapping("/{novel}/glossary/merge")
    public Map<String, String> mergeGlossary(Principal principal, @PathVariable String novel, @RequestBody GlossaryMerge request) throws Exception {
        var actor = manage(principal, novel);
        if (request.keepKey() == null || request.removeKey() == null || request.keepKey().equals(request.removeKey()))
            throw new IllegalArgumentException("Виберіть два різні записи для об’єднання.");
        try (var jdbc = database.open()) {
            String id = new panrid.space.novelka.core.repository.NovelRepository(jdbc).resolveNovel(novel);
            var glossaries = new panrid.space.novelka.core.repository.GlossaryRepository(jdbc);
            var service = new panrid.space.novelka.core.service.glossary.GlossaryService(jdbc, glossaries,
                    new panrid.space.novelka.core.repository.JobRepository(jdbc), new panrid.space.novelka.core.repository.AiCallRepository(jdbc));
            jdbc.transaction(() -> {
                boolean available = (Boolean) jdbc.rows("SELECT pg_try_advisory_xact_lock(hashtext(?)) AS available", id).getFirst().get("available");
                if (!available) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Словник новели зараз зайнятий перекладом. Зупиніть завдання й повторіть об’єднання.");
                var old = glossaries.glossary(id);
                if (old.revision() != request.revision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Словник уже змінено.");
                var keep = old.entries().stream().filter(entry -> entry.key().equals(request.keepKey())).findFirst().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Перший запис не знайдено."));
                var remove = old.entries().stream().filter(entry -> entry.key().equals(request.removeKey())).findFirst().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Другий запис не знайдено."));
                if (!panrid.space.novelka.core.service.glossary.EntryIdentity.possible(keep, remove))
                    throw new IllegalArgumentException("Ці записи не схожі на одну сутність.");
                var aliases = new java.util.LinkedHashSet<String>(keep.aliases());
                for (String name : java.util.List.of(remove.key(), remove.japanese(), remove.ukrainian(), remove.reading()))
                    if (!name.isBlank() && !name.equals(keep.key()) && !name.equals(keep.japanese()) && !name.equals(keep.ukrainian())) aliases.add(name);
                aliases.addAll(remove.aliases());
                aliases.removeIf(String::isBlank);
                String facts = keep.facts();
                if (!remove.facts().isBlank() && !facts.contains(remove.facts()))
                    facts = facts.isBlank() ? remove.facts() : facts + "\n" + remove.facts();
                var merged = new Entry(keep.key(), keep.kind(), keep.japanese(), keep.reading().isBlank() ? remove.reading() : keep.reading(),
                        keep.ukrainian().isBlank() ? remove.ukrainian() : keep.ukrainian(), java.util.List.copyOf(aliases),
                        keep.gender().equals("unknown") ? remove.gender() : keep.gender(), facts, keep.certainty(),
                        Math.min(keep.sourceChapter(), remove.sourceChapter()), true);
                var entries = new java.util.ArrayList<Entry>();
                for (var entry : old.entries()) {
                    if (entry.key().equals(keep.key())) entries.add(merged);
                    else if (!entry.key().equals(remove.key())) entries.add(entry);
                }
                service.update(id, new Glossary(old.revision() + 1, entries));
                new AuditRepository(jdbc).add(actor.id(), "glossary.merge", id,
                        Map.of("keepKey", keep.key(), "removeKey", remove.key(), "revision", old.revision() + 1));
                return null;
            });
        }
        return Map.of("message", "Записи об’єднано. Перевірте збережені поля й пов’язані переклади.");
    }

    @GetMapping("/{novel}/export")
    public ResponseEntity<byte[]> export(Principal principal, @PathVariable String novel,
            @RequestParam(defaultValue = "epub") String format) throws Exception {
        manage(principal, novel);
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
