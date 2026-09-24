package panrid.space.novelka.server.correction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Segment;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.core.repository.ReaderRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.CorrectionRepository;
import panrid.space.novelka.server.novel.NovelAccessService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Reader corrections. A correction is a private draft until its author submits the drafts of a novel as one batch.
 * Reviewing applies every approved correction of a chapter in one new revision, so a batch does not create a revision
 * per paragraph. Replace corrections change every occurrence of a phrase in one chapter or in all published chapters.
 */
@Service
public final class CorrectionService {
    private final ReaderDatabase database;
    private final NovelAccessService access;

    public CorrectionService(ReaderDatabase database, NovelAccessService access) {
        this.database = database; this.access = access;
    }

    /** Saves a paragraph draft; a second proposal for the same paragraph updates the author's open correction. */
    public String propose(Account author, CorrectionRequest request) throws Exception {
        if (request.replacement() == null || request.replacement().isBlank() || request.replacement().length() > 20000
                || request.original() == null || request.reason() == null || request.reason().length() > 2000
                || request.original().equals(request.replacement())) throw new IllegalArgumentException("Перевірте текст правки.");
        try (var jdbc = database.open(); var lock = jdbc.lock(request.novelId())) {
            return jdbc.transaction(() -> {
                var work = new ReaderRepository(jdbc).chapter(request.novelId(), request.chapter());
                if (work == null || !work.id().equals(request.baseJobId())) throw conflict();
                var blocks = revised(work);
                if (request.blockIndex() < 0 || request.blockIndex() >= blocks.size()) throw conflict();
                var block = blocks.get(request.blockIndex());
                if (block.kind().equals("separator") || !block.text().equals(request.original())) throw conflict();
                var corrections = new CorrectionRepository(jdbc);
                var open = corrections.openBlock(author.id(), request.novelId(), request.chapter(), block.id());
                if (open != null) {
                    corrections.update((String) open.get("id"), request.replacement(), request.reason());
                    new AuditRepository(jdbc).add(author.id(), "correction.update", (String) open.get("id"), Map.of());
                    return (String) open.get("id");
                }
                String id = corrections.create(author.id(), request, block.id());
                new AuditRepository(jdbc).add(author.id(), "correction.propose", id, Map.of());
                return id;
            });
        }
    }

    public String proposeReplace(Account author, ReplaceRequest request) throws Exception {
        String find = request.find() == null ? "" : request.find();
        String replacement = request.replacement() == null ? "" : request.replacement();
        if (find.isBlank() || find.length() > 200 || replacement.length() > 200 || find.equals(replacement)
                || request.reason() == null || request.reason().length() > 2000 || !List.of("chapter", "novel").contains(request.scope()))
            throw new IllegalArgumentException("Вкажіть, що замінити (до 200 символів), на що, і обсяг: глава або новела.");
        try (var jdbc = database.open(); var lock = jdbc.lock(request.novelId())) {
            return jdbc.transaction(() -> {
                var work = new ReaderRepository(jdbc).chapter(request.novelId(), request.chapter());
                if (work == null || !work.id().equals(request.baseJobId())) throw conflict();
                int matches = occurrences(jdbc, request.novelId(), request.chapter(), find, request.scope()).values().stream().mapToInt(Integer::intValue).sum();
                if (matches == 0) throw new IllegalArgumentException("Такого тексту немає в опублікованому перекладі.");
                String id = new CorrectionRepository(jdbc).createReplace(author.id(), request.novelId(), request.chapter(), work.id(),
                        find, replacement, request.reason(), request.scope());
                new AuditRepository(jdbc).add(author.id(), "correction.propose-replace", id, Map.of("scope", request.scope(), "matches", matches));
                return id;
            });
        }
    }

    /** How many times the phrase occurs, per published chapter, so the author sees the reach before proposing. */
    public Map<String, Object> preview(String reference, int chapter, String find, String scope) throws Exception {
        if (find == null || find.isBlank() || find.length() > 200 || !List.of("chapter", "novel").contains(scope))
            throw new IllegalArgumentException("Вкажіть текст до 200 символів і обсяг.");
        try (var jdbc = database.open()) {
            String novel = new NovelRepository(jdbc).resolveNovel(reference);
            var counts = occurrences(jdbc, novel, chapter, find, scope);
            var chapters = counts.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .map(entry -> Map.of("chapter", entry.getKey(), "count", entry.getValue())).toList();
            return Map.of("total", counts.values().stream().mapToInt(Integer::intValue).sum(), "chapters", chapters);
        }
    }

