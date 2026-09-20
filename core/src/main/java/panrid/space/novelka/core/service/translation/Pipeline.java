package panrid.space.novelka.core.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import panrid.space.novelka.core.integration.ai.AiClient;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Segment;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.service.glossary.Dictionary;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.core.support.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static panrid.space.novelka.core.support.Hashes.hash;

public final class Pipeline {
    private final DatabaseSession database;
    private final AiClient ai;
    private final int segmentChars;
    private final double usdPer5000Tokens;

    public Pipeline(DatabaseSession database, AiClient ai, int segmentChars) {
        this(database, ai, segmentChars, 0.10);
    }

    public Pipeline(DatabaseSession database, AiClient ai, int segmentChars, double usdPer5000Tokens) {
        if (!Double.isFinite(usdPer5000Tokens) || usdPer5000Tokens < 0)
            throw new IllegalArgumentException("Invalid USD rate per 5000 tokens");
        this.database = database;
        this.ai = ai;
        this.segmentChars = segmentChars;
        this.usdPer5000Tokens = usdPer5000Tokens;
    }

    public Work create(String novel, int chapter, boolean force) throws Exception {
        Chapter ch = database.chapters().chapter(novel, chapter);
        Work old = database.jobs().latest(novel, chapter);
        String hash = hash(ch.blocks());
        if (old != null && !force) {
            if (!old.sourceHash().equals(hash))
                throw new IllegalStateException(
                        "Source changed; use --force for a new translation revision");
            return old;
        }
        var segments =
                Segments.split(ch.blocks(), segmentChars).stream()
                        .map(b -> new Segment(b, List.of(), List.of(), "", "pending"))
                        .toList();
        Work work =
                new Work(
                        UUID.randomUUID().toString(),
                        novel,
                        chapter,
                        hash,
                        old == null ? 1 : old.revision() + 1,
                        segments,
                        "pending",
                        "");
        database.transaction(
                () -> {
                    database.jobs().save(work);
                    int tokens = Tokens.source(ch.blocks());
                    database.jobs().recordMetrics(work.id(), tokens, usdPer5000Tokens);
                    return null;
                });
        return work;
    }

