package panrid.space.novelka.core.service.glossary;

import com.fasterxml.jackson.databind.JsonNode;

import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.AiCallRepository;
import panrid.space.novelka.core.repository.GlossaryRepository;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.NotificationEventRepository;
import panrid.space.novelka.core.support.Json;

/** Updates canonical facts and invalidates translations in the same transaction. */
public final class GlossaryService {
    private final JdbcSession jdbc;
    private final GlossaryRepository glossaries;
    private final JobRepository jobs;
    private final AiCallRepository calls;

    public GlossaryService(JdbcSession jdbc, GlossaryRepository glossaries, JobRepository jobs, AiCallRepository calls) {
        this.jdbc = jdbc;
        this.glossaries = glossaries;
        this.jobs = jobs;
        this.calls = calls;
    }

    public void update(String novel, Glossary glossary) throws Exception {
        jdbc.transaction(() -> {
            var old = glossaries.glossary(novel);
            glossaries.save(novel, glossary);
            var previousKeys = old.entries().stream().map(Entry::key).collect(java.util.stream.Collectors.toSet());
            int added = (int) glossary.entries().stream().filter(entry -> !previousKeys.contains(entry.key())).count();
            new NotificationEventRepository(jdbc).glossaryAdded(novel, glossary.revision(), added);
            var changed = old.entries().stream()
                    .filter(entry -> glossary.entries().stream().noneMatch(entry::equals))
                    .map(Entry::key).toList();
            if (!changed.isEmpty()) {
                for (Work work : jobs.glossaryConsumers(novel)) {
                    boolean depends = calls.contexts(work.id()).stream()
                            .map(row -> Json.read(row.get("context").toString()))
                            .anyMatch(context -> changed.stream().anyMatch(key -> containsEntry(context, key)));
                    if (depends) {
                        jobs.save(new Work(work.id(), work.novelId(), work.chapter(), work.sourceHash(),
                                work.revision(), work.segments(), "needs-review", work.summary()));
                    }
                }
            }
            return null;
        });
    }

    private static boolean containsEntry(JsonNode node, String key) {
        if (node.isObject() && node.path("key").asText().equals(key)) return true;
        if (node.isContainerNode()) {
            for (var child : node) if (containsEntry(child, key)) return true;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.startsWith("{") || text.startsWith("[")) {
                try {
                    return containsEntry(Json.read(text), key);
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }
}
