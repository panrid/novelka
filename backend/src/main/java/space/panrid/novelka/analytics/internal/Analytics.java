package space.panrid.novelka.analytics.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the analytics report. It reads across the modules' tables in plain SQL: these are
 * reports with aggregates, window functions and JSON paths, clearer as SQL than as jOOQ chains.
 */
@Component
class Analytics {

    /** What a call cost: the price OpenRouter reported, or the estimate when the answer may have been paid but was lost. */
    private static final String COST = """
            (case when c.state = 'complete' then coalesce(c.cost_actual_musd, c.cost_estimated_musd)
                  when c.state = 'uncertain' then coalesce(c.cost_actual_musd, c.cost_estimated_musd)
                  else coalesce(c.cost_actual_musd, 0) end)""";
    private static final String SECONDS = "extract(epoch from (c.finished_at - c.created_at))";
    private static final List<String> STAGES = List.of("analyze", "translate", "proofread");
    private static final Pattern IDS = Pattern.compile("\\b[sbp]?\\d+\\b");

    private final DSLContext db;
    private final JsonMapper json;
    private final Clock clock;

    Analytics(DSLContext db, JsonMapper json, Clock clock) {
        this.db = db;
        this.json = json;
        this.clock = clock;
    }

    /** @param days 7, 30, 90 or 365 — or 0 for all time */
    @Transactional(readOnly = true)
    Report report(int days) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime from = days > 0 ? now.minusDays(days) : OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime first = days > 0 ? from : firstCall().orElse(now.minusDays(30));
        String bucket = days > 0 && days <= 90 ? "day" : "week";