    public void edit(Account author, String id, CorrectionEdit request) throws Exception {
        if (request.replacement() == null || request.reason() == null || request.reason().length() > 2000)
            throw new IllegalArgumentException("Перевірте текст правки.");
        try (var jdbc = database.open()) {
            var corrections = new CorrectionRepository(jdbc);
            var row = own(corrections, author, id);
            int limit = "replace".equals(row.get("kind")) ? 200 : 20000;
            if (request.replacement().length() > limit || request.replacement().equals(row.get("original"))
                    || "block".equals(row.get("kind")) && request.replacement().isBlank())
                throw new IllegalArgumentException("Перевірте текст правки.");
            corrections.update(id, request.replacement(), request.reason());
            new AuditRepository(jdbc).add(author.id(), "correction.update", id, Map.of());
        }
    }

    public void withdraw(Account author, String id) throws Exception {
        try (var jdbc = database.open()) {
            var corrections = new CorrectionRepository(jdbc);
            own(corrections, author, id);
            corrections.delete(id);
            new AuditRepository(jdbc).add(author.id(), "correction.withdraw", id, Map.of());
        }
    }

    /** Sends the author's drafts to reviewers as one batch and returns how many were submitted. */
    public Map<String, Object> submit(Account author, SubmitRequest request) throws Exception {
        try (var jdbc = database.open()) {
            String novel = new NovelRepository(jdbc).resolveNovel(request.novelId());
            String batch = UUID.randomUUID().toString();
            int count = jdbc.transaction(() -> new CorrectionRepository(jdbc).submit(author.id(), novel, request.chapter(), batch));
            if (count == 0) throw new IllegalArgumentException("Немає неподаних правок.");
            new AuditRepository(jdbc).add(author.id(), "correction.submit", batch, Map.of("count", count));
            return Map.of("batchId", batch, "count", count);
        }
    }

    public void review(Account reviewer, String id, ReviewRequest request) throws Exception {
        try (var jdbc = database.open()) {
            var existing = new CorrectionRepository(jdbc).get(id, false);
            if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            decide(jdbc, reviewer, (String) existing.get("novel_id"), request, repository -> List.of(repository.get(id, true)));
        }
    }

    /** Approves or rejects the whole batch; approval makes one revision per changed chapter. */
    public void reviewBatch(Account reviewer, String batch, ReviewRequest request) throws Exception {
        try (var jdbc = database.open()) {
            var rows = new CorrectionRepository(jdbc).batch(batch, false);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            decide(jdbc, reviewer, (String) rows.getFirst().get("novel_id"), request, repository -> repository.batch(batch, true));
        }
    }

