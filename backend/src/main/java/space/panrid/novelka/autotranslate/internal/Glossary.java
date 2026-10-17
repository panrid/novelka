package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_ANALYSIS;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_ORIGINAL;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FACT;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FORM;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_LINK;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_MENTION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_MENTION_SCAN;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;
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
class Glossary implements space.panrid.novelka.autotranslate.GlossaryCards {

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
    /** @param looseForms the Ukrainian form is found as before: any ending, any case (WordForms) */
    record Entry(long id, String original, String reading, List<String> aliases, String ukrainian, String kind,
            String gender, String note, Integer sourceChapter, boolean manual, String status, boolean looseForms, Long sameAs,
            Boolean inStory) {

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

        /** A line for analysis: the entry under its number, with what is known of it already. */
        String analysisLine(List<String> facts) {
            StringBuilder out = new StringBuilder("#").append(id).append(' ').append(line());
            if (!facts.isEmpty()) {
                out.append(" Known: ").append(String.join(" ", facts));
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

    static final int PAGE = 20;
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
        return addFromAnalysis(editionId, chapter, proposed, new ArrayList<>());
    }

    /** @param added filled with the Ukrainian forms of the entries that are new (for the run's journal) */
    int addFromAnalysis(long editionId, int chapter, List<Proposed> proposed, List<String> newForms) {
        Scope scope = scope(editionId);
        int added = 0;
        for (Proposed entry : proposed) {
            String original = entry.original() == null ? "" : entry.original().strip();
            String ukrainian = entry.ukrainian() == null ? "" : entry.ukrainian().strip();
            if (original.isEmpty() || ukrainian.isEmpty() || original.length() > 100 || ukrainian.length() > 100) {
                continue;
            }
            String kind = KINDS.contains(entry.kind()) ? entry.kind() : "other";
            // 水 or 火 alone is an ordinary word that every paragraph mentions; only a person goes by one character (涼).
            if (original.codePointCount(0, original.length()) == 1 && !kind.equals("character")) {
                continue;
            }
            added += db.transactionResult(trx -> {
                DSLContext tx = trx.dsl();
                if (tx.fetchExists(GLOSSARY_FORM, GLOSSARY_FORM.NOVEL_ID.eq(scope.novelId()),
                        GLOSSARY_FORM.LANGUAGE.eq(scope.language()), GLOSSARY_FORM.ORIGINAL.eq(original))) {
                    return 0;
                }
                // The same thing under another spelling (ソルジャーアント and 兵士蟻 → «солдат-мураха»):
                // one entry, the second spelling becomes its alias, not a duplicate to sort out by hand.
                Long same = tx.select(GLOSSARY_ENTRY.ID).from(GLOSSARY_ENTRY).join(GLOSSARY_FORM)
                        .on(GLOSSARY_FORM.ENTRY_ID.eq(GLOSSARY_ENTRY.ID), GLOSSARY_FORM.LANGUAGE.eq(scope.language()))
                        .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope.novelId()), GLOSSARY_ENTRY.KIND.eq(kind),
                                GLOSSARY_ENTRY.STATUS.ne("rejected"),
                                DSL.lower(GLOSSARY_ENTRY.UKRAINIAN).eq(ukrainian.toLowerCase(java.util.Locale.ROOT)))
                        .orderBy(GLOSSARY_ENTRY.ID).limit(1).fetchOne(GLOSSARY_ENTRY.ID);
                if (same != null) {
                    tx.update(GLOSSARY_FORM)
                            .set(GLOSSARY_FORM.ALIASES, DSL.field("{0} || {1}::jsonb", JSONB.class, GLOSSARY_FORM.ALIASES,
                                    DSL.val(json.writeValueAsString(List.of(original)))))
                            .where(GLOSSARY_FORM.ENTRY_ID.eq(same), GLOSSARY_FORM.LANGUAGE.eq(scope.language()),
                                    DSL.condition("NOT jsonb_exists({0}, {1})", GLOSSARY_FORM.ALIASES, DSL.val(original)))
                            .execute();
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
                        .set(GLOSSARY_ENTRY.IN_STORY, kind.equals("character") && entry.role() != null
                                ? (Boolean) "acting".equals(entry.role()) : null)
                        .returning(GLOSSARY_ENTRY.ID).fetchOne(GLOSSARY_ENTRY.ID);
                addForm(tx, scope, id, original, blankToNull(entry.reading()));
                if (twin == null) {
                    newForms.add(ukrainian);
                }
                return twin == null ? 1 : 0;
            });
        }
        return added;
    }

    // ---- facts: what the novel tells about an entry, chapter by chapter --------------------------

    /** A fact analysis found: about entry {@code id}, or (id 0) about a new entry by its {@code original} form. */
    record ProposedFact(long id, String original, String fact) {
    }

    record Fact(long id, int chapter, String text, boolean manual) {
    }

    static final int FACTS_PER_CHAPTER = 3;
    static final int FACT_MAX = 300;
    /** Facts analysis is shown for an entry, so it does not repeat them: the latest ones. */
    static final int FACTS_SHOWN = 4;

    /** The latest facts of each entry, oldest first, for the analysis prompt. */
    Map<Long, List<String>> recentFacts(java.util.Collection<Long> entryIds) {
        Map<Long, List<String>> out = new java.util.HashMap<>();
        if (entryIds.isEmpty()) {
            return out;
        }
        var rank = DSL.rowNumber().over(DSL.partitionBy(GLOSSARY_FACT.ENTRY_ID).orderBy(GLOSSARY_FACT.CHAPTER.desc(), GLOSSARY_FACT.ID.desc()));
        var ranked = DSL.select(GLOSSARY_FACT.ENTRY_ID, GLOSSARY_FACT.CHAPTER, GLOSSARY_FACT.ID, GLOSSARY_FACT.FACT, rank.as("rank"))
                .from(GLOSSARY_FACT).where(GLOSSARY_FACT.ENTRY_ID.in(entryIds)).asTable("ranked");
        db.select(ranked.field(GLOSSARY_FACT.ENTRY_ID), ranked.field(GLOSSARY_FACT.FACT)).from(ranked)
                .where(ranked.field("rank", Integer.class).le(FACTS_SHOWN))
                .orderBy(ranked.field(GLOSSARY_FACT.CHAPTER), ranked.field(GLOSSARY_FACT.ID))
                .forEach(r -> out.computeIfAbsent(r.value1(), key -> new ArrayList<>()).add(r.value2()));
        return out;
    }

    /**
     * Facts from analysis of chapter {@code chapter}. An entry is taken by a number analysis was
     * shown, or a new one by its form in this language; a fact the entry has already is skipped.
     *
     * @return how many were added
     */
    int addFacts(long editionId, int chapter, Set<Long> shown, List<ProposedFact> facts) {
        Scope scope = scope(editionId);
        Map<Long, Integer> perEntry = new java.util.HashMap<>();
        int added = 0;
        for (ProposedFact proposed : facts) {
            String text = proposed.fact() == null ? "" : proposed.fact().strip();
            if (text.isEmpty() || text.length() > FACT_MAX) {
                continue;
            }
            Long entryId = proposed.id() > 0 ? (shown.contains(proposed.id()) ? proposed.id() : null) : byOriginal(scope, proposed.original());
            if (entryId == null || perEntry.merge(entryId, 1, Integer::sum) > FACTS_PER_CHAPTER) {
                continue;
            }
            boolean known = db.fetchExists(GLOSSARY_FACT, GLOSSARY_FACT.ENTRY_ID.eq(entryId),
                    DSL.lower(GLOSSARY_FACT.FACT).eq(text.toLowerCase(java.util.Locale.ROOT)));
            if (!known) {
                db.insertInto(GLOSSARY_FACT).set(GLOSSARY_FACT.ENTRY_ID, entryId).set(GLOSSARY_FACT.CHAPTER, chapter)
                        .set(GLOSSARY_FACT.FACT, text).execute();
                added++;
            }
        }
        return added;
    }

    private Long byOriginal(Scope scope, String original) {
        String form = original == null ? "" : original.strip();
        if (form.isEmpty()) {
            return null;
        }
        return db.select(GLOSSARY_FORM.ENTRY_ID).from(GLOSSARY_FORM)
                .where(GLOSSARY_FORM.NOVEL_ID.eq(scope.novelId()), GLOSSARY_FORM.LANGUAGE.eq(scope.language()),
                        GLOSSARY_FORM.ORIGINAL.eq(form).or(DSL.condition("jsonb_exists({0}, {1})", GLOSSARY_FORM.ALIASES, DSL.val(form))))
                .limit(1).fetchOne(GLOSSARY_FORM.ENTRY_ID);
    }

    Map<Long, Integer> factCounts(java.util.Collection<Long> entryIds) {
        Map<Long, Integer> out = new java.util.HashMap<>();
        if (!entryIds.isEmpty()) {
            db.select(GLOSSARY_FACT.ENTRY_ID, DSL.count()).from(GLOSSARY_FACT).where(GLOSSARY_FACT.ENTRY_ID.in(entryIds))
                    .groupBy(GLOSSARY_FACT.ENTRY_ID).forEach(r -> out.put(r.value1(), r.value2()));
        }
        return out;
    }

    static final int CARDS = 4;
    private static final int SELECTION_MAX = 300;

    @Override
    public List<Term> known(long editionId, int chapter) {
        var told = DSL.exists(DSL.selectOne().from(GLOSSARY_FACT)
                .where(GLOSSARY_FACT.ENTRY_ID.eq(GLOSSARY_ENTRY.ID), GLOSSARY_FACT.CHAPTER.le(chapter)));
        return db.select(GLOSSARY_ENTRY.ID, GLOSSARY_ENTRY.UKRAINIAN, GLOSSARY_ENTRY.KIND, GLOSSARY_ENTRY.LOOSE_FORMS).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()), GLOSSARY_ENTRY.STATUS.ne("rejected"),
                        GLOSSARY_ENTRY.SOURCE_CHAPTER.isNull().or(GLOSSARY_ENTRY.SOURCE_CHAPTER.le(chapter)),
                        GLOSSARY_ENTRY.NOTE.isNotNull().and(GLOSSARY_ENTRY.NOTE.ne("")).or(told))
                .orderBy(DSL.length(GLOSSARY_ENTRY.UKRAINIAN).desc())
                .fetch(r -> new Term(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    // ---- what an entry is tied to in a chapter: the original, and the team's own links ----------

    /** The original paragraphs of a chapter (by the ids its translation keeps) and their language; empty when the site has none. */
    private java.util.Optional<Map<String, String>> originalBlocks(long editionId, int chapter) {
        Long sourceId = db.select(CHAPTER.SOURCE_CHAPTER_ID).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.eq(chapter)).fetchOne(CHAPTER.SOURCE_CHAPTER_ID);
        JSONB blocks = sourceId == null ? null
                : db.select(SOURCE_CHAPTER.BLOCKS).from(SOURCE_CHAPTER).where(SOURCE_CHAPTER.ID.eq(sourceId)).fetchOne(SOURCE_CHAPTER.BLOCKS);
        if (blocks == null) {
            blocks = db.select(EDITION_ORIGINAL.BLOCKS).from(EDITION_ORIGINAL)
                    .where(EDITION_ORIGINAL.EDITION_ID.eq(editionId), EDITION_ORIGINAL.NUMBER.eq(chapter)).fetchOne(EDITION_ORIGINAL.BLOCKS);
        }
        if (blocks == null) {
            return java.util.Optional.empty();
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (var block : json.readValue(blocks.data(), BLOCKS)) {
            if (!block.text().isBlank()) {
                out.put(block.id(), block.text());
            }
        }
        return java.util.Optional.of(out);
    }

    /** Entries each original paragraph names, by their forms in any of the novel's languages. */
    private java.util.Optional<Map<String, Set<Long>>> byOriginal(long editionId, int chapter) {
        return originalBlocks(editionId, chapter).map(paragraphs -> {
            Map<Long, List<String>> forms = new java.util.HashMap<>();
            db.select(GLOSSARY_FORM.ENTRY_ID, GLOSSARY_FORM.ORIGINAL, GLOSSARY_FORM.ALIASES).from(GLOSSARY_FORM).join(GLOSSARY_ENTRY)
                    .on(GLOSSARY_ENTRY.ID.eq(GLOSSARY_FORM.ENTRY_ID))
                    .where(GLOSSARY_FORM.NOVEL_ID.eq(scope(editionId).novelId()), GLOSSARY_ENTRY.STATUS.ne("rejected"))
                    .forEach(r -> {
                        List<String> all = forms.computeIfAbsent(r.value1(), key -> new ArrayList<>());
                        all.add(r.value2());
                        if (r.value3() != null) {
                            all.addAll(json.readValue(r.value3().data(), STRINGS));
                        }
                    });
            Map<String, Set<Long>> out = new java.util.HashMap<>();
            paragraphs.forEach((blockId, text) -> forms.forEach((entryId, written) -> {
                if (written.stream().anyMatch(form -> form != null && !form.isBlank() && text.contains(form))) {
                    out.computeIfAbsent(blockId, key -> new java.util.HashSet<>()).add(entryId);
                }
            }));
            return out;
        });
    }

    private List<BlockLink> manualLinks(long editionId, int chapter) {
        return db.select(GLOSSARY_LINK.BLOCK_ID, GLOSSARY_LINK.ENTRY_ID, GLOSSARY_LINK.FORM, GLOSSARY_LINK.LINKED).from(GLOSSARY_LINK)
                .where(GLOSSARY_LINK.EDITION_ID.eq(editionId), GLOSSARY_LINK.CHAPTER_NUMBER.eq(chapter))
                .fetch(r -> new BlockLink(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    @Override
    public List<Card> characters(long editionId, Integer chapter, Sort sort) {
        int upTo = chapter == null ? Integer.MAX_VALUE : chapter;
        // Acting characters only (monsters, people only mentioned or thought of stay in the hints),
        // a group once — met when any of its names was met, in the order the novel met them.
        List<GlossaryEntryRecord> mains = db.selectFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()), GLOSSARY_ENTRY.STATUS.ne("rejected"),
                        GLOSSARY_ENTRY.KIND.eq("character"), GLOSSARY_ENTRY.SAME_AS.isNull(), GLOSSARY_ENTRY.IN_STORY.isDistinctFrom(false))
                .fetch();
        Map<Long, List<GlossaryEntryRecord>> groups = groups(mains.stream().map(GlossaryEntryRecord::getId).toList());
        java.util.function.ToIntFunction<GlossaryEntryRecord> firstMet = main -> groups.getOrDefault(main.getId(), List.of(main)).stream()
                .mapToInt(entry -> entry.getSourceChapter() == null ? 0 : entry.getSourceChapter()).min().orElse(0);
        List<GlossaryEntryRecord> met = mains.stream().filter(main -> firstMet.applyAsInt(main) <= upTo)
                .sorted(java.util.Comparator.comparingInt(firstMet).thenComparing(GlossaryEntryRecord::getId)).toList();
        Map<Long, Integer> named = mentions(editionId, upTo, groups);
        List<Card> out = cards(met, upTo).stream()
                .map(card -> new Card(card.id(), card.ukrainian(), card.kind(), card.gender(), card.facts(), named.getOrDefault(card.id(), 0)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // Sorting is stable: equals stay in the order the novel met them.
        switch (sort) {
            case MENTIONS -> out.sort(java.util.Comparator.comparingInt(card -> -card.mentions()));
            case NAME -> {
                java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.of("uk"));
                out.sort(java.util.Comparator.comparing(Card::ukrainian, collator));
            }
            case APPEARANCE -> {
            }
        }
        return out;
    }

    // ---- mentions: how often the novel names each character ---------------------------------------

    /** Mentions of each character group in chapters up to {@code upTo}, all its names together. */
    private Map<Long, Integer> mentions(long editionId, int upTo, Map<Long, List<GlossaryEntryRecord>> groups) {
        countMentions(editionId);
        Map<Long, Long> mainOf = new java.util.HashMap<>();
        groups.forEach((main, members) -> members.forEach(entry -> mainOf.put(entry.getId(), main)));
        Map<Long, Integer> out = new java.util.HashMap<>();
        db.select(GLOSSARY_MENTION.ENTRY_ID, DSL.sum(GLOSSARY_MENTION.MENTIONS)).from(GLOSSARY_MENTION)
                .where(GLOSSARY_MENTION.EDITION_ID.eq(editionId), GLOSSARY_MENTION.CHAPTER_NUMBER.le(upTo))
                .groupBy(GLOSSARY_MENTION.ENTRY_ID)
                .forEach(r -> {
                    Long main = mainOf.get(r.value1());
                    if (main != null) {
                        out.merge(main, r.value2().intValue(), Integer::sum);
                    }
                });
        return out;
    }

    private record Named(long entryId, String form) {
    }

    /**
     * Counts the chapters not counted yet, or counted with other names or another text: in the
     * original by the characters' original forms, without one in the translation by their
     * Ukrainian forms. A longer name takes its place first: «Лісія Фаргайт» is not also «Лісія».
     */
    private void countMentions(long editionId) {
        long novelId = scope(editionId).novelId();
        List<GlossaryEntryRecord> characters = db.selectFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.STATUS.ne("rejected"), GLOSSARY_ENTRY.KIND.eq("character"))
                .orderBy(GLOSSARY_ENTRY.ID).fetch();
        List<Named> originals = new ArrayList<>();
        if (!characters.isEmpty()) {
            db.select(GLOSSARY_FORM.ENTRY_ID, GLOSSARY_FORM.ORIGINAL, GLOSSARY_FORM.ALIASES).from(GLOSSARY_FORM)
                    .where(GLOSSARY_FORM.ENTRY_ID.in(characters.stream().map(GlossaryEntryRecord::getId).toList()))
                    .orderBy(GLOSSARY_FORM.ENTRY_ID, GLOSSARY_FORM.LANGUAGE)
                    .forEach(r -> {
                        List<String> all = new ArrayList<>();
                        all.add(r.value2());
                        if (r.value3() != null) {
                            all.addAll(json.readValue(r.value3().data(), STRINGS));
                        }
                        all.stream().filter(form -> form != null && !form.isBlank()).distinct()
                                .forEach(form -> originals.add(new Named(r.value1(), form.strip())));
                    });
        }
        originals.sort(java.util.Comparator.comparingInt((Named named) -> -named.form().length()));
        List<GlossaryEntryRecord> byLength = characters.stream()
                .sorted(java.util.Comparator.comparingInt((GlossaryEntryRecord entry) -> -entry.getUkrainian().length())).toList();
        int names = java.util.Objects.hash(originals, characters.stream()
                .map(entry -> entry.getId() + ":" + entry.getUkrainian() + ":" + entry.getLooseForms()).toList());

        Map<Integer, String> originalAt = new java.util.HashMap<>();
        db.select(EDITION_ORIGINAL.NUMBER, EDITION_ORIGINAL.UPDATED_AT).from(EDITION_ORIGINAL).where(EDITION_ORIGINAL.EDITION_ID.eq(editionId))
                .forEach(r -> originalAt.put(r.value1(), "o" + r.value2()));
        Map<Integer, String> texts = new java.util.TreeMap<>(originalAt);
        db.select(CHAPTER.NUMBER, CHAPTER.SOURCE_CHAPTER_ID, CHAPTER.PUBLISHED_REVISION_ID).from(CHAPTER).where(CHAPTER.EDITION_ID.eq(editionId))
                .forEach(r -> {
                    if (r.value2() != null) {
                        texts.put(r.value1(), "s" + r.value2());
                    } else if (!originalAt.containsKey(r.value1()) && r.value3() != null) {
                        texts.put(r.value1(), "r" + r.value3());
                    }
                });
        Map<Integer, Integer> scanned = db.select(GLOSSARY_MENTION_SCAN.CHAPTER_NUMBER, GLOSSARY_MENTION_SCAN.FINGERPRINT).from(GLOSSARY_MENTION_SCAN)
                .where(GLOSSARY_MENTION_SCAN.EDITION_ID.eq(editionId)).fetchMap(GLOSSARY_MENTION_SCAN.CHAPTER_NUMBER, GLOSSARY_MENTION_SCAN.FINGERPRINT);
        texts.forEach((number, text) -> {
            int fingerprint = java.util.Objects.hash(names, text);
            if (Integer.valueOf(fingerprint).equals(scanned.get(number))) {
                return;
            }
            Map<Long, Integer> counted = new java.util.HashMap<>();
            var original = originalBlocks(editionId, number);
            if (original.isPresent()) {
                for (String paragraph : original.get().values()) {
                    boolean[] taken = new boolean[paragraph.length()];
                    for (Named named : originals) {
                        for (int at = paragraph.indexOf(named.form()); at >= 0; at = paragraph.indexOf(named.form(), at + 1)) {
                            int end = at + named.form().length();
                            if (wordAt(paragraph, at, end) && take(taken, at, end)) {
                                counted.merge(named.entryId(), 1, Integer::sum);
                            }
                        }
                    }
                }
            } else if (text.startsWith("r")) {
                JSONB blocks = db.select(REVISION.BLOCKS).from(REVISION).where(REVISION.ID.eq(Long.parseLong(text.substring(1))))
                        .fetchOne(REVISION.BLOCKS);
                for (var block : blocks == null ? List.<space.panrid.novelka.platform.text.Block>of() : json.readValue(blocks.data(), BLOCKS)) {
                    String paragraph = block.text();
                    boolean[] taken = new boolean[paragraph.length()];
                    for (GlossaryEntryRecord entry : byLength) {
                        for (int[] at : WordForms.find(paragraph, entry.getUkrainian(), Boolean.TRUE.equals(entry.getLooseForms()))) {
                            if (take(taken, at[0], at[1])) {
                                counted.merge(entry.getId(), 1, Integer::sum);
                            }
                        }
                    }
                }
            }
            db.transaction(trx -> {
                DSLContext tx = trx.dsl();
                tx.deleteFrom(GLOSSARY_MENTION).where(GLOSSARY_MENTION.EDITION_ID.eq(editionId), GLOSSARY_MENTION.CHAPTER_NUMBER.eq(number)).execute();
                if (!counted.isEmpty()) {
                    var insert = tx.insertInto(GLOSSARY_MENTION, GLOSSARY_MENTION.EDITION_ID, GLOSSARY_MENTION.CHAPTER_NUMBER,
                            GLOSSARY_MENTION.ENTRY_ID, GLOSSARY_MENTION.MENTIONS);
                    counted.forEach((entryId, times) -> insert.values(editionId, number, entryId, times));
                    insert.onConflictDoNothing().execute();
                }
                tx.insertInto(GLOSSARY_MENTION_SCAN).set(GLOSSARY_MENTION_SCAN.EDITION_ID, editionId)
                        .set(GLOSSARY_MENTION_SCAN.CHAPTER_NUMBER, number).set(GLOSSARY_MENTION_SCAN.FINGERPRINT, fingerprint)
                        .onConflict(GLOSSARY_MENTION_SCAN.EDITION_ID, GLOSSARY_MENTION_SCAN.CHAPTER_NUMBER).doUpdate()
                        .set(GLOSSARY_MENTION_SCAN.FINGERPRINT, fingerprint).set(GLOSSARY_MENTION_SCAN.SCANNED_AT, DSL.currentOffsetDateTime())
                        .execute();
            });
        });
    }

    /** A name in letters of an alphabet stands as a whole word («Al» is not in «Also»); kanji and kana stand anywhere. */
    private static boolean wordAt(String text, int from, int to) {
        boolean spaced = Character.isAlphabetic(text.charAt(from)) && !isIdeographic(text.charAt(from));
        if (!spaced) {
            return true;
        }
        return (from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1)))
                && (to >= text.length() || !Character.isLetterOrDigit(text.charAt(to)));
    }

    private static boolean isIdeographic(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }

    /** Marks the place as taken by a name, unless a longer name already stands there. */
    private static boolean take(boolean[] taken, int from, int to) {
        for (int i = from; i < to; i++) {
            if (taken[i]) {
                return false;
            }
        }
        java.util.Arrays.fill(taken, from, to, true);
        return true;
    }

    @Override
    public int characterCount(long editionId) {
        return db.fetchCount(GLOSSARY_ENTRY, GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()),
                GLOSSARY_ENTRY.STATUS.ne("rejected"), GLOSSARY_ENTRY.KIND.eq("character"), GLOSSARY_ENTRY.SAME_AS.isNull(),
                GLOSSARY_ENTRY.IN_STORY.isDistinctFrom(false));
    }