        List<Run> runs = runs(from);
        Map<Long, Settings> settings = settings(runs.stream().map(Run::jobId).collect(Collectors.toSet()));
        return new Report(new Report.Period(days, first.toLocalDate().toString(), bucket),
                spend(from, now, days, runs),
                spendSeries(from, bucket),
                stages(from),
                models(from),
                combos(runs, settings),
                novels(from),
                funding(from),
                translation(from, runs, settings),
                proofread(from),
                analysis(from, runs, settings),
                reasons(from),
                site(from, bucket));
    }

    private java.util.Optional<OffsetDateTime> firstCall() {
        return db.resultQuery("select min(created_at) from ai_call").fetchOptional(0, OffsetDateTime.class);
    }

    // ---- money ----------------------------------------------------------------------------------

    private Report.Spend spend(OffsetDateTime from, OffsetDateTime now, int days, List<Run> runs) {
        Record r = db.resultQuery("""
                select coalesce(sum(%s), 0) musd, count(*) calls,
                       count(*) filter (where c.state = 'failed') failed,
                       count(*) filter (where c.state = 'uncertain') uncertain,
                       coalesce(sum(c.tokens_in), 0) tin, coalesce(sum(c.tokens_out), 0) tout,
                       sum(c.cost_estimated_musd) filter (where c.cost_actual_musd is not null) estimated,
                       sum(c.cost_actual_musd) actual
                from ai_call c where c.created_at >= cast(? as timestamptz)""".formatted(COST), from).fetchOne();
        double previous = 0;
        if (days > 0) {
            previous = usd(db.resultQuery("select coalesce(sum(%s), 0) from ai_call c where c.created_at >= cast(? as timestamptz) and c.created_at < cast(? as timestamptz)"
                    .formatted(COST), from.minusDays(days), from).fetchOne(0, Long.class));
        }
        int calls = r.get("calls", Integer.class);
        double usd = usd(r.get("musd", Long.class));
        Long estimated = r.get("estimated", Long.class);
        Long actual = r.get("actual", Long.class);
        int chapters = (int) runs.stream().filter(Run::translated).count();
        return new Report.Spend(usd, previous, calls, r.get("failed", Integer.class), r.get("uncertain", Integer.class),
                r.get("tin", Long.class), r.get("tout", Long.class), calls == 0 ? 0 : usd / calls,
                estimated == null || estimated == 0 || actual == null ? null : (double) actual / estimated,
                chapters, chapters == 0 ? 0 : runs.stream().filter(Run::translated).mapToDouble(Run::usd).sum() / chapters);
    }

    private List<Report.Bucket> spendSeries(OffsetDateTime from, String bucket) {
        Map<LocalDate, Map<String, Double>> byStage = new TreeMap<>();
        Map<LocalDate, Integer> calls = new HashMap<>();
        db.resultQuery("""
                select date_trunc('%s', c.created_at at time zone 'UTC')::date bucket, c.stage, sum(%s) musd, count(*) calls
                from ai_call c where c.created_at >= cast(? as timestamptz) group by 1, 2 order by 1""".formatted(bucket, COST), from)
                .forEach(r -> {
                    LocalDate start = r.get("bucket", LocalDate.class);
                    byStage.computeIfAbsent(start, d -> new LinkedHashMap<>()).merge(stageGroup(r.get("stage", String.class)),
                            usd(r.get("musd", Long.class)), Double::sum);
                    calls.merge(start, r.get("calls", Integer.class), Integer::sum);
                });
        return byStage.entrySet().stream().map(e -> new Report.Bucket(e.getKey(), e.getValue(), calls.get(e.getKey()))).toList();
    }

    private List<Report.StageRow> stages(OffsetDateTime from) {
        List<Record> rows = db.resultQuery("""
                select c.stage, sum(%s) musd, count(*) calls, coalesce(sum(c.tokens_in), 0) tin, coalesce(sum(c.tokens_out), 0) tout
                from ai_call c where c.created_at >= cast(? as timestamptz) group by 1 order by 2 desc""".formatted(COST), from).fetch();
        double total = rows.stream().mapToDouble(r -> usd(r.get("musd", Long.class))).sum();
        return rows.stream().map(r -> new Report.StageRow(r.get("stage", String.class), usd(r.get("musd", Long.class)),
                r.get("calls", Integer.class), total == 0 ? 0 : usd(r.get("musd", Long.class)) / total,
                r.get("tin", Long.class), r.get("tout", Long.class))).toList();
    }

    private List<Report.ModelRow> models(OffsetDateTime from) {
        return db.resultQuery("""
                select c.model, c.stage, count(*) calls, count(*) filter (where c.state in ('failed', 'uncertain')) failed,
                       coalesce(sum(c.tokens_in), 0) tin, coalesce(sum(c.tokens_out), 0) tout, sum(%1$s) musd,
                       avg(%2$s) filter (where c.finished_at is not null) average,
                       percentile_cont(0.9) within group (order by %2$s) filter (where c.finished_at is not null) p90
                from ai_call c where c.created_at >= cast(? as timestamptz) group by 1, 2 order by 7 desc""".formatted(COST, SECONDS), from)
                .fetch(r -> {
                    long tokens = r.get("tin", Long.class) + r.get("tout", Long.class);
                    double usd = usd(r.get("musd", Long.class));
                    long in = r.get("tin", Long.class);
                    return new Report.ModelRow(r.get("model", String.class), r.get("stage", String.class),
                            r.get("calls", Integer.class), r.get("failed", Integer.class), in, r.get("tout", Long.class), usd,
                            r.get("average", Double.class), r.get("p90", Double.class),
                            tokens == 0 ? null : usd / tokens * 1_000_000, in == 0 ? null : (double) r.get("tout", Long.class) / in);
                });
    }

    private List<Report.Combo> combos(List<Run> runs, Map<Long, Settings> settings) {
        Map<List<String>, List<Run>> groups = new LinkedHashMap<>();
        for (Run run : runs) {
            Settings s = settings.get(run.jobId());
            if (s != null && run.translated()) {
                groups.computeIfAbsent(List.of(s.analyze(), s.translate(), s.proofread()), k -> new ArrayList<>()).add(run);
            }
        }
        return groups.entrySet().stream().map(e -> {
            List<Run> group = e.getValue();
            double usd = group.stream().mapToDouble(Run::usd).sum();
            long chars = group.stream().mapToLong(Run::chars).sum();
            Map<String, Double> byStage = new LinkedHashMap<>();
            for (String stage : STAGES) {
                byStage.put(stage, group.stream().mapToDouble(run -> run.byStage().getOrDefault(stage, 0.0)).sum() / group.size());
            }
            return new Report.Combo(e.getKey().get(0), e.getKey().get(1), e.getKey().get(2), group.size(), usd,
                    usd / group.size(), chars == 0 ? null : usd / chars * 1000,
                    group.stream().mapToDouble(Run::seconds).average().orElse(0), byStage);
        }).sorted(Comparator.comparingInt(Report.Combo::chapters).reversed()).toList();
    }

    private List<Report.NovelCost> novels(OffsetDateTime from) {
        return db.resultQuery("""
                select coalesce(e.title, n.title) title, n.slug,
                       count(distinct (c.job_id, c.chapter_number)) filter (where c.stage = 'translate') chapters,
                       sum(%1$s) musd,
                       sum(%1$s) filter (where c.stage = 'analyze') analyze,
                       sum(%1$s) filter (where c.stage = 'translate') translate,
                       sum(%1$s) filter (where c.stage = 'proofread') proofread,
                       sum(%1$s) filter (where c.stage not in ('analyze', 'translate', 'proofread')) other
                from ai_call c join job j on j.id = c.job_id join edition e on e.id = j.edition_id join novel n on n.id = e.novel_id
                where c.created_at >= cast(? as timestamptz) group by 1, 2 order by 4 desc""".formatted(COST), from)
                .fetch(r -> {
                    int chapters = r.get("chapters", Integer.class);
                    double usd = usd(r.get("musd", Long.class));
                    Map<String, Double> byStage = new LinkedHashMap<>();
                    for (String stage : List.of("analyze", "translate", "proofread", "other")) {
                        byStage.put(stage, usd(r.get(stage, Long.class)));
                    }
                    return new Report.NovelCost(r.get("title", String.class), r.get("slug", String.class), chapters, usd,
                            chapters == 0 ? 0 : usd / chapters, byStage);
                });
    }

    private Report.Funding funding(OffsetDateTime from) {
        Record r = db.resultQuery("""
                select coalesce(sum(%s) filter (where j.funding = 'site'), 0) site,
                       coalesce(sum(%1$s) filter (where j.funding in ('team', 'account')), 0) people,
                       coalesce(sum(%1$s) filter (where c.job_id is null), 0) outside
                from ai_call c left join job j on j.id = c.job_id where c.created_at >= cast(? as timestamptz)""".formatted(COST), from).fetchOne();
        Record charged = db.resultQuery("""
                select coalesce(sum(j.charged_shah), 0) shah,
                       coalesce(sum(j.charged_shah * coalesce((j.settings ->> 'microUsdPerShah')::bigint, 0)), 0) musd
                from job j where j.created_at >= cast(? as timestamptz)""", from).fetchOne();
        return new Report.Funding(usd(r.get("site", Long.class)), usd(r.get("people", Long.class)),
                usd(r.get("outside", Long.class)), charged.get("shah", Integer.class), usd(charged.get("musd", Long.class)));
    }

    // ---- quality ----------------------------------------------------------------------------------

    private List<Report.TranslationRow> translation(OffsetDateTime from, List<Run> runs, Map<Long, Settings> known) {
        // The latest machine version of each chapter translated in the period, and the text readers see now.
        List<Record> chapters = db.resultQuery("""
                select distinct on (r.chapter_id) r.chapter_id, r.job_id, r.created_at, r.blocks::text ai_blocks,
                       p.blocks::text current_blocks, ch.number
                from revision r join chapter ch on ch.id = r.chapter_id
                     left join revision p on p.id = ch.published_revision_id
                where r.origin = 'ai' and r.job_id is not null and r.created_at >= cast(? as timestamptz)
                order by r.chapter_id, r.created_at desc""", from).fetch();
        Map<Long, Settings> settings = new HashMap<>(known);
        settings.putAll(settings(chapters.stream().map(r -> r.get("job_id", Long.class))
                .filter(id -> !known.containsKey(id)).collect(Collectors.toSet())));
        Map<Long, int[]> edits = new HashMap<>();
        db.resultQuery("""
                select a.chapter_id,
                       count(distinct h.id) filter (where h.origin = 'editor') editor,
                       count(distinct h.id) filter (where h.origin = 'suggestion') applied,
                       (select count(*) from suggestion s where s.chapter_id = a.chapter_id and s.created_at > a.created_at) suggestions,
                       (select count(*) from suggestion s where s.chapter_id = a.chapter_id and s.created_at > a.created_at and s.state = 'accepted') accepted,
                       (select count(*) from suggestion s where s.chapter_id = a.chapter_id and s.created_at > a.created_at and s.state = 'rejected') rejected
                from (select distinct on (chapter_id) chapter_id, created_at from revision
                      where origin = 'ai' and job_id is not null and created_at >= cast(? as timestamptz) order by chapter_id, created_at desc) a
                     left join revision h on h.chapter_id = a.chapter_id and h.created_at > a.created_at and h.origin <> 'ai'
                group by a.chapter_id, a.created_at""", from)
                .forEach(r -> edits.put(r.get("chapter_id", Long.class), new int[] {r.get("editor", Integer.class),
                        r.get("applied", Integer.class), r.get("suggestions", Integer.class), r.get("accepted", Integer.class),
                        r.get("rejected", Integer.class)}));
        Map<String, int[]> events = events(from);
        Map<Long, Integer> failedSteps = new HashMap<>();
        db.resultQuery("select job_id, count(*) n from job_step where state = 'failed' and updated_at >= cast(? as timestamptz) group by 1", from)
                .forEach(r -> failedSteps.put(r.get("job_id", Long.class), r.get("n", Integer.class)));
        Map<String, Run> runByChapter = runs.stream().collect(Collectors.toMap(run -> run.jobId() + ":" + run.chapter(),
                Function.identity(), (a, b) -> a));

        Map<List<String>, Totals> rows = new LinkedHashMap<>();
        Map<List<String>, Set<Long>> jobsOf = new HashMap<>();
        for (Record chapter : chapters) {
            long jobId = chapter.get("job_id", Long.class);
            Settings s = settings.get(jobId);
            if (s == null) {
                continue;
            }
            List<String> key = List.of(s.translate(), s.proofread());
            Totals t = rows.computeIfAbsent(key, k -> new Totals());
            jobsOf.computeIfAbsent(key, k -> new HashSet<>()).add(jobId);
            int[] e = edits.getOrDefault(chapter.get("chapter_id", Long.class), new int[5]);
            t.chapters++;
            t.edited += e[0] + e[1] > 0 ? 1 : 0;
            t.editor += e[0];
            t.suggestions += e[2];
            t.accepted += e[3];
            t.rejected += e[4];
            double[] changed = changed(chapter.get("ai_blocks", String.class), chapter.get("current_blocks", String.class));
            t.paragraphs += changed[0];
            t.words += changed[1];
            String at = jobId + ":" + chapter.get("number", Integer.class);
            int[] ev = events.getOrDefault(at, new int[6]);
            t.retries += ev[0];
            t.splits += ev[1];
            t.missing += ev[2];
            Run run = runByChapter.get(at);
            if (run != null) {
                t.costed++;
                t.usd += run.usd();
                t.seconds += run.seconds();
            }
        }
        return rows.entrySet().stream().map(e -> {
            Totals t = e.getValue();
            int failed = jobsOf.get(e.getKey()).stream().mapToInt(id -> failedSteps.getOrDefault(id, 0)).sum();
            return new Report.TranslationRow(e.getKey().get(0), e.getKey().get(1), t.chapters, t.edited,
                    share(t.edited, t.chapters), t.editor, t.suggestions, t.accepted, t.rejected,
                    per(t.suggestions, t.chapters), per(t.accepted, t.chapters), t.paragraphs / t.chapters, t.words / t.chapters,
                    per(t.retries, t.chapters), per(t.splits, t.chapters), per(t.missing, t.chapters), failed,
                    t.costed == 0 ? null : t.usd / t.costed, t.costed == 0 ? null : t.seconds / t.costed);
        }).sorted(Comparator.comparingInt(Report.TranslationRow::chapters).reversed()).toList();
    }

    /** Per «job:chapter»: translation retries, splits, missing lines, proofread changes, proofread skips, proofread parts. */
    private Map<String, int[]> events(OffsetDateTime from) {
        Map<String, int[]> out = new HashMap<>();
        db.resultQuery("""
                select job_id, chapter_number,
                       count(*) filter (where kind = 'retry' and coalesce(payload ->> 'stage', 'translate') = 'translate') retries,
                       count(*) filter (where kind = 'split') splits,
                       count(*) filter (where kind = 'missing') missing
                from job_event where created_at >= cast(? as timestamptz) group by 1, 2""", from)
                .forEach(r -> out.put(r.get("job_id", Long.class) + ":" + r.get("chapter_number", Integer.class),
                        new int[] {r.get("retries", Integer.class), r.get("splits", Integer.class), r.get("missing", Integer.class)}));
        return out;
    }

    private List<Report.ProofreadRow> proofread(OffsetDateTime from) {
        return db.resultQuery("""
                select j.settings -> 'proofread' ->> 'model' model,
                       count(*) filter (where e.kind = 'proofread') parts,
                       coalesce(sum(jsonb_array_length(coalesce(e.payload -> 'changes', '[]'::jsonb))) filter (where e.kind = 'proofread'), 0) changed,
                       count(*) filter (where e.kind = 'proofread_skipped') skipped,
                       count(*) filter (where e.kind = 'retry' and e.payload ->> 'stage' = 'proofread') retries
                from job_event e join job j on j.id = e.job_id
                where e.created_at >= cast(? as timestamptz) and e.kind in ('proofread', 'proofread_skipped', 'retry')
                group by 1 having count(*) filter (where e.kind in ('proofread', 'proofread_skipped')) > 0
                order by 2 desc""", from)
                .fetch(r -> {
                    int parts = r.get("parts", Integer.class);
                    int skipped = r.get("skipped", Integer.class);
                    int changed = r.get("changed", Integer.class);
                    return new Report.ProofreadRow(Objects.requireNonNullElse(r.get("model", String.class), "—"), parts, changed,
                            per(changed, parts), skipped, share(skipped, parts + skipped), per(r.get("retries", Integer.class), parts + skipped));
                });
    }

    private List<Report.AnalysisRow> analysis(OffsetDateTime from, List<Run> runs, Map<Long, Settings> settings) {
        Map<String, int[]> titles = new LinkedHashMap<>();
        db.resultQuery("""
                select j.settings -> 'analyze' ->> 'model' model, count(*) chapters, count(*) filter (where ca.edited) edited
                from chapter_analysis ca join job j on j.id = ca.job_id where ca.updated_at >= cast(? as timestamptz) group by 1 order by 2 desc""", from)
                .forEach(r -> titles.put(Objects.requireNonNullElse(r.get("model", String.class), "—"),
                        new int[] {r.get("chapters", Integer.class), r.get("edited", Integer.class)}));
        // Glossary entries by the model that analysed the chapter they came from.
        Map<String, int[]> entries = new HashMap<>();
        db.resultQuery("""
                select model, status, manual, count(*) n from (
                    select distinct on (g.id) g.id, g.status, g.manual, j.settings -> 'analyze' ->> 'model' model
                    from glossary_entry g join edition e on e.novel_id = g.novel_id
                         join chapter_analysis ca on ca.edition_id = e.id and ca.number = g.source_chapter
                         join job j on j.id = ca.job_id
                    where g.source_chapter is not null and ca.updated_at >= cast(? as timestamptz)
                    order by g.id, ca.updated_at) x
                group by 1, 2, 3""", from)
                .forEach(r -> {
                    int[] counts = entries.computeIfAbsent(Objects.requireNonNullElse(r.get("model", String.class), "—"), m -> new int[4]);
                    int n = r.get("n", Integer.class);
                    String status = r.get("status", String.class);
                    if ("rejected".equals(status)) {
                        counts[2] += n;
                    } else if ("new".equals(status)) {
                        counts[3] += n;
                    } else if (Boolean.TRUE.equals(r.get("manual", Boolean.class))) {
                        counts[1] += n;
                    } else {
                        counts[0] += n;
                    }
                });
        Map<String, double[]> cost = new HashMap<>();
        for (Run run : runs) {
            Settings s = settings.get(run.jobId());
            double analyze = run.byStage().getOrDefault("analyze", 0.0);
            if (s != null && analyze > 0) {
                double[] c = cost.computeIfAbsent(s.analyze(), m -> new double[2]);
                c[0] += analyze;
                c[1]++;
            }
        }
        Map<String, Integer> retries = new HashMap<>();
        db.resultQuery("""
                select j.settings -> 'analyze' ->> 'model' model, count(*) n from job_event e join job j on j.id = e.job_id
                where e.kind = 'retry' and e.payload ->> 'stage' = 'analyze' and e.created_at >= cast(? as timestamptz) group by 1""", from)
                .forEach(r -> retries.put(Objects.requireNonNullElse(r.get("model", String.class), "—"), r.get("n", Integer.class)));
        Set<String> models = new java.util.LinkedHashSet<>(titles.keySet());
        models.addAll(entries.keySet());
        List<Report.AnalysisRow> out = new ArrayList<>();
        for (String model : models) {
            int[] t = titles.getOrDefault(model, new int[2]);
            int[] g = entries.getOrDefault(model, new int[4]);
            int all = g[0] + g[1] + g[2] + g[3];
            double[] c = cost.get(model);
            out.add(new Report.AnalysisRow(model, t[0], all, g[0], g[1], g[2], g[3], per(all, t[0]), share(g[2], all - g[3]),
                    t[1], share(t[1], t[0]), c == null ? null : c[0] / c[1], per(retries.getOrDefault(model, 0), t[0])));
        }
        return out;
    }

    private List<Report.Reason> reasons(OffsetDateTime from) {
        Map<List<String>, Integer> counts = new HashMap<>();
        db.resultQuery("""
                select e.kind, coalesce(e.payload ->> 'stage', 'translate') stage,
                       j.settings -> coalesce(e.payload ->> 'stage', 'translate') ->> 'model' model,
                       coalesce(e.payload ->> 'reason', '') reason
                from job_event e join job j on j.id = e.job_id
                where e.kind in ('retry', 'split', 'proofread_skipped', 'missing') and e.created_at >= cast(? as timestamptz)""", from)
                .forEach(r -> {
                    String reason = IDS.matcher(r.get("reason", String.class)).replaceAll("N").strip();
                    counts.merge(List.of(r.get("stage", String.class), Objects.requireNonNullElse(r.get("model", String.class), "—"),
                            r.get("kind", String.class), reason), 1, Integer::sum);
                });
        return counts.entrySet().stream()
                .map(e -> new Report.Reason(e.getKey().get(0), e.getKey().get(1), e.getKey().get(2), e.getKey().get(3), e.getValue()))
                .sorted(Comparator.comparingInt(Report.Reason::count).reversed()).limit(30).toList();
    }

    // ---- the site -------------------------------------------------------------------------------

    private Report.Site site(OffsetDateTime from, String bucket) {
        Record r = db.resultQuery("""
                select (select count(*) from account where created_at >= cast(? as timestamptz)) accounts,
                       (select count(*) from account where last_seen_at >= cast(? as timestamptz)) active,
                       (select count(distinct account_id) from reading_progress where updated_at >= cast(? as timestamptz)) readers,
                       (select count(*) from chapter where first_published_at >= cast(? as timestamptz)) chapters,
                       (select count(*) from chapter ch join edition e on e.id = ch.edition_id
                        where ch.first_published_at >= cast(? as timestamptz) and e.kind in ('machine', 'mixed')) machine,
                       (select count(*) from comment where created_at >= cast(? as timestamptz) and deleted_at is null) comments,
                       (select count(*) from suggestion where created_at >= cast(? as timestamptz) and state <> 'draft') suggestions,
                       (select count(*) from suggestion where reviewed_at >= cast(? as timestamptz) and state = 'accepted') accepted,
                       (select count(*) from suggestion where reviewed_at >= cast(? as timestamptz) and state = 'rejected') rejected,
                       (select count(*) from library_entry where updated_at >= cast(? as timestamptz)) library""",
                from, from, from, from, from, from, from, from, from, from).fetchOne();
        Map<LocalDate, int[]> series = new TreeMap<>();
        String trunc = "date_trunc('%s', %%s at time zone 'UTC')::date".formatted(bucket);
        series(series, 0, "select %s d, count(*) n from account where created_at >= cast(? as timestamptz) group by 1".formatted(trunc.formatted("created_at")), from);
        series(series, 1, "select %s d, count(*) n from chapter where first_published_at >= cast(? as timestamptz) group by 1"
                .formatted(trunc.formatted("first_published_at")), from);
        series(series, 2, "select %s d, count(*) n from comment where created_at >= cast(? as timestamptz) and deleted_at is null group by 1"
                .formatted(trunc.formatted("created_at")), from);
        series(series, 3, "select %s d, count(*) n from suggestion where created_at >= cast(? as timestamptz) and state <> 'draft' group by 1"
                .formatted(trunc.formatted("created_at")), from);
        List<Report.TopEdition> top = db.resultQuery("""
                select coalesce(e.title, n.title) title, n.slug, t.handle team, e.chapter_count chapters,
                       (select count(distinct p.account_id) from reading_progress p where p.edition_id = e.id and p.updated_at >= cast(? as timestamptz)) readers,
                       (select count(*) from library_entry l where l.edition_id = e.id) library
                from edition e join novel n on n.id = e.novel_id join team t on t.id = e.team_id
                where e.chapter_count > 0 order by 5 desc, 6 desc limit 10""", from)
                .fetch(t -> new Report.TopEdition(t.get("title", String.class), t.get("slug", String.class), t.get("team", String.class),
                        t.get("readers", Integer.class), t.get("library", Integer.class), t.get("chapters", Integer.class)));
        return new Report.Site(r.get("accounts", Integer.class), r.get("active", Integer.class), r.get("readers", Integer.class),
                r.get("chapters", Integer.class), r.get("machine", Integer.class), r.get("comments", Integer.class),
                r.get("suggestions", Integer.class), r.get("accepted", Integer.class), r.get("rejected", Integer.class),
                r.get("library", Integer.class),
                series.entrySet().stream().map(e -> new Report.SiteBucket(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3])).toList(),
                top);
    }

    private void series(Map<LocalDate, int[]> series, int index, String sql, OffsetDateTime from) {
        db.resultQuery(sql, from).forEach(r -> series.computeIfAbsent(r.get("d", LocalDate.class), d -> new int[4])[index] =
                r.get("n", Integer.class));
    }

    // ---- shared ------------------------------------------------------------------------------------

    /** What the models did for one chapter in one run, with its cost by step. */
    record Run(long jobId, int chapter, long chars, double usd, Map<String, Double> byStage, double seconds, boolean translated) {
    }

    private List<Run> runs(OffsetDateTime from) {
        return db.resultQuery("""
                select c.job_id, c.chapter_number, coalesce(max(ch.source_chars), 0) chars, sum(%1$s) musd,
                       sum(%1$s) filter (where c.stage = 'analyze') analyze,
                       sum(%1$s) filter (where c.stage = 'translate') translate,
                       sum(%1$s) filter (where c.stage = 'proofread') proofread,
                       coalesce(sum(%2$s), 0) seconds, bool_or(c.stage = 'translate') translated
                from ai_call c join job j on j.id = c.job_id
                     left join chapter ch on ch.edition_id = j.edition_id and ch.number = c.chapter_number
                where c.created_at >= cast(? as timestamptz) and c.chapter_number is not null
                group by 1, 2""".formatted(COST, SECONDS), from)
                .fetch(r -> {
                    Map<String, Double> byStage = new HashMap<>();
                    for (String stage : STAGES) {
                        byStage.put(stage, usd(r.get(stage, Long.class)));
                    }
                    return new Run(r.get("job_id", Long.class), r.get("chapter_number", Integer.class), r.get("chars", Long.class),
                            usd(r.get("musd", Long.class)), byStage, r.get("seconds", Double.class),
                            Boolean.TRUE.equals(r.get("translated", Boolean.class)));
                });
    }

    /** Which model each step of a run used; a step switched off shows as «—». */
    record Settings(String analyze, String translate, String proofread) {
    }

    private Map<Long, Settings> settings(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Settings> out = new HashMap<>();
        db.resultQuery("select id, settings::text s from job where id = any(?)", (Object) jobIds.toArray(Long[]::new))
                .forEach(r -> {
                    JsonNode s = json.readTree(r.get("s", String.class));
                    out.put(r.get("id", Long.class), new Settings(model(s, "analyze"), model(s, "translate"), model(s, "proofread")));
                });
        return out;
    }

    private static String model(JsonNode settings, String stage) {
        JsonNode step = settings.path(stage);
        if (step.isMissingNode() || !step.path("enabled").asBoolean(true) || step.path("model").asString("").isEmpty()) {
            return "—";
        }
        return step.path("model").asString();
    }

    /** Of the model's paragraphs, the share that differs now; of its words, the share no longer there. */
    private double[] changed(String aiBlocks, String currentBlocks) {
        Map<String, String> before = texts(aiBlocks);
        Map<String, String> after = currentBlocks == null ? before : texts(currentBlocks);
        if (before.isEmpty()) {
            return new double[] {0, 0};
        }
        int paragraphs = 0;
        Map<String, Integer> words = new HashMap<>();
        int total = 0;
        for (Map.Entry<String, String> block : before.entrySet()) {
            if (!block.getValue().equals(after.get(block.getKey()))) {
                paragraphs++;
            }
            for (String word : block.getValue().split("\\s+")) {
                if (!word.isEmpty()) {
                    words.merge(word, 1, Integer::sum);
                    total++;
                }
            }
        }
        for (String text : after.values()) {
            for (String word : text.split("\\s+")) {
                words.computeIfPresent(word, (w, n) -> n > 1 ? n - 1 : null);
            }
        }
        int gone = words.values().stream().mapToInt(Integer::intValue).sum();
        return new double[] {(double) paragraphs / before.size(), total == 0 ? 0 : (double) gone / total};
    }

    private Map<String, String> texts(String blocks) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode block : json.readTree(blocks)) {
            StringBuilder text = new StringBuilder();
            for (JsonNode span : block.path("content")) {
                text.append(span.path("text").asString(""));
            }
            if (!text.isEmpty()) {
                out.put(block.path("id").asString(""), text.toString().strip());
            }
        }
        return out;
    }

    private static final class Totals {
        int chapters;
        int edited;
        int editor;
        int suggestions;
        int accepted;
        int rejected;
        double paragraphs;
        double words;
        int retries;
        int splits;
        int missing;
        int costed;
        double usd;
        double seconds;
    }

    private static String stageGroup(String stage) {
        return STAGES.contains(stage) ? stage : "other";
    }

    private static double usd(Long microUsd) {
        return microUsd == null ? 0 : microUsd / 1_000_000.0;
    }

    private static double share(int part, int whole) {
        return whole <= 0 ? 0 : (double) part / whole;
    }

    private static double per(int count, int whole) {
        return whole <= 0 ? 0 : (double) count / whole;
    }
}
