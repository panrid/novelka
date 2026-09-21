package panrid.space.novelka.server.correction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Segment;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.ReaderRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.CorrectionRepository;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public final class CorrectionService {
    private final ReaderDatabase database;

    public CorrectionService(ReaderDatabase database) { this.database = database; }

    public String propose(Account author, CorrectionRequest request) throws Exception {
        if (request.replacement() == null || request.replacement().isBlank() || request.replacement().length() > 20000
                || request.original() == null || request.reason() == null || request.reason().length() > 2000
                || request.original().equals(request.replacement())) throw new IllegalArgumentException("Перевірте текст правки.");
        try (var jdbc = database.open(); var lock = jdbc.lock(request.novelId())) {
            return jdbc.transaction(() -> {
                var work = new ReaderRepository(jdbc).chapter(request.novelId(), request.chapter());
                if (work == null || !work.id().equals(request.baseJobId())) throw conflict();
                var blocks = work.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
                if (request.blockIndex() < 0 || request.blockIndex() >= blocks.size()) throw conflict();
                var block = blocks.get(request.blockIndex());
                if (block.kind().equals("separator") || !block.text().equals(request.original())) throw conflict();
                var corrections = new CorrectionRepository(jdbc);
                if (corrections.pending(author.id(), work).stream()
                        .anyMatch(row -> ((Number) row.get("block_index")).intValue() == request.blockIndex()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ваша правка цього абзацу вже очікує перевірки.");
                String id = corrections.create(author.id(), request, block.id());
                new AuditRepository(jdbc).add(author.id(), "correction.propose", id, Map.of());
                return id;
            });
        }
    }

    public void review(Account reviewer, String id, ReviewRequest request) throws Exception {
        if (request.note() == null || request.note().length() > 2000) throw new IllegalArgumentException("Завеликий коментар.");
        try (var jdbc = database.open()) {
            var repository = new CorrectionRepository(jdbc);
            var existing = repository.get(id, false);
            if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            try (var lock = jdbc.lock((String) existing.get("novel_id"))) {
                jdbc.transaction(() -> {
                    var row = repository.get(id, true);
                    if (!row.get("state").equals("pending")) throw conflict();
                    if (row.get("author_id").equals(reviewer.id()))
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Власну правку має перевірити інший редактор.");
                    String published = null;
                    if (request.approve()) {
                        var reader = new ReaderRepository(jdbc);
                        var jobs = new JobRepository(jdbc);
                        var current = reader.chapter((String) row.get("novel_id"), ((Number) row.get("chapter")).intValue());
                        var base = jobs.job((String) row.get("base_job_id"));
                        if (current == null || !current.sourceHash().equals(base.sourceHash())) throw conflict();
                        int target = ((Number) row.get("block_index")).intValue();
                        var blocks = current.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
                        if (target >= blocks.size() || !blocks.get(target).id().equals(row.get("block_id"))
                                || !blocks.get(target).text().equals(row.get("original"))) throw conflict();
                        var segments = new ArrayList<Segment>();
                        int offset = 0;
                        for (var segment : current.segments()) {
                            var revised = new ArrayList<>(segment.revised());
                            if (target >= offset && target < offset + revised.size()) {
                                var old = revised.get(target - offset);
                                revised.set(target - offset, new Block(old.id(), old.kind(), (String) row.get("replacement")));
                            }
                            offset += revised.size();
                            segments.add(new Segment(segment.source(), segment.draft(), revised, segment.context(), segment.state()));
                        }
                        published = UUID.randomUUID().toString();
                        jobs.save(new Work(published, current.novelId(), current.chapter(), current.sourceHash(),
                                current.revision() + 1, segments, "complete", current.summary()));
                        repository.lineage(published, current.id());
                    }
                    repository.review(id, reviewer.id(), request.approve() ? "approved" : "rejected", request.note(), published);
                    new AuditRepository(jdbc).add(reviewer.id(), "correction." + (request.approve() ? "approve" : "reject"),
                            id, Map.of("note", request.note()));
                    return null;
                });
            }
        }
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Текст або стан правки змінився. Оновіть сторінку.");
    }
}