    private void decide(JdbcSession jdbc, Account reviewer, String novel, ReviewRequest request, CorrectionSelection selection) throws Exception {
        if (request.note() == null || request.note().length() > 2000) throw new IllegalArgumentException("Завеликий коментар.");
        if (!access.canReview(jdbc, reviewer, novel)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try (var lock = jdbc.lock(novel)) {
            jdbc.transaction(() -> {
                var repository = new CorrectionRepository(jdbc);
                var rows = selection.rows(repository);
                for (var row : rows) {
                    if (!"pending".equals(row.get("state"))) throw conflict();
                    if (!access.mayDecide(jdbc, reviewer, novel, (String) row.get("author_id")))
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Власну правку має перевірити інший редактор.");
                }
                var published = request.approve() ? apply(jdbc, novel, rows) : Map.<Integer, String>of();
                for (var row : rows) {
                    String id = (String) row.get("id");
                    int chapter = ((Number) row.get("chapter")).intValue();
                    String job = published.getOrDefault(chapter, published.values().stream().findFirst().orElse(null));
                    repository.review(id, reviewer.id(), request.approve() ? "approved" : "rejected", request.note(), job);
                    new AuditRepository(jdbc).add(reviewer.id(), "correction." + (request.approve() ? "approve" : "reject"), id, Map.of("note", request.note()));
                }
                return null;
            });
        }
    }

    /** Applies paragraph corrections first, then replacements, and saves one new revision per changed chapter. */
    private Map<Integer, String> apply(JdbcSession jdbc, String novel, List<Map<String, Object>> rows) throws Exception {
        var reader = new ReaderRepository(jdbc);
        var jobs = new JobRepository(jdbc);
        var chapters = new TreeSet<Integer>();
        boolean novelWide = false;
        for (var row : rows) {
            if ("novel".equals(row.get("scope")) && "replace".equals(row.get("kind"))) novelWide = true;
            else chapters.add(((Number) row.get("chapter")).intValue());
        }
        if (novelWide) chapters.addAll(publishedChapters(jdbc, novel));
        var published = new LinkedHashMap<Integer, String>();
        var replaced = new HashMap<String, Integer>();
        for (int chapter : chapters) {
            var current = reader.chapter(novel, chapter);
            if (current == null) {
                if (rows.stream().anyMatch(row -> "block".equals(row.get("kind")) && ((Number) row.get("chapter")).intValue() == chapter)) throw conflict();
                continue;
            }
            var blocks = new ArrayList<>(revised(current));
            boolean changed = false;
            for (var row : rows) {
                if (!"block".equals(row.get("kind")) || ((Number) row.get("chapter")).intValue() != chapter) continue;
                var base = jobs.job((String) row.get("base_job_id"));
                int target = ((Number) row.get("block_index")).intValue();
                if (!current.sourceHash().equals(base.sourceHash()) || target >= blocks.size()
                        || !blocks.get(target).id().equals(row.get("block_id")) || !blocks.get(target).text().equals(row.get("original")))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Абзац глави " + chapter + " уже змінився після подання правки. Відхиліть її або перевірте окремо.");
                var old = blocks.get(target);
                blocks.set(target, new Block(old.id(), old.kind(), (String) row.get("replacement")));
                changed = true;
            }
            for (var row : rows) {
                if (!"replace".equals(row.get("kind"))) continue;
                if ("chapter".equals(row.get("scope")) && ((Number) row.get("chapter")).intValue() != chapter) continue;
                String find = (String) row.get("original"), replacement = (String) row.get("replacement");
                for (int index = 0; index < blocks.size(); index++) {
                    var block = blocks.get(index);
                    int count = count(block.text(), find);
                    if (count == 0) continue;
                    blocks.set(index, new Block(block.id(), block.kind(), block.text().replace(find, replacement)));
                    replaced.merge((String) row.get("id"), count, Integer::sum);
                    changed = true;
                }
            }
            if (!changed) continue;
            var segments = new ArrayList<Segment>();
            int offset = 0;
            for (var segment : current.segments()) {
                int size = segment.revised().size();
                segments.add(new Segment(segment.source(), segment.draft(), new ArrayList<>(blocks.subList(offset, offset + size)),
                        segment.context(), segment.state()));
                offset += size;
            }
            String id = UUID.randomUUID().toString();
            jobs.save(new Work(id, current.novelId(), current.chapter(), current.sourceHash(), current.revision() + 1, segments, "complete", current.summary()));
            new CorrectionRepository(jdbc).lineage(id, current.id());
            published.put(chapter, id);
        }
        for (var row : rows)
            if ("replace".equals(row.get("kind")) && !replaced.containsKey((String) row.get("id")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Текст «" + row.get("original") + "» уже не знайдено. Відхиліть цю заміну.");
        return published;
    }

    private static Map<Integer, Integer> occurrences(JdbcSession jdbc, String novel, int chapter, String find, String scope) throws Exception {
        var reader = new ReaderRepository(jdbc);
        var counts = new LinkedHashMap<Integer, Integer>();
        for (int number : "novel".equals(scope) ? publishedChapters(jdbc, novel) : List.of(chapter)) {
            var work = reader.chapter(novel, number);
            if (work != null) counts.put(number, revised(work).stream().mapToInt(block -> count(block.text(), find)).sum());
        }
        return counts;
    }

    private static List<Integer> publishedChapters(JdbcSession jdbc, String novel) throws Exception {
        return jdbc.rows("""
                SELECT DISTINCT j.chapter FROM jobs j JOIN chapters c ON c.novel_id=j.novel_id AND c.number=j.chapter
                WHERE j.novel_id=? AND j.state IN ('complete','needs-review') AND j.data->>'sourceHash'=c.source_hash ORDER BY j.chapter
                """, novel).stream().map(row -> ((Number) row.get("chapter")).intValue()).toList();
    }

    private static int count(String text, String find) {
        int count = 0;
        for (int index = text.indexOf(find); index >= 0; index = text.indexOf(find, index + find.length())) count++;
        return count;
    }

    private static List<Block> revised(Work work) {
        return work.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
    }

    private static Map<String, Object> own(CorrectionRepository corrections, Account author, String id) throws Exception {
        var row = corrections.get(id, false);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!author.id().equals(row.get("author_id"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Змінювати можна лише власну правку.");
        if (!List.of("draft", "pending").contains(row.get("state")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Правку вже перевірено, її не можна змінити.");
        return row;
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Текст або стан правки змінився. Оновіть сторінку.");
    }
}
