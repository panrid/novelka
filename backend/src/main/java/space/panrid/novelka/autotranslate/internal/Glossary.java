package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.jooq.tables.records.GlossaryEntryRecord;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Names and terms of one edition. Analysis only adds what is missing; it never overwrites
 * an entry, so a name the owner corrected stays corrected. Only entries the text mentions
 * go into a prompt.
 */
@Component
class Glossary {

    static final Set<String> KINDS = Set.of("character", "place", "organization", "term", "other");
    static final Set<String> GENDERS = Set.of("male", "female", "unknown");
    private static final int PROMPT_LIMIT = 200;
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;

    Glossary(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    record Entry(long id, String japanese, String reading, String ukrainian, List<String> aliases, String kind,
            String gender, String note, Integer sourceChapter, boolean manual) {

        /** One line for a prompt. */
        String line() {
            StringBuilder out = new StringBuilder(japanese);
            if (reading != null && !reading.isBlank()) {
                out.append(" (").append(reading).append(')');
            }
            out.append(" → ").append(ukrainian).append(" [").append(kind);
            if (gender != null && !gender.equals("unknown")) {
                out.append(", ").append(gender);
            }
            out.append(']');
            if (note != null && !note.isBlank()) {
                out.append(" — ").append(note);
            }
            return out.toString();
        }
    }

    List<Entry> all(long editionId) {
        return db.selectFrom(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId))
                .orderBy(GLOSSARY_ENTRY.KIND, GLOSSARY_ENTRY.UKRAINIAN).fetch(this::entry);
    }

    /** Entries whose Japanese form or an alias appears in the text. */
    List<Entry> mentionedIn(long editionId, String text) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : all(editionId)) {
            boolean mentioned = text.contains(entry.japanese())
                    || entry.aliases().stream().anyMatch(alias -> !alias.isBlank() && text.contains(alias));
            if (mentioned && found.size() < PROMPT_LIMIT) {
                found.add(entry);
            }
        }
        return found;
    }

    /** New entries from analysis; one already known (by its Japanese form) is left alone. */
    int addFromAnalysis(long editionId, int chapter, List<Proposed> proposed) {
        int added = 0;
        for (Proposed entry : proposed) {
            String japanese = entry.japanese() == null ? "" : entry.japanese().strip();
            String ukrainian = entry.ukrainian() == null ? "" : entry.ukrainian().strip();
            if (japanese.isEmpty() || ukrainian.isEmpty() || japanese.length() > 100 || ukrainian.length() > 100) {
                continue;
            }
            added += db.insertInto(GLOSSARY_ENTRY)
                    .set(GLOSSARY_ENTRY.EDITION_ID, editionId)
                    .set(GLOSSARY_ENTRY.JAPANESE, japanese)
                    .set(GLOSSARY_ENTRY.READING, blankToNull(entry.reading()))
                    .set(GLOSSARY_ENTRY.UKRAINIAN, ukrainian)
                    .set(GLOSSARY_ENTRY.KIND, KINDS.contains(entry.kind()) ? entry.kind() : "other")
                    .set(GLOSSARY_ENTRY.GENDER, GENDERS.contains(entry.gender()) ? entry.gender() : "unknown")
                    .set(GLOSSARY_ENTRY.NOTE, limit(blankToNull(entry.note()), 300))
                    .set(GLOSSARY_ENTRY.SOURCE_CHAPTER, chapter)
                    .onConflictDoNothing()
                    .execute();
        }
        return added;
    }

    record Proposed(String japanese, String reading, String ukrainian, String kind, String gender, String note) {
    }

    /** The owner's correction: the Ukrainian form, kind, gender and note. */
    void update(long editionId, long entryId, String ukrainian, String kind, String gender, String note) {
        String name = ukrainian == null ? "" : ukrainian.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw UserFacingException.badRequest("Вкажіть, як писати українською (до 100 знаків).");
        }
        if (!KINDS.contains(kind) || (gender != null && !GENDERS.contains(gender))) {
            throw UserFacingException.badRequest("Невідомий вид запису.");
        }
        int changed = db.update(GLOSSARY_ENTRY)
                .set(GLOSSARY_ENTRY.UKRAINIAN, name)
                .set(GLOSSARY_ENTRY.KIND, kind)
                .set(GLOSSARY_ENTRY.GENDER, gender == null ? "unknown" : gender)
                .set(GLOSSARY_ENTRY.NOTE, limit(blankToNull(note), 300))
                .set(GLOSSARY_ENTRY.MANUAL, true)
                .set(GLOSSARY_ENTRY.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.EDITION_ID.eq(editionId))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такого запису в словнику немає.");
        }
    }

    /** «Усе перевірено»: entries from analysis count as checked, as if the owner had saved each. */
    void markAllChecked(long editionId) {
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.MANUAL, true)
                .where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId), GLOSSARY_ENTRY.MANUAL.isFalse()).execute();
    }

    void delete(long editionId, long entryId) {
        db.deleteFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.EDITION_ID.eq(editionId)).execute();
    }

    private Entry entry(GlossaryEntryRecord record) {
        JSONB aliases = record.getAliases();
        return new Entry(record.getId(), record.getJapanese(), record.getReading(), record.getUkrainian(),
                aliases == null ? List.of() : json.readValue(aliases.data(), STRINGS), record.getKind(),
                record.getGender(), record.getNote(), record.getSourceChapter(), record.getManual());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
