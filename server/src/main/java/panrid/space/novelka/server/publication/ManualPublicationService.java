package panrid.space.novelka.server.publication;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.integration.source.text.PlainText;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.model.Segment;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.repository.ChapterRepository;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.core.repository.ReaderRepository;
import panrid.space.novelka.core.support.Hashes;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.NovelAccessRepository;
import panrid.space.novelka.server.repository.CorrectionRepository;
import panrid.space.novelka.server.repository.ManualDraftRepository;
import panrid.space.novelka.server.repository.TagRepository;
import panrid.space.novelka.server.tag.TagNames;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ready translations published without the AI pipeline. A manual novel uses the ordinary Novel, Chapter and Work models:
 * the published text is both the chapter source and its complete revision, so readers, corrections, exports and
 * notifications treat it like any other revision. Drafts stay private until an explicit publication.
 */
@Service
public final class ManualPublicationService {
    public static final String SOURCE = "manual:";
    private final ReaderDatabase database;

    public ManualPublicationService(ReaderDatabase database) { this.database = database; }

    public static boolean manual(Novel novel) { return novel.url() != null && novel.url().startsWith(SOURCE); }

    public String createNovel(Account actor, ManualNovelRequest request) throws Exception {
        String titleUk = text(request.titleUk(), 500, "українську назву");
        if (titleUk == null) throw new IllegalArgumentException("Вкажіть українську назву до 500 символів.");
        String title = text(request.title(), 500, "оригінальну назву");
        String authorUk = text(request.authorUk(), 500, "автора українською");
        String author = text(request.author(), 500, "автора в оригіналі");
        String description = text(request.descriptionUk(), 10000, "опис");
        var tags = TagNames.names(request.tags());
        String id = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try (var jdbc = database.open()) {
            jdbc.transaction(() -> {
                new NovelRepository(jdbc).save(new Novel(id, title == null ? titleUk : title, titleUk,
                        author != null ? author : authorUk == null ? "" : authorUk, authorUk, description, SOURCE + id, 0, false));
                new NovelAccessRepository(jdbc).owner(id, actor.id());
                if (!tags.isEmpty()) new TagRepository(jdbc).replace(id, tags);
                new AuditRepository(jdbc).add(actor.id(), "novel.create-manual", id, Map.of("titleUk", titleUk));
                return null;
            });
        }
        return id;
    }

    /** Drafts and published chapters of a manual novel for the workshop list. */
    public Map<String, Object> overview(String reference) throws Exception {
        try (var jdbc = database.open()) {
            String id = manualNovel(jdbc, reference);
            var published = jdbc.rows("""
                    SELECT DISTINCT ON (j.chapter) j.chapter,j.revision,c.data->>'title' AS title FROM jobs j
                    JOIN chapters c ON c.novel_id=j.novel_id AND c.number=j.chapter
                    WHERE j.novel_id=? AND j.state IN ('complete','needs-review') ORDER BY j.chapter,j.revision DESC
                    """, id);
            return Map.of("drafts", new ManualDraftRepository(jdbc).drafts(id), "published", published);
        }
    }

    /** Current draft and the published text, so an edit can start from either. */
    public Map<String, Object> chapter(String reference, int chapter) throws Exception {
        try (var jdbc = database.open()) {
            String id = manualNovel(jdbc, reference);
            var result = new LinkedHashMap<String, Object>();
            result.put("draft", new ManualDraftRepository(jdbc).draft(id, chapter));
            var work = new ReaderRepository(jdbc).chapter(id, chapter);
            if (work != null) {
                var blocks = work.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
                String title = blocks.stream().filter(block -> block.kind().equals("heading")).map(Block::text).findFirst().orElse("");
                String text = blocks.stream().filter(block -> !block.kind().equals("heading")).map(Block::text).collect(Collectors.joining("\n\n"));
                result.put("published", Map.of("title", title, "text", text, "revision", work.revision()));
            } else result.put("published", null);
            return result;
        }
    }

    public void saveDraft(Account actor, String reference, int chapter, ManualChapterRequest request) throws Exception {
        if (chapter < 1 || chapter > 100000) throw new IllegalArgumentException("Некоректний номер глави.");
        String title = text(request.title(), 300, "назву глави");
        if (title == null) throw new IllegalArgumentException("Вкажіть назву глави до 300 символів.");
        if (request.text() == null || request.text().isBlank() || request.text().length() > 300000)
            throw new IllegalArgumentException("Вкажіть текст глави до 300000 символів.");
        PlainText.parse(chapter, SOURCE, title + "\n" + request.text());
        try (var jdbc = database.open()) {
            String id = manualNovel(jdbc, reference);
            jdbc.transaction(() -> {
                new ManualDraftRepository(jdbc).save(id, chapter, title, request.text(), actor.id());
                new AuditRepository(jdbc).add(actor.id(), "chapter.manual.draft", id, Map.of("chapter", chapter));
                return null;
            });
        }
    }

    public void discardDraft(Account actor, String reference, int chapter) throws Exception {
        try (var jdbc = database.open()) {
            String id = manualNovel(jdbc, reference);
            if (!new ManualDraftRepository(jdbc).delete(id, chapter)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            new AuditRepository(jdbc).add(actor.id(), "chapter.manual.discard", id, Map.of("chapter", chapter));
        }
    }

    /** Turns the draft into the next complete revision and links it to the previous one in work_origins. */
    public int publish(Account actor, String reference, int chapter) throws Exception {
        try (var jdbc = database.open()) {
            String id = manualNovel(jdbc, reference);
            try (var lock = jdbc.lock(id)) {
                return jdbc.transaction(() -> {
                    var drafts = new ManualDraftRepository(jdbc);
                    var draft = drafts.draft(id, chapter);
                    if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Чернетки цієї глави немає.");
                    var parsed = PlainText.parse(chapter, SOURCE + id + "/" + chapter, draft.get("title") + "\n" + draft.get("text"));
                    new ChapterRepository(jdbc).save(id, parsed);
                    var jobs = new JobRepository(jdbc);
                    var previous = jobs.latest(id, chapter);
                    int revision = previous == null ? 1 : previous.revision() + 1;
                    var work = new Work(UUID.randomUUID().toString(), id, chapter, Hashes.hash(parsed.blocks()), revision,
                            List.of(new Segment(parsed.blocks(), List.of(), parsed.blocks(), "", "complete")), "complete", "");
                    jobs.save(work);
                    if (previous != null) new CorrectionRepository(jdbc).lineage(work.id(), previous.id());
                    var novels = new NovelRepository(jdbc);
                    var novel = novels.novel(id);
                    if (chapter > novel.chapterCount()) novels.save(new Novel(novel.id(), novel.title(), novel.titleUk(), novel.author(),
                            novel.authorUk(), novel.descriptionUk(), novel.url(), chapter, novel.shortStory()));
                    drafts.delete(id, chapter);
                    new AuditRepository(jdbc).add(actor.id(), "chapter.manual.publish", id, Map.of("chapter", chapter, "revision", revision));
                    return revision;
                });
            }
        }
    }

    private static String manualNovel(panrid.space.novelka.core.persistence.JdbcSession jdbc, String reference) throws Exception {
        var novels = new NovelRepository(jdbc);
        String id = novels.resolveNovel(reference);
        if (!manual(novels.novel(id)))
            throw new IllegalArgumentException("Главу вручну можна додати лише до новели, створеної вручну: оригінали імпортованих новел не перезаписуються.");
        return id;
    }

    private static String text(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maximum) throw new IllegalArgumentException("Вкажіть " + field + " до " + maximum + " символів.");
        return normalized;
    }
}
