package panrid.space.novelka.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.repository.GlossaryRepository;
import panrid.space.novelka.core.service.glossary.Dictionary;
import panrid.space.novelka.core.support.Hashes;
import panrid.space.novelka.core.support.Json;

import java.util.*;

/** Groups historical proposals without deleting records or changing canonical facts. */
public final class GlossaryProposalService {
    private final JdbcSession jdbc;

    public GlossaryProposalService(JdbcSession jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(String novel) throws Exception {
        var repository = new GlossaryRepository(jdbc);
        var glossary = repository.glossary(novel);
        var dismissed = new HashSet<String>();
        jdbc.rows("SELECT fingerprint FROM glossary_proposal_dismissals WHERE novel_id=?", novel)
                .forEach(row -> dismissed.add(row.get("fingerprint").toString()));
        var canonical = new HashSet<String>();
        glossary.entries().forEach(entry -> canonical.add(fingerprint(Json.M.valueToTree(entry))));
        var groups = new LinkedHashMap<String, Map<String, Object>>();
        for (var row : repository.proposals(novel)) {
            JsonNode proposal = (JsonNode) row.get("proposal");
            String fingerprint = fingerprint(proposal);
            var group = groups.get(fingerprint);
            if (group != null) {
                group.put("occurrences", (Integer) group.get("occurrences") + 1);
                continue;
            }
            group = new LinkedHashMap<>(row);
            group.put("occurrences", 1);
            group.put("status", dismissed.contains(fingerprint) ? "dismissed" : canonical.contains(fingerprint) ? "in_dictionary" : "pending");
            try {
                Entry entry = Json.decode(proposal.toString(), Entry.class);
                Dictionary.validate(entry);
                for (var existing : glossary.entries()) {
                    if (Dictionary.sameEntity(existing, entry)) {
                        group.put("canonicalKey", existing.key());
                        break;
                    }
                }
            } catch (IllegalArgumentException ignored) { /* Invalid proposals remain reviewable. */ }
            groups.put(fingerprint, group);
        }
        return List.copyOf(groups.values());
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
