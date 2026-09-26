package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_ANALYSIS;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            String gender, String note, Integer sourceChapter, boolean manual, String status) {

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

    /** Everything the translation may use: rejected entries never reach a prompt. */
    List<Entry> all(long editionId) {
        return db.selectFrom(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId), GLOSSARY_ENTRY.STATUS.ne("rejected"))
                .orderBy(GLOSSARY_ENTRY.KIND, GLOSSARY_ENTRY.UKRAINIAN).fetch(this::entry);
    }

    static final int PAGE = 50;
    static final Set<String> STATUSES = Set.of("new", "approved", "rejected");

    /** {@code labels}: what readers see for each chapter in {@code chapters} — its number, or its title when it has none. */
    record Page(List<Entry> items, int total, int page, boolean hasMore, List<Integer> chapters, Map<Integer, String> labels,
            Map<String, Integer> counts) {
    }

    /**
     * One page of the glossary for review.
     *
     * @param status  new, approved, rejected, or null for all
     * @param chapter the chapter whose analysis found the entry, or null for all
     * @param sort    «alpha» (by the Ukrainian form) or «chapter» (in the order the novel met them)
     */
    Page page(long editionId, String status, Integer chapter, String query, String sort, int page) {
        var where = GLOSSARY_ENTRY.EDITION_ID.eq(editionId)
                .and(status == null ? DSL.noCondition() : GLOSSARY_ENTRY.STATUS.eq(status))
                .and(chapter == null ? DSL.noCondition() : GLOSSARY_ENTRY.SOURCE_CHAPTER.eq(chapter))
                .and(query == null || query.isBlank() ? DSL.noCondition() : GLOSSARY_ENTRY.UKRAINIAN.containsIgnoreCase(query.strip()));
        int total = db.fetchCount(GLOSSARY_ENTRY, where);
        var order = "chapter".equals(sort)
                ? List.of(GLOSSARY_ENTRY.SOURCE_CHAPTER.asc().nullsFirst(), GLOSSARY_ENTRY.UKRAINIAN.asc())
                : List.of(DSL.lower(GLOSSARY_ENTRY.UKRAINIAN).asc(), GLOSSARY_ENTRY.ID.asc());
        int at = Math.max(1, page);
        List<Entry> items = db.selectFrom(GLOSSARY_ENTRY).where(where).orderBy(order).limit(PAGE).offset((at - 1) * PAGE).fetch(this::entry);
        List<Integer> chapters = db.selectDistinct(GLOSSARY_ENTRY.SOURCE_CHAPTER).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId), GLOSSARY_ENTRY.SOURCE_CHAPTER.isNotNull(), GLOSSARY_ENTRY.SOURCE_CHAPTER.gt(0))
                .orderBy(GLOSSARY_ENTRY.SOURCE_CHAPTER).fetch(GLOSSARY_ENTRY.SOURCE_CHAPTER);
        Map<String, Integer> counts = new java.util.HashMap<>(Map.of("new", 0, "approved", 0, "rejected", 0));
        db.select(GLOSSARY_ENTRY.STATUS, DSL.count()).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId))
                .groupBy(GLOSSARY_ENTRY.STATUS).forEach(r -> counts.put(r.value1(), r.value2()));
        Map<Integer, String> labels = new java.util.HashMap<>();
        db.select(CHAPTER_ANALYSIS.NUMBER, CHAPTER_ANALYSIS.LABEL, CHAPTER_ANALYSIS.TITLE).from(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.in(chapters))
                .forEach(r -> labels.put(r.value1(), r.value2() == null ? String.valueOf(r.value1()) : r.value2().isEmpty() ? r.value3() : r.value2()));
        return new Page(items, total, at, at * PAGE < total, chapters, labels, counts);
    }

    /** «Затвердити», «Відхилити» or «Повернути в нові» for the selected entries. */
    int setStatus(long editionId, List<Long> ids, String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw UserFacingException.badRequest("Невідома дія.");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.STATUS, status).set(GLOSSARY_ENTRY.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(GLOSSARY_ENTRY.EDITION_ID.eq(editionId), GLOSSARY_ENTRY.ID.in(ids)).execute();
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
        if (kind == null || !KINDS.contains(kind) || (gender != null && !GENDERS.contains(gender))) {
            throw UserFacingException.badRequest("Невідомий вид запису.");
        }
        int changed = db.update(GLOSSARY_ENTRY)
                .set(GLOSSARY_ENTRY.UKRAINIAN, name)
                .set(GLOSSARY_ENTRY.KIND, kind)
                .set(GLOSSARY_ENTRY.GENDER, gender == null ? "unknown" : gender)
                .set(GLOSSARY_ENTRY.NOTE, limit(blankToNull(note), 300))
                .set(GLOSSARY_ENTRY.MANUAL, true)
                // A corrected entry is an approved one.
                .set(GLOSSARY_ENTRY.STATUS, "approved")
                .set(GLOSSARY_ENTRY.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.EDITION_ID.eq(editionId))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такого запису в словнику немає.");
        }
    }

    /**
     * What the team sees behind «Оригінал» (рішення 30): the form in the original, where the
     * analysis met it, and the translated chapter to see it in context.
     *
     * @param snippet  the sentence around the first place it occurs in that chapter, or null
     * @param chapter  the edition's published chapter made from that original chapter, or null
     */
    record Original(String original, String reading, List<String> aliases, Integer sourceChapter, String snippet,
            TextLink chapter) {
    }

    record TextLink(String slug, String team, int number, String label) {
    }

    private static final TypeReference<List<space.panrid.novelka.platform.text.Block>> BLOCKS = new TypeReference<>() { };
    private static final int AROUND = 70;

    Original original(long editionId, long entryId) {
        GlossaryEntryRecord entry = db.selectFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.EDITION_ID.eq(editionId)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого запису немає."));
        List<String> aliases = json.readValue(entry.getAliases().data(), STRINGS);
        Integer number = entry.getSourceChapter();
        String snippet = null;
        TextLink link = null;
        if (number != null && number > 0) {
            var place = db.select(EDITION.NOVEL_ID, NOVEL.SLUG, TEAM.HANDLE).from(EDITION)
                    .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                    .where(EDITION.ID.eq(editionId)).fetchSingle();
            var source = db.select(SOURCE_CHAPTER.ID, SOURCE_CHAPTER.BLOCKS).from(SOURCE_CHAPTER)
                    .where(SOURCE_CHAPTER.NOVEL_ID.eq(place.value1()), SOURCE_CHAPTER.NUMBER.eq(number)).fetchOne();
            if (source != null) {
                List<String> forms = new ArrayList<>(List.of(entry.getJapanese()));
                forms.addAll(aliases);
                snippet = snippet(json.readValue(source.value2().data(), BLOCKS), forms);
                var chapter = db.select(CHAPTER.NUMBER, CHAPTER.LABEL).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.SOURCE_CHAPTER_ID.eq(source.value1()),
                                CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                        .fetchOne();
                if (chapter != null) {
                    link = new TextLink(place.value2(), place.value3(), chapter.value1(),
                            chapter.value2() == null ? String.valueOf(chapter.value1()) : chapter.value2());
                }
            }
        }
        return new Original(entry.getJapanese(), entry.getReading(), aliases, number, snippet, link);
    }

    /** The text around the first form found, cut at about a sentence either side. */
    static String snippet(List<space.panrid.novelka.platform.text.Block> blocks, List<String> forms) {
        for (var block : blocks) {
            String text = block.text();
            for (String form : forms) {
                int at = form == null || form.isBlank() ? -1 : text.indexOf(form);
                if (at >= 0) {
                    int from = Math.max(0, at - AROUND);
                    int to = Math.min(text.length(), at + form.length() + AROUND);
                    return (from > 0 ? "…" : "") + text.substring(from, to).strip() + (to < text.length() ? "…" : "");
                }
            }
        }
        return null;
    }

    void delete(long editionId, long entryId) {
        db.deleteFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.EDITION_ID.eq(editionId)).execute();
    }

    private Entry entry(GlossaryEntryRecord record) {
        JSONB aliases = record.getAliases();
        return new Entry(record.getId(), record.getJapanese(), record.getReading(), record.getUkrainian(),
                aliases == null ? List.of() : json.readValue(aliases.data(), STRINGS), record.getKind(),
                record.getGender(), record.getNote(), record.getSourceChapter(), record.getManual(), record.getStatus());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
