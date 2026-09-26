package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_ANALYSIS;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FORM;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.jooq.tables.records.GlossaryEntryRecord;
import space.panrid.novelka.jooq.tables.records.GlossaryFormRecord;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Names and terms of one novel, shared by all its translations. An entry is a notion with one
 * Ukrainian form; its forms in the originals are kept per language, so the novel can move to
 * a source in another language and keep its names. Analysis only adds what is missing; it
 * never overwrites an entry, so a name the team corrected stays corrected. Only entries the
 * text mentions go into a prompt.
 */
@Component
class Glossary {

    static final Set<String> KINDS = Set.of("character", "place", "organization", "term", "other");
    static final Set<String> GENDERS = Set.of("male", "female", "unknown");
    private static final int PROMPT_LIMIT = 200;
    private static final int UNLINKED_LIMIT = 300;
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;

    Glossary(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    /** The novel an edition translates and the language its original is in now. */
    record Scope(long novelId, String language) {
    }

    Scope scope(long editionId) {
        var row = db.select(NOVEL.ID, NOVEL.SOURCE_LANGUAGE).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого перекладу немає."));
        return new Scope(row.value1(), row.value2() == null ? "ja" : row.value2());
    }

    /**
     * @param original the form in the original's current language, or null when the entry
     *                 came from another language and this one has not named it yet
     */
    record Entry(long id, String original, String reading, List<String> aliases, String ukrainian, String kind,
            String gender, String note, Integer sourceChapter, boolean manual, String status) {

        /** One line for a prompt. */
        String line() {
            StringBuilder out = new StringBuilder(original);
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

        /** A line for analysis about an entry with no form in this language yet: known by its number. */
        String unlinkedLine() {
            StringBuilder out = new StringBuilder("#").append(id).append(" → ").append(ukrainian).append(" [").append(kind).append(']');
            if (note != null && !note.isBlank()) {
                out.append(" — ").append(note);
            }
            return out.toString();
        }
    }

    private List<Entry> entries(Scope scope, Condition where, List<? extends OrderField<?>> order, int limit, int offset) {
        return db.select(GLOSSARY_ENTRY.asterisk(), GLOSSARY_FORM.ORIGINAL, GLOSSARY_FORM.READING, GLOSSARY_FORM.ALIASES)
                .from(GLOSSARY_ENTRY)
                .leftJoin(GLOSSARY_FORM).on(GLOSSARY_FORM.ENTRY_ID.eq(GLOSSARY_ENTRY.ID), GLOSSARY_FORM.LANGUAGE.eq(scope.language()))
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId()), where)
                .orderBy(order).limit(limit).offset(offset)
                .fetch(this::entry);
    }