    @Override
    public Hints hints(long editionId, int chapter) {
        List<Term> terms = new ArrayList<>(known(editionId, chapter));
        List<BlockLink> links = new ArrayList<>();
        var original = byOriginal(editionId, chapter);
        original.ifPresent(found -> found.forEach((blockId, ids) -> ids.forEach(id -> links.add(new BlockLink(blockId, id, null, true)))));
        List<BlockLink> manual = manualLinks(editionId, chapter);
        links.addAll(manual);
        // A word the team tied by hand is marked even if the entry has no note yet.
        Set<Long> listed = terms.stream().map(Term::id).collect(java.util.stream.Collectors.toSet());
        List<Long> missing = manual.stream().filter(BlockLink::linked).map(BlockLink::entryId).filter(id -> !listed.contains(id)).distinct().toList();
        if (!missing.isEmpty()) {
            db.select(GLOSSARY_ENTRY.ID, GLOSSARY_ENTRY.UKRAINIAN, GLOSSARY_ENTRY.KIND, GLOSSARY_ENTRY.LOOSE_FORMS).from(GLOSSARY_ENTRY)
                    .where(GLOSSARY_ENTRY.ID.in(missing)).forEach(r -> terms.add(new Term(r.value1(), r.value2(), r.value3(), r.value4())));
        }
        return new Hints(original.isPresent(), terms, links);
    }