    public Work run(Work work, double budget) throws Exception {
        if (work.state().equals("complete")) return work;
        if (work.state().equals("needs-review"))
            throw new IllegalStateException("Dictionary changed; run proofread or translate --force");
        var segments = new ArrayList<>(work.segments());
        String previous = "";
        var prior = database.jobs().latest(work.novelId(), work.chapter() - 1);
        if (prior != null && prior.state().equals("complete")) previous = prior.summary();
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (s.state().equals("complete")) {
                previous = s.context();
                continue;
            }
            String adjacent =
                    i == 0
                            ? ""
                            : Json.write(
                            segments
                                    .get(i - 1)
                                    .source()
                                    .subList(
                                            Math.max(0, segments.get(i - 1).source().size() - 2),
                                            segments.get(i - 1).source().size()));
            if (s.state().equals("pending")) {
                var g = database.glossaries().glossary(work.novelId());
                JsonNode analysis =
                        ai.generate(
                                work,
                                i,
                                "analyze",
                                g,
                                Map.of(
                                        "source",
                                        s.source(),
                                        "chapter",
                                        work.chapter(),
                                        "context",
                                        previous,
                                        "adjacent",
                                        adjacent),
                                budget);
                work = applyAnalysis(work, i, segments, g, analysis, previous);
                s = segments.get(i);
            }
            if (s.state().equals("analyzed")) {
                var result =
                        ai.generate(
                                work,
                                i,
                                "translate",
                                database.glossaries().glossary(work.novelId()),
                                Map.of("source", s.source(), "context", previous, "adjacent", adjacent),
                                budget);
                var draft = blocks(result);
                Segments.validate(s.source(), draft);
                s = new Segment(s.source(), draft, List.of(), "", "translated");
                segments.set(i, s);
                work = save(work, segments, "running", previous);
            }
            if (s.state().equals("translated")) {
                var result =
                        ai.generate(
                                work,
                                i,
                                "proofread",
                                database.glossaries().glossary(work.novelId()),
                                Map.of(
                                        "source",
                                        s.source(),
                                        "draft",
                                        s.draft(),
                                        "context",
                                        previous,
                                        "adjacent",
                                        adjacent),
                                budget);
                var revised = blocks(result);
                Segments.validate(s.source(), revised);
                String summary = result.path("summary").asText();
                if (summary.length() > 1000) summary = summary.substring(0, 1000);
                s = new Segment(s.source(), s.draft(), revised, summary, "complete");
                segments.set(i, s);
                previous = summary;
                work = save(work, segments, "running", previous);
            }
        }
        return save(work, segments, "complete", previous);
    }

    private Work applyAnalysis(
            Work work, int index, List<Segment> segments, Glossary g, JsonNode analysis, String previous)
            throws Exception {
        return database.transaction(
                () -> {
                    if (!analysis.path("entries").isArray() || analysis.path("entries").size() > 30)
                        throw new IllegalArgumentException("Invalid glossary analysis");
                    var entries = new ArrayList<>(g.entries());
                    for (var node : analysis.path("entries")) {
                        Entry e;
                        try {
                            e = Json.decode(node.toString(), Entry.class);
                            // A malformed proposal is reviewable data, never grounds to overwrite a canonical
                            // fact.
                            Dictionary.validate(e);
                        } catch (IllegalArgumentException error) {
                            database.glossaries().propose(work.novelId(), work.id(),
                                    Map.of("rejected", node, "validationError", error.getMessage()));
                            System.err.println(
                                    "Invalid dictionary proposal retained for review; canonical dictionary"
                                            + " unchanged.");
                            continue;
                        }
                        e =
                                new Entry(
                                        e.key(),
                                        e.kind(),
                                        e.japanese(),
                                        e.reading(),
                                        e.ukrainian(),
                                        e.aliases(),
                                        e.gender(),
                                        e.facts(),
                                        e.certainty(),
                                        work.chapter(),
                                        false);
                        database.glossaries().propose(work.novelId(), work.id(), e);
                        Entry proposal = e;
                        // Existing facts are never silently overwritten. Conflicting proposals remain
                        // reviewable.
                        if (entries.stream().noneMatch(x -> Dictionary.sameEntity(x, proposal))) entries.add(e);
                    }
                    if (!entries.equals(g.entries()))
                        database.glossaryService().update(work.novelId(), new Glossary(g.revision() + 1, List.copyOf(entries)));
                    Segment source = segments.get(index);
                    segments.set(
                            index,
                            new Segment(
                                    source.source(), source.draft(), source.revised(), source.context(), "analyzed"));
                    return save(work, segments, "running", previous);
                });
    }

    public Work proofread(Work old) throws Exception {
        var latest = database.jobs().latest(old.novelId(), old.chapter());
        var segments =
                old.segments().stream()
                        .map(
                                s -> {
                                    if (s.draft().isEmpty()) throw new IllegalStateException("Translate first");
                                    return new Segment(
                                            s.source(),
                                            s.revised().isEmpty() ? s.draft() : s.revised(),
                                            List.of(),
                                            "",
                                            "translated");
                                })
                        .toList();
        Work w =
                new Work(
                        UUID.randomUUID().toString(),
                        old.novelId(),
                        old.chapter(),
                        old.sourceHash(),
                        latest.revision() + 1,
                        segments,
                        "pending",
                        "");
        database.transaction(
                () -> {
                    database.jobs().save(w);
                    int tokens =
                            w.segments().stream().mapToInt(segment -> Tokens.source(segment.source())).sum();
                    database.jobs().recordMetrics(w.id(), tokens, usdPer5000Tokens);
                    return null;
                });
        return w;
    }

    private Work save(Work w, List<Segment> s, String state, String summary) throws Exception {
        Work next =
                new Work(
                        w.id(),
                        w.novelId(),
                        w.chapter(),
                        w.sourceHash(),
                        w.revision(),
                        List.copyOf(s),
                        state,
                        summary);
        database.jobs().save(next);
        return next;
    }

    private List<Block> blocks(JsonNode node) {
        if (!node.path("blocks").isArray())
            throw new IllegalArgumentException("Response has no blocks");
        var result = new ArrayList<Block>();
        for (var b : node.path("blocks")) result.add(Json.decode(b.toString(), Block.class));
        return result;
    }
}