    /** Everything the translation may use: rejected entries never reach a prompt. */
    List<Entry> all(long editionId) {
        return entries(scope(editionId), GLOSSARY_ENTRY.STATUS.ne("rejected"),
                List.of(GLOSSARY_ENTRY.KIND, GLOSSARY_ENTRY.UKRAINIAN), Integer.MAX_VALUE, 0);
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
        Scope scope = scope(editionId);
        var novel = GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId());
        var where = (status == null ? DSL.noCondition() : GLOSSARY_ENTRY.STATUS.eq(status))
                .and(chapter == null ? DSL.noCondition() : GLOSSARY_ENTRY.SOURCE_CHAPTER.eq(chapter))
                .and(query == null || query.isBlank() ? DSL.noCondition() : GLOSSARY_ENTRY.UKRAINIAN.containsIgnoreCase(query.strip()));
        int total = db.fetchCount(GLOSSARY_ENTRY, novel.and(where));
        var order = "chapter".equals(sort)
                ? List.of(GLOSSARY_ENTRY.SOURCE_CHAPTER.asc().nullsFirst(), GLOSSARY_ENTRY.UKRAINIAN.asc())
                : List.of(DSL.lower(GLOSSARY_ENTRY.UKRAINIAN).asc(), GLOSSARY_ENTRY.ID.asc());
        int at = Math.max(1, page);
        List<Entry> items = entries(scope, where, order, PAGE, (at - 1) * PAGE);
        List<Integer> chapters = db.selectDistinct(GLOSSARY_ENTRY.SOURCE_CHAPTER).from(GLOSSARY_ENTRY)
                .where(novel, GLOSSARY_ENTRY.SOURCE_CHAPTER.isNotNull(), GLOSSARY_ENTRY.SOURCE_CHAPTER.gt(0))
                .orderBy(GLOSSARY_ENTRY.SOURCE_CHAPTER).fetch(GLOSSARY_ENTRY.SOURCE_CHAPTER);
        Map<String, Integer> counts = new java.util.HashMap<>(Map.of("new", 0, "approved", 0, "rejected", 0));
        db.select(GLOSSARY_ENTRY.STATUS, DSL.count()).from(GLOSSARY_ENTRY).where(novel)
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
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()), GLOSSARY_ENTRY.ID.in(ids)).execute();
    }

    /** Entries whose form in this language or an alias appears in the text. */
    List<Entry> mentionedIn(long editionId, String text) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : all(editionId)) {
            boolean mentioned = entry.original() != null && (text.contains(entry.original())
                    || entry.aliases().stream().anyMatch(alias -> !alias.isBlank() && text.contains(alias)));
            if (mentioned && found.size() < PROMPT_LIMIT) {
                found.add(entry);
            }
        }
        return found;
    }

    /** Entries with no form in this language yet: analysis may find how the text names them. */
    List<Entry> unlinked(long editionId) {
        return entries(scope(editionId), GLOSSARY_ENTRY.STATUS.ne("rejected").and(GLOSSARY_FORM.ORIGINAL.isNull()),
                List.of(GLOSSARY_ENTRY.SOURCE_CHAPTER.asc().nullsFirst(), GLOSSARY_ENTRY.ID.asc()), UNLINKED_LIMIT, 0);
    }

    /**
     * New entries from analysis. A form already known in this language is left alone; a new
     * form whose Ukrainian name belongs to an entry without a form in this language joins that
     * entry instead of making a twin.
     */
    int addFromAnalysis(long editionId, int chapter, List<Proposed> proposed) {
        Scope scope = scope(editionId);
        int added = 0;
        for (Proposed entry : proposed) {
            String original = entry.original() == null ? "" : entry.original().strip();
            String ukrainian = entry.ukrainian() == null ? "" : entry.ukrainian().strip();
            if (original.isEmpty() || ukrainian.isEmpty() || original.length() > 100 || ukrainian.length() > 100) {
                continue;
            }
            String kind = KINDS.contains(entry.kind()) ? entry.kind() : "other";
            added += db.transactionResult(trx -> {
                DSLContext tx = trx.dsl();
                if (tx.fetchExists(GLOSSARY_FORM, GLOSSARY_FORM.NOVEL_ID.eq(scope.novelId()),
                        GLOSSARY_FORM.LANGUAGE.eq(scope.language()), GLOSSARY_FORM.ORIGINAL.eq(original))) {
                    return 0;
                }
                Long twin = tx.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY)
                        .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId()), GLOSSARY_ENTRY.KIND.eq(kind),
                                DSL.lower(GLOSSARY_ENTRY.UKRAINIAN).eq(ukrainian.toLowerCase(java.util.Locale.ROOT)),
                                DSL.notExists(DSL.selectOne().from(GLOSSARY_FORM).where(GLOSSARY_FORM.ENTRY_ID.eq(GLOSSARY_ENTRY.ID),
                                        GLOSSARY_FORM.LANGUAGE.eq(scope.language()))))
                        .orderBy(GLOSSARY_ENTRY.ID).limit(1).fetchOne(GLOSSARY_ENTRY.ID);
                long id = twin != null ? twin : tx.insertInto(GLOSSARY_ENTRY)
                        .set(GLOSSARY_ENTRY.NOVEL_ID, scope.novelId())
                        .set(GLOSSARY_ENTRY.UKRAINIAN, ukrainian)
                        .set(GLOSSARY_ENTRY.KIND, kind)
                        .set(GLOSSARY_ENTRY.GENDER, GENDERS.contains(entry.gender()) ? entry.gender() : "unknown")
                        .set(GLOSSARY_ENTRY.NOTE, limit(blankToNull(entry.note()), 300))
                        .set(GLOSSARY_ENTRY.SOURCE_CHAPTER, chapter)
                        .returning(GLOSSARY_ENTRY.ID).fetchOne(GLOSSARY_ENTRY.ID);
                addForm(tx, scope, id, original, blankToNull(entry.reading()));
                return twin == null ? 1 : 0;
            });
        }
        return added;
    }

    /** @param original the form exactly as the original writes it */
    record Proposed(String original, String reading, String ukrainian, String kind, String gender, String note) {
    }

    /** Analysis says how this language writes a known entry («запис №12 тут — Ryo»). */
    record Known(long id, String original) {
    }

    int link(long editionId, List<Known> known) {
        Scope scope = scope(editionId);
        int linked = 0;
        for (Known item : known) {
            String original = item.original() == null ? "" : item.original().strip();
            if (original.isEmpty() || original.length() > 100
                    || !db.fetchExists(GLOSSARY_ENTRY, GLOSSARY_ENTRY.ID.eq(item.id()), GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId()))) {
                continue;
            }
            linked += addForm(db, scope, item.id(), original, null);
        }
        return linked;
    }

    /** Nothing when the entry has a form in this language already or another entry has this one. */
    private int addForm(DSLContext tx, Scope scope, long entryId, String original, String reading) {
        return tx.insertInto(GLOSSARY_FORM)
                .set(GLOSSARY_FORM.ENTRY_ID, entryId)
                .set(GLOSSARY_FORM.NOVEL_ID, scope.novelId())
                .set(GLOSSARY_FORM.LANGUAGE, scope.language())
                .set(GLOSSARY_FORM.ORIGINAL, original)
                .set(GLOSSARY_FORM.READING, reading)
                .onConflictDoNothing()
                .execute();
    }

    /** The team's correction: the Ukrainian form, kind, gender and note. */
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
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()))
                .execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такого запису в словнику немає.");
        }
    }

    /**
     * What the team sees behind «Оригінал» (рішення 30): the form in the original, where the
     * analysis met it, and the translated chapter to see it in context.
     *
     * @param original the form in the current original's language, or null if it has none yet
     * @param others   forms in the other languages the novel was taken from
     * @param snippet  the sentence around the first place it occurs in that chapter, or null
     * @param chapter  the edition's published chapter made from that original chapter, or null
     */
    record Original(String language, String original, String reading, List<String> aliases, List<Form> others,
            Integer sourceChapter, String snippet, TextLink chapter) {
    }

    record Form(String language, String original) {
    }

    record TextLink(String slug, String team, int number, String label) {
    }

    private static final TypeReference<List<space.panrid.novelka.platform.text.Block>> BLOCKS = new TypeReference<>() { };
    private static final int AROUND = 70;

    Original original(long editionId, long entryId) {
        Scope scope = scope(editionId);
        GlossaryEntryRecord entry = db.selectFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId())).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такого запису немає."));
        List<GlossaryFormRecord> forms = db.selectFrom(GLOSSARY_FORM).where(GLOSSARY_FORM.ENTRY_ID.eq(entryId))
                .orderBy(GLOSSARY_FORM.LANGUAGE).fetch();
        GlossaryFormRecord current = forms.stream().filter(form -> form.getLanguage().equals(scope.language())).findFirst().orElse(null);
        List<String> aliases = current == null ? List.of() : json.readValue(current.getAliases().data(), STRINGS);
        List<Form> others = forms.stream().filter(form -> form != current)
                .map(form -> new Form(form.getLanguage(), form.getOriginal())).toList();
        Integer number = entry.getSourceChapter();
        String snippet = null;
        TextLink link = null;
        if (number != null && number > 0) {
            var place = db.select(NOVEL.SLUG, TEAM.HANDLE).from(EDITION)
                    .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                    .where(EDITION.ID.eq(editionId)).fetchSingle();
            var source = db.select(SOURCE_CHAPTER.ID, SOURCE_CHAPTER.BLOCKS).from(SOURCE_CHAPTER)
                    .where(SOURCE_CHAPTER.NOVEL_ID.eq(scope.novelId()), SOURCE_CHAPTER.NUMBER.eq(number)).fetchOne();
            if (source != null) {
                List<String> spellings = new ArrayList<>();
                forms.forEach(form -> spellings.add(form.getOriginal()));
                spellings.addAll(aliases);
                snippet = snippet(json.readValue(source.value2().data(), BLOCKS), spellings);
                var chapter = db.select(CHAPTER.NUMBER, CHAPTER.LABEL).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.SOURCE_CHAPTER_ID.eq(source.value1()),
                                CHAPTER.PUBLISHED_REVISION_ID.isNotNull())
                        .fetchOne();
                if (chapter != null) {
                    link = new TextLink(place.value1(), place.value2(), chapter.value1(),
                            chapter.value2() == null ? String.valueOf(chapter.value1()) : chapter.value2());
                }
            }
        }
        return new Original(scope.language(), current == null ? null : current.getOriginal(),
                current == null ? null : current.getReading(), aliases, others, number, snippet, link);
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
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId())).execute();
    }

    private Entry entry(Record row) {
        JSONB aliases = row.get(GLOSSARY_FORM.ALIASES);
        return new Entry(row.get(GLOSSARY_ENTRY.ID), row.get(GLOSSARY_FORM.ORIGINAL), row.get(GLOSSARY_FORM.READING),
                aliases == null ? List.of() : json.readValue(aliases.data(), STRINGS), row.get(GLOSSARY_ENTRY.UKRAINIAN),
                row.get(GLOSSARY_ENTRY.KIND), row.get(GLOSSARY_ENTRY.GENDER), row.get(GLOSSARY_ENTRY.NOTE),
                row.get(GLOSSARY_ENTRY.SOURCE_CHAPTER), row.get(GLOSSARY_ENTRY.MANUAL), row.get(GLOSSARY_ENTRY.STATUS));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