    /** The team ties words of a paragraph to an entry, or unties them from it there. */
    void link(long editionId, int chapter, String blockId, long entryId, String form, boolean linked, long accountId) {
        requireEntry(editionId, entryId);
        String words = form == null ? "" : form.strip();
        if (words.isEmpty() || words.length() > 100 || blockId == null || blockId.isBlank() || blockId.length() > 64) {
            throw UserFacingException.badRequest("Виділіть слово чи кілька слів в одному абзаці (до 100 знаків).");
        }
        db.insertInto(GLOSSARY_LINK).set(GLOSSARY_LINK.EDITION_ID, editionId).set(GLOSSARY_LINK.CHAPTER_NUMBER, chapter)
                .set(GLOSSARY_LINK.BLOCK_ID, blockId).set(GLOSSARY_LINK.ENTRY_ID, entryId).set(GLOSSARY_LINK.FORM, words)
                .set(GLOSSARY_LINK.LINKED, linked).set(GLOSSARY_LINK.ADDED_BY, accountId)
                .onConflict(GLOSSARY_LINK.EDITION_ID, GLOSSARY_LINK.CHAPTER_NUMBER, GLOSSARY_LINK.BLOCK_ID, GLOSSARY_LINK.ENTRY_ID, GLOSSARY_LINK.FORM)
                .doUpdate().set(GLOSSARY_LINK.LINKED, linked).set(GLOSSARY_LINK.ADDED_BY, accountId).set(GLOSSARY_LINK.CREATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    @Override
    public List<Card> named(long editionId, int chapter, String selection, String blockId) {
        String text = selection == null ? "" : selection.strip();
        if (text.isEmpty() || text.length() > SELECTION_MAX) {
            return List.of();
        }
        long novelId = scope(editionId).novelId();
        // In a paragraph: only what the lamp marks there — named by the original or tied by the team,
        // less what the team untied (by the words untied, if they are in the selection).
        java.util.function.Predicate<GlossaryEntryRecord> belongs = entry -> true;
        Set<Long> tiedByHand = new java.util.HashSet<>();
        if (blockId != null && !blockId.isBlank()) {
            List<BlockLink> manual = manualLinks(editionId, chapter).stream().filter(link -> link.blockId().equals(blockId)).toList();
            var original = byOriginal(editionId, chapter);
            Set<Long> allowed = new java.util.HashSet<>();
            original.ifPresent(found -> allowed.addAll(found.getOrDefault(blockId, Set.of())));
            manual.stream().filter(link -> link.linked() && text.toLowerCase(java.util.Locale.ROOT).contains(link.text().toLowerCase(java.util.Locale.ROOT)))
                    .forEach(link -> tiedByHand.add(link.entryId()));
            Set<Long> untied = manual.stream().filter(link -> !link.linked() && text.toLowerCase(java.util.Locale.ROOT)
                    .contains(link.text().toLowerCase(java.util.Locale.ROOT))).map(BlockLink::entryId).collect(java.util.stream.Collectors.toSet());
            boolean byOriginal = original.isPresent();
            belongs = entry -> !untied.contains(entry.getId()) && (!byOriginal || allowed.contains(entry.getId()));
        }
        java.util.function.Predicate<GlossaryEntryRecord> keep = belongs;
        List<GlossaryEntryRecord> found = db.selectFrom(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.STATUS.ne("rejected"))
                .fetch().stream()
                .filter(entry -> tiedByHand.contains(entry.getId())
                        || keep.test(entry) && !WordForms.find(text, entry.getUkrainian(), entry.getLooseForms()).isEmpty())
                // «Крижаний спис» before «спис»; then the one the novel met first.
                .sorted(java.util.Comparator.comparingInt((GlossaryEntryRecord entry) -> -entry.getUkrainian().length())
                        .thenComparing(entry -> entry.getSourceChapter() == null ? 0 : entry.getSourceChapter()))
                .limit(CARDS).toList();
        return cards(found, chapter);
    }

    /** What the novel told about each entry by {@code chapter}: its first note from the chapter it appeared, then the facts. */
    private List<Card> cards(List<GlossaryEntryRecord> found, int chapter) {
        if (found.isEmpty()) {
            return List.of();
        }
        // Another name of a main entry leads to the main one's card.
        List<Long> mains = found.stream().map(entry -> entry.getSameAs() != null ? entry.getSameAs() : entry.getId()).distinct().toList();
        Map<Long, List<GlossaryEntryRecord>> groups = groups(mains);
        Map<Long, List<String>> facts = new java.util.HashMap<>();
        List<Long> everyone = groups.values().stream().flatMap(List::stream).map(GlossaryEntryRecord::getId).toList();
        if (!everyone.isEmpty()) {
            db.select(GLOSSARY_FACT.ENTRY_ID, GLOSSARY_FACT.FACT).from(GLOSSARY_FACT)
                    .where(GLOSSARY_FACT.ENTRY_ID.in(everyone), GLOSSARY_FACT.CHAPTER.le(chapter))
                    .orderBy(GLOSSARY_FACT.CHAPTER, GLOSSARY_FACT.ID)
                    .forEach(r -> facts.computeIfAbsent(r.value1(), key -> new ArrayList<>()).add(r.value2()));
        }
        Set<Long> foundIds = found.stream().map(GlossaryEntryRecord::getId).collect(java.util.stream.Collectors.toSet());
        List<Card> out = new ArrayList<>();
        for (long mainId : mains) {
            List<GlossaryEntryRecord> members = groups.getOrDefault(mainId, List.of());
            if (members.isEmpty()) {
                continue;
            }
            List<GlossaryEntryRecord> met = members.stream().filter(entry -> metBy(entry, chapter)).toList();
            GlossaryEntryRecord main = members.getFirst();
            // The main name once the novel has told it; before that, the name the reader knows («дружина Лота»),
            // or the selected word itself when the novel has not met any of them by this chapter.
            GlossaryEntryRecord shown = met.contains(main) ? main : !met.isEmpty() ? met.getFirst()
                    : members.stream().filter(entry -> foundIds.contains(entry.getId())).findFirst().orElse(main);
            List<String> told = new ArrayList<>();
            for (GlossaryEntryRecord entry : met) {
                // The first note is what analysis said when the entry appeared: shown from that chapter on.
                if (entry.getNote() != null && !entry.getNote().isBlank() && !told.contains(entry.getNote())) {
                    told.add(entry.getNote());
                }
            }
            for (GlossaryEntryRecord entry : members) {
                facts.getOrDefault(entry.getId(), List.of()).stream().filter(fact -> !told.contains(fact)).forEach(told::add);
            }
            out.add(new Card(mainId, shown.getUkrainian(), main.getKind(), main.getGender(), told));
        }
        return out;
    }

    private static boolean metBy(GlossaryEntryRecord entry, int chapter) {
        return entry.getSourceChapter() == null || entry.getSourceChapter() <= chapter;
    }

    /** Each main entry with its other names: the main one first, the rest in the order the novel met them. */
    private Map<Long, List<GlossaryEntryRecord>> groups(java.util.Collection<Long> mains) {
        Map<Long, List<GlossaryEntryRecord>> out = new java.util.HashMap<>();
        if (mains.isEmpty()) {
            return out;
        }
        db.selectFrom(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.in(mains).or(GLOSSARY_ENTRY.SAME_AS.in(mains)), GLOSSARY_ENTRY.STATUS.ne("rejected"))
                .orderBy(GLOSSARY_ENTRY.SOURCE_CHAPTER.asc().nullsFirst(), GLOSSARY_ENTRY.ID)
                .forEach(entry -> {
                    long main = entry.getSameAs() != null ? entry.getSameAs() : entry.getId();
                    List<GlossaryEntryRecord> group = out.computeIfAbsent(main, key -> new ArrayList<>());
                    if (entry.getId() == main) {
                        group.addFirst(entry);
                    } else {
                        group.add(entry);
                    }
                });
        // A group whose main entry was rejected (or is itself another name) has no card.
        out.values().removeIf(group -> group.isEmpty() || group.getFirst().getSameAs() != null || !mains.contains(group.getFirst().getId()));
        return out;
    }

    // ---- groups: other names of one entry, for the hints only ------------------------------------

    /** «Це той самий, що…»: the entry becomes another name of {@code mainId} (or of the main entry that one belongs to). */
    void mergeInto(long editionId, long entryId, long mainId) {
        requireEntry(editionId, entryId);
        requireEntry(editionId, mainId);
        Long further = db.select(GLOSSARY_ENTRY.SAME_AS).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.eq(mainId)).fetchOne(GLOSSARY_ENTRY.SAME_AS);
        long main = further != null ? further : mainId;
        if (main == entryId) {
            throw UserFacingException.badRequest("Це вже головний запис цієї групи.");
        }
        db.transaction(trx -> {
            DSLContext tx = trx.dsl();
            // Names that pointed to this entry now point to the main one: a group has one main entry.
            tx.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, main).where(GLOSSARY_ENTRY.SAME_AS.eq(entryId)).execute();
            tx.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, main).set(GLOSSARY_ENTRY.UPDATED_AT, DSL.currentOffsetDateTime())
                    .where(GLOSSARY_ENTRY.ID.eq(entryId)).execute();
        });
    }

    /** «Окремий запис»: the entry leaves its group. */
    void separate(long editionId, long entryId) {
        requireEntry(editionId, entryId);
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, (Long) null).where(GLOSSARY_ENTRY.ID.eq(entryId)).execute();
    }

    /** «Зробити головним»: the group's card and «Персонажі» take this name (her name, once the novel told it). */
    void makeMain(long editionId, long entryId) {
        requireEntry(editionId, entryId);
        Long old = db.select(GLOSSARY_ENTRY.SAME_AS).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.eq(entryId)).fetchOne(GLOSSARY_ENTRY.SAME_AS);
        if (old == null) {
            return;
        }
        db.transaction(trx -> {
            DSLContext tx = trx.dsl();
            // The old main entry decided whether the character acts in the story; the new one keeps that.
            Boolean inStory = tx.select(GLOSSARY_ENTRY.IN_STORY).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.eq(old)).fetchOne(GLOSSARY_ENTRY.IN_STORY);
            tx.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, entryId)
                    .where(GLOSSARY_ENTRY.SAME_AS.eq(old).and(GLOSSARY_ENTRY.ID.ne(entryId)).or(GLOSSARY_ENTRY.ID.eq(old))).execute();
            tx.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.SAME_AS, (Long) null).set(GLOSSARY_ENTRY.IN_STORY, inStory)
                    .where(GLOSSARY_ENTRY.ID.eq(entryId)).execute();
        });
    }

    /** The team says whether a character acts in the story («Персонажі») or is a monster or only mentioned. */
    void setInStory(long editionId, long entryId, boolean inStory) {
        requireEntry(editionId, entryId);
        db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.IN_STORY, inStory).where(GLOSSARY_ENTRY.ID.eq(entryId)).execute();
    }

    /** For the glossary page: the main entry of another name, and a main entry's other names. */
    record Name(long id, String ukrainian) {
    }

    record Group(Name main, List<Name> others) {
    }

    Map<Long, Group> groupsOf(java.util.Collection<Long> entryIds) {
        Map<Long, Group> out = new java.util.HashMap<>();
        if (entryIds.isEmpty()) {
            return out;
        }
        var rows = db.select(GLOSSARY_ENTRY.ID, GLOSSARY_ENTRY.UKRAINIAN, GLOSSARY_ENTRY.SAME_AS).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.in(entryIds).or(GLOSSARY_ENTRY.SAME_AS.in(entryIds))).fetch();
        List<Long> mainIds = rows.stream().map(r -> r.value3()).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new java.util.HashMap<>();
        if (!mainIds.isEmpty()) {
            db.select(GLOSSARY_ENTRY.ID, GLOSSARY_ENTRY.UKRAINIAN).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.in(mainIds))
                    .forEach(r -> names.put(r.value1(), r.value2()));
        }
        for (long id : entryIds) {
            Long main = rows.stream().filter(r -> r.value1() == id).map(r -> r.value3()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            List<Name> others = rows.stream().filter(r -> java.util.Objects.equals(r.value3(), id)).map(r -> new Name(r.value1(), r.value2())).toList();
            out.put(id, new Group(main == null ? null : new Name(main, names.get(main)), others));
        }
        return out;
    }

    /** All facts of an entry of this novel, in the order the novel tells them. */
    List<Fact> facts(long editionId, long entryId) {
        requireEntry(editionId, entryId);
        return db.select(GLOSSARY_FACT.ID, GLOSSARY_FACT.CHAPTER, GLOSSARY_FACT.FACT, GLOSSARY_FACT.MANUAL).from(GLOSSARY_FACT)
                .where(GLOSSARY_FACT.ENTRY_ID.eq(entryId)).orderBy(GLOSSARY_FACT.CHAPTER, GLOSSARY_FACT.ID)
                .fetch(r -> new Fact(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    /** The team adds a fact by hand, or corrects one. */
    Fact saveFact(long editionId, long entryId, Long factId, int chapter, String text, long accountId) {
        requireEntry(editionId, entryId);
        String fact = text == null ? "" : text.strip();
        if (fact.isEmpty() || fact.length() > FACT_MAX) {
            throw UserFacingException.badRequest("Факт — одне-два речення, до %d знаків.".formatted(FACT_MAX));
        }
        if (chapter < 0) {
            throw UserFacingException.badRequest("Глава — від 0 (відомо з самого початку).");
        }
        if (factId == null) {
            long id = db.insertInto(GLOSSARY_FACT).set(GLOSSARY_FACT.ENTRY_ID, entryId).set(GLOSSARY_FACT.CHAPTER, chapter)
                    .set(GLOSSARY_FACT.FACT, fact).set(GLOSSARY_FACT.MANUAL, true).set(GLOSSARY_FACT.ADDED_BY, accountId)
                    .returning(GLOSSARY_FACT.ID).fetchOne(GLOSSARY_FACT.ID);
            return new Fact(id, chapter, fact, true);
        }
        int changed = db.update(GLOSSARY_FACT).set(GLOSSARY_FACT.CHAPTER, chapter).set(GLOSSARY_FACT.FACT, fact)
                .set(GLOSSARY_FACT.MANUAL, true).set(GLOSSARY_FACT.ADDED_BY, accountId)
                .where(GLOSSARY_FACT.ID.eq(factId), GLOSSARY_FACT.ENTRY_ID.eq(entryId)).execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такого факту немає.");
        }
        return new Fact(factId, chapter, fact, true);
    }

    void deleteFact(long editionId, long entryId, long factId) {
        requireEntry(editionId, entryId);
        db.deleteFrom(GLOSSARY_FACT).where(GLOSSARY_FACT.ID.eq(factId), GLOSSARY_FACT.ENTRY_ID.eq(entryId)).execute();
    }

    private void requireEntry(long editionId, long entryId) {
        if (!db.fetchExists(GLOSSARY_ENTRY, GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId()))) {
            throw UserFacingException.notFound("Такого запису в словнику немає.");
        }
    }

    /** @param original the form exactly as the original writes it */
    /** @param role for a character: acting (in the story), mentioned or creature; null — not said */
    record Proposed(String original, String reading, String ukrainian, String kind, String gender, String note, String role) {
        Proposed(String original, String reading, String ukrainian, String kind, String gender, String note) {
            this(original, reading, ukrainian, kind, gender, note, null);
        }
    }

    /** Analysis says how this language writes a known entry («запис №12 тут — Ryo»). */
    record Known(long id, String original) {
    }

    /** Only numbers analysis was shown: a model may make one up, and it must not tie a stranger's name to an entry. */
    int link(long editionId, List<Known> known) {
        Scope scope = scope(editionId);
        Set<Long> shown = unlinked(editionId).stream().map(Entry::id).collect(java.util.stream.Collectors.toSet());
        int linked = 0;
        for (Known item : known) {
            String original = item.original() == null ? "" : item.original().strip();
            if (original.isEmpty() || original.length() > 100 || !shown.contains(item.id())) {
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
    /** @param looseForms null keeps it as it was */
    void update(long editionId, long entryId, String ukrainian, String kind, String gender, String note, Boolean looseForms) {
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
                .set(GLOSSARY_ENTRY.LOOSE_FORMS, looseForms == null ? GLOSSARY_ENTRY.LOOSE_FORMS : DSL.val(looseForms))
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

    /** One chapter where the Ukrainian form is used: how often and a few places to see it. */
    record Occurrence(int number, String label, String title, int count, List<String> snippets) {
    }

    record Occurrences(String form, int total, List<Occurrence> chapters) {
    }

    private record Published(int number, String label, String title, List<space.panrid.novelka.platform.text.Block> blocks) {
    }

    private List<Published> published(long editionId) {
        return db.select(CHAPTER.NUMBER, CHAPTER.LABEL, REVISION.TITLE, REVISION.BLOCKS).from(CHAPTER)
                .join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(CHAPTER.NUMBER)
                .fetch(r -> new Published(r.value1(), r.value2(), r.value3(), json.readValue(r.value4().data(), BLOCKS)));
    }

    /** Every chapter of the translation that uses the entry's Ukrainian form, in any case (етап 17). */
    Occurrences occurrences(long editionId, long entryId) {
        String form = db.select(GLOSSARY_ENTRY.UKRAINIAN).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.ID.eq(entryId), GLOSSARY_ENTRY.NOVEL_ID.eq(scope(editionId).novelId())).fetchOptional(GLOSSARY_ENTRY.UKRAINIAN)
                .orElseThrow(() -> UserFacingException.notFound("Такого запису немає."));
        return occurrencesOf(editionId, form, looseForms(entryId));
    }

    /** Whether the entry is found as before: any ending, any case. */
    boolean looseForms(long entryId) {
        return Boolean.TRUE.equals(db.select(GLOSSARY_ENTRY.LOOSE_FORMS).from(GLOSSARY_ENTRY).where(GLOSSARY_ENTRY.ID.eq(entryId))
                .fetchOne(GLOSSARY_ENTRY.LOOSE_FORMS));
    }

    Occurrences occurrencesOf(long editionId, String form, boolean loose) {
        List<Occurrence> found = new ArrayList<>();
        int total = 0;
        for (Published chapter : published(editionId)) {
            int count = 0;
            List<String> snippets = new ArrayList<>();
            for (var block : chapter.blocks()) {
                String text = block.text();
                for (int[] at : WordForms.find(text, form, loose)) {
                    count++;
                    if (snippets.size() < 3) {
                        int from = Math.max(0, at[0] - AROUND);
                        int to = Math.min(text.length(), at[1] + AROUND);
                        snippets.add((from > 0 ? "…" : "") + text.substring(from, to).strip() + (to < text.length() ? "…" : ""));
                    }
                }
            }
            if (count > 0) {
                found.add(new Occurrence(chapter.number(), chapter.label() == null ? String.valueOf(chapter.number()) : chapter.label(),
                        chapter.title(), count, snippets));
                total += count;
            }
        }
        return new Occurrences(form, total, found);
    }

    /** Every paragraph of the translation where {@code from} would become {@code to}, as it would read. */
    List<space.panrid.novelka.suggestion.Suggestions.BlockChange> rewrite(long editionId, String from, String to) {
        return rewrite(editionId, from, to, false, new java.util.HashMap<>());
    }

    /** Paragraphs that name the form, as they are, for a model to look through (gender, етап 17). */
    List<space.panrid.novelka.suggestion.Suggestions.BlockChange> naming(long editionId, String form, boolean loose) {
        List<space.panrid.novelka.suggestion.Suggestions.BlockChange> out = new ArrayList<>();
        for (Published chapter : published(editionId)) {
            for (var block : chapter.blocks()) {
                if (block.content() != null && block.content().size() == 1 && !WordForms.find(block.text(), form, loose).isEmpty()) {
                    out.add(new space.panrid.novelka.suggestion.Suggestions.BlockChange(chapter.number(), block.id(), block.content()));
                }
            }
        }
        return out;
    }

    /** @param originals filled with each changed paragraph's text before the change, by «chapter:block» */
    List<space.panrid.novelka.suggestion.Suggestions.BlockChange> rewrite(long editionId, String from, String to, boolean loose,
            Map<String, String> originals) {
        List<space.panrid.novelka.suggestion.Suggestions.BlockChange> changes = new ArrayList<>();
        if (from == null || from.isBlank() || to == null || to.isBlank() || from.strip().equals(to.strip())) {
            return changes;
        }
        for (Published chapter : published(editionId)) {
            for (var block : chapter.blocks()) {
                if (block.content() == null || WordForms.find(block.text(), from, loose).isEmpty()) {
                    continue;
                }
                List<space.panrid.novelka.platform.text.Span> spans = block.content().stream()
                        .map(span -> new space.panrid.novelka.platform.text.Span(WordForms.replace(span.text(), from, to, loose), span.marks()))
                        .toList();
                if (!spans.equals(block.content())) {
                    changes.add(new space.panrid.novelka.suggestion.Suggestions.BlockChange(chapter.number(), block.id(), spans));
                    originals.put(chapter.number() + ":" + block.id(), block.text());
                }
            }
        }
        return changes;
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
                row.get(GLOSSARY_ENTRY.SOURCE_CHAPTER), row.get(GLOSSARY_ENTRY.MANUAL), row.get(GLOSSARY_ENTRY.STATUS),
                row.get(GLOSSARY_ENTRY.LOOSE_FORMS), row.get(GLOSSARY_ENTRY.SAME_AS), row.get(GLOSSARY_ENTRY.IN_STORY));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
