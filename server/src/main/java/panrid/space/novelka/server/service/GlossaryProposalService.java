package panrid.space.novelka.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.GlossaryRepository;
import panrid.space.novelka.core.service.glossary.Dictionary;
import panrid.space.novelka.core.service.glossary.EntryIdentity;
import panrid.space.novelka.core.support.Hashes;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.*;

/** Groups historical proposals without deleting records or changing canonical facts. */
public final class GlossaryProposalService {
    private final JdbcSession jdbc;

    public GlossaryProposalService(JdbcSession jdbc) { this.jdbc = jdbc; }

    public ListPage<Map<String, Object>> page(String novel, ListQuery query) throws Exception {
        String base = """
                WITH normalized AS (
                    SELECT id,job_id,proposal,
                        jsonb_set(proposal - 'key' - 'sourceChapter' - 'manual','{aliases}',
                            COALESCE((SELECT jsonb_agg(to_jsonb(btrim(value)) ORDER BY btrim(value))
                                FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(proposal->'aliases')='array'
                                    THEN proposal->'aliases' ELSE '[]'::jsonb END) AS alias(value)), '[]'::jsonb),true) identity
                    FROM glossary_proposals WHERE novel_id=? AND (?='' OR proposal::text ILIKE ? ESCAPE '\\')
                ), grouped AS (
                    SELECT min(id) id,count(*) occurrences,(array_agg(job_id ORDER BY id))[1] job_id,
                        (array_agg(proposal ORDER BY id))[1] proposal FROM normalized GROUP BY identity
                )
                """;
        long total = ((Number) jdbc.rows(base + "SELECT count(*) total FROM grouped", novel, query.q(), query.pattern())
                .getFirst().get("total")).longValue();
        String order = query.order(Map.of("created", "id", "occurrences", "occurrences"), "created", "id");
        var rows = jdbc.rows(base + "SELECT id,job_id,proposal,occurrences FROM grouped" + order + " LIMIT ? OFFSET ?",
                novel, query.q(), query.pattern(), query.size(), query.offset());
        return ListPage.of(classify(novel, rows), query, total);
    }

    private List<Map<String, Object>> classify(String novel, List<Map<String, Object>> rows) throws Exception {
        var repository = new GlossaryRepository(jdbc);
        var glossary = repository.glossary(novel);
        var fingerprints = rows.stream().map(row -> fingerprint((JsonNode) row.get("proposal"))).distinct().toList();
        var dismissed = new HashSet<String>();
        if (!fingerprints.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(fingerprints.size(), "?"));
            var arguments = new java.util.ArrayList<Object>();
            arguments.add(novel);
            arguments.addAll(fingerprints);
            jdbc.rows("SELECT fingerprint FROM glossary_proposal_dismissals WHERE novel_id=? AND fingerprint IN (" + placeholders + ")",
                    arguments.toArray()).forEach(row -> dismissed.add(row.get("fingerprint").toString()));
        }
        var canonical = new HashSet<String>();
        glossary.entries().forEach(entry -> canonical.add(fingerprint(Json.M.valueToTree(entry))));
        var groups = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            JsonNode proposal = (JsonNode) row.get("proposal");
            String fingerprint = fingerprint(proposal);
            var group = new LinkedHashMap<String, Object>(row);
            group.put("occurrences", ((Number) row.getOrDefault("occurrences", 1)).intValue());
            group.put("status", dismissed.contains(fingerprint) ? "dismissed" : canonical.contains(fingerprint) ? "in_dictionary" : "pending");
            try {
                Entry entry = Json.decode(proposal.toString(), Entry.class);
                Dictionary.validate(entry);
                var exact = glossary.entries().stream().filter(existing -> Dictionary.sameEntity(existing, entry)).toList();
                var candidates = exact.isEmpty() ? glossary.entries().stream().filter(existing -> EntryIdentity.possible(existing, entry)).toList() : exact;
                group.put("candidates", candidates);
                group.put("kind", candidates.isEmpty() ? "new" : exact.size() == 1 ? "update" : "possible_duplicate");
                if (exact.size() == 1) {
                    var existing = exact.getFirst();
                    group.put("canonicalKey", existing.key());
                    var differences = EntryIdentity.differences(existing, entry);
                    group.put("differences", differences);
                    if (differences.isEmpty() && !dismissed.contains(fingerprint)) group.put("status", "in_dictionary");
                }
            } catch (IllegalArgumentException ignored) { /* Invalid proposals remain reviewable. */ }
            groups.add(group);
        }
        return List.copyOf(groups);
    }

    public void dismiss(String novel, long id) throws Exception {
        var rows = jdbc.rows("SELECT proposal FROM glossary_proposals WHERE novel_id=? AND id=?", novel, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пропозицію не знайдено.");
        jdbc.exec("INSERT INTO glossary_proposal_dismissals(novel_id,fingerprint) VALUES(?,?) ON CONFLICT DO NOTHING",
                novel, fingerprint((JsonNode) rows.getFirst().get("proposal")));
    }

    private static String fingerprint(JsonNode proposal) {
        JsonNode value = proposal.deepCopy();
        if (value instanceof ObjectNode object && object.has("japanese")) {
            object.remove(List.of("key", "sourceChapter", "manual"));
        }
        return Hashes.hash(Json.write(normalized(value)));
    }

    private static Object normalized(JsonNode value) {
        if (value.isObject()) {
            var fields = new TreeMap<String, Object>();
            value.fields().forEachRemaining(field -> {
                if (field.getKey().equals("aliases") && field.getValue().isArray()) {
                    var aliases = new TreeSet<String>();
                    field.getValue().forEach(alias -> aliases.add(alias.asText().strip()));
                    fields.put(field.getKey(), aliases);
                } else fields.put(field.getKey(), normalized(field.getValue()));
            });
            return fields;
        }
        if (value.isArray()) {
            var items = new ArrayList<Object>();
            value.forEach(item -> items.add(normalized(item)));
            return items;
        }
        return value.isTextual() ? value.asText().strip() : Json.M.convertValue(value, Object.class);
    }
}
