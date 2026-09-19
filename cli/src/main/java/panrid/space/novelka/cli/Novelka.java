package panrid.space.novelka.cli;

import panrid.space.novelka.core.*;
import panrid.space.novelka.core.Dictionary;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

import static panrid.space.novelka.core.Domain.Entry;
import static panrid.space.novelka.core.Domain.Glossary;

@Command(
        name = "novelka",
        mixinStandardHelpOptions = true,
        version = "Novelka 0.1",
        description = "Japanese → Ukrainian novel translation",
        subcommands = {
                Novelka.Init.class,
                Novelka.Import.class,
                Novelka.ImportText.class,
                Novelka.Chapters.class,
                Novelka.Translate.class,
                Novelka.Resume.class,
                Novelka.Proofread.class,
                Novelka.Status.class,
                Novelka.GlossaryCommand.class,
                Novelka.Export.class,
                Novelka.Costs.class
        })
public final class Novelka implements Runnable {
    public static void main(String[] args) {
        var cli = new CommandLine(new Novelka());
        cli.setExecutionExceptionHandler(
                (e, c, p) -> {
                    c.getErr().println("Error: " + e.getMessage());
                    return 1;
                });
        System.exit(cli.execute(args));
    }

    public void run() {
        CommandLine.usage(this, System.out);
    }

    static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    static Store store() throws Exception {
        return new Store(
                env("NOVELKA_DB_URL", "jdbc:postgresql://localhost:5432/novelka"),
                env("NOVELKA_DB_USER", "novelka"),
                env("NOVELKA_DB_PASSWORD", "novelka"));
    }

    static Pipeline pipeline(Store s, String model) {
        Map<String, OpenRouter> clients = new HashMap<>();
        for (String stage : List.of("analyze", "translate", "proofread"))
            clients.put(
                    stage,
                    new OpenRouter(
                            s,
                            System.getenv("OPENROUTER_API_KEY"),
                            env("NOVELKA_" + stage.toUpperCase(Locale.ROOT) + "_MODEL", model),
                            stage));
        return new Pipeline(
                s,
                (job, segment, stage, glossary, payload, budget) ->
                        clients.get(stage).generate(job, segment, stage, glossary, payload, budget),
                Integer.parseInt(env("NOVELKA_SEGMENT_CHARS", "1500")));
    }

    static void print(Object value) {
        System.out.println(Json.write(value));
    }

    static int[] range(Integer chapter, String chapters, int max) {
        if ((chapter == null) == (chapters == null))
            throw new IllegalArgumentException("Specify exactly one of --chapter or --chapters");
        int first, last;
        if (chapter != null) {
            first = last = chapter;
        } else {
            if (!chapters.matches("[0-9]+-[0-9]+"))
                throw new IllegalArgumentException("Use --chapters 1-10");
            var parts = chapters.split("-");
            first = Integer.parseInt(parts[0]);
            last = Integer.parseInt(parts[1]);
        }
        if (first < 1 || last < first || last > max)
            throw new IllegalArgumentException("Invalid chapter range (1-" + max + ")");
        return new int[]{first, last};
    }

    abstract static class DbCommand implements Callable<Integer> {
        public Integer call() throws Exception {
            try (Store s = store()) {
                execute(s);
            }
            return 0;
        }

        abstract void execute(Store s) throws Exception;
    }

    @Command(
            name = "init",
            mixinStandardHelpOptions = true,
            description = "Apply database migrations")
    static class Init extends DbCommand {
        void execute(Store s) {
            System.out.println("Database ready");
        }
    }

    @Command(
            name = "import",
            mixinStandardHelpOptions = true,
            description = "Import metadata and optional chapter range")
    static class Import extends DbCommand {
        @Parameters(index = "0")
        String url;

        @Option(names = "--chapter")
        Integer chapter;

        @Option(names = "--chapters")
        String chapters;

        void execute(Store store) throws Exception {
            var source = new Syosetu();
            var novel = source.inspect(url);
            try (var lock = store.lock(novel.id())) {
                store.save(novel);
                print(novel);
                if (chapter != null || chapters != null) {
                    var r = range(chapter, chapters, novel.chapterCount());
                    for (int i = r[0]; i <= r[1]; i++) {
                        store.save(novel.id(), source.fetch(novel, i));
                        System.out.println("Imported chapter " + i);
                    }
                }
            }
        }
    }

    @Command(
            name = "import-text",
            mixinStandardHelpOptions = true,
            description = "Import a user-provided UTF-8 chapter; first line is its title")
    static class ImportText extends DbCommand {
        @Parameters(index = "0")
        String novel;

        @Option(names = "--chapter", required = true)
        int chapter;

        @Option(names = "--file", required = true)
        Path file;

        void execute(Store s) throws Exception {
            try (var lock = s.lock(novel)) {
                var n = s.novel(novel);
                if (chapter < 1 || chapter > n.chapterCount())
                    throw new IllegalArgumentException("Chapter outside novel range");
                var ch =
                        PlainText.parse(
                                chapter, file.toAbsolutePath().toUri().toString(), Files.readString(file));
                s.save(novel, ch);
                print(
                        Map.of(
                                "novel",
                                novel,
                                "chapter",
                                chapter,
                                "blocks",
                                ch.blocks().size(),
                                "sourceTokens",
                                Tokens.source(ch.blocks())));
            }
        }
    }

    @Command(name = "chapters", mixinStandardHelpOptions = true)
    static class Chapters extends DbCommand {
        @Parameters(index = "0")
        String novel;

        void execute(Store s) throws Exception {
            print(s.novel(novel));
            print(
                    s.rows(
                            "SELECT number,data->>'title' title,source_hash FROM chapters WHERE novel_id=? ORDER"
                                    + " BY number",
                            novel));
        }
    }

    abstract static class AiCommand extends DbCommand {
        @Option(names = "--model", defaultValue = "${env:NOVELKA_MODEL:-openai/gpt-4o-mini}")
        String model;

        @Option(
                names = "--budget-usd",
                description = "Conservative USD ceiling for this invocation; 0 = no ceiling",
                defaultValue = "0")
        double budget;

        void validateBudget() {
            if (!Double.isFinite(budget) || budget < 0)
                throw new IllegalArgumentException("Budget must be finite and nonnegative");
        }
    }

    @Command(
            name = "translate",
            mixinStandardHelpOptions = true,
            description = "Fetch missing source and translate + proofread")
    static class Translate extends AiCommand {
        @Parameters(index = "0")
        String novel;

        @Option(names = "--chapter")
        Integer chapter;

        @Option(names = "--chapters")
        String chapters;

        @Option(names = "--force")
        boolean force;

        void execute(Store s) throws Exception {
            validateBudget();
            try (var lock = s.lock(novel)) {
                var n = s.novel(novel);
                var r = range(chapter, chapters, n.chapterCount());
                var source = new Syosetu();
                var p = pipeline(s, model);
                double consumed = 0;
                for (int i = r[0]; i <= r[1]; i++) {
                    if (s.rows("SELECT number FROM chapters WHERE novel_id=? AND number=?", novel, i)
                            .isEmpty()) s.save(novel, source.fetch(n, i));
                    var ch = s.chapter(novel, i);
                    int tokens = Tokens.source(ch.blocks());
                    System.out.printf(
                            Locale.ROOT,
                            "Chapter %d: %d source tokens (o200k_base); target $%.4f%n",
                            i,
                            tokens,
                            tokens / 5000.0 * 0.10);
                    var w = p.create(novel, i, force);
                    double before = s.spent(w.id());
                    if (budget > 0 && consumed >= budget)
                        throw new IllegalStateException("Run budget reached");
                    System.out.println("Job " + w.id());
                    w = p.run(w, budget == 0 ? 0 : before + budget - consumed);
                    consumed += s.spent(w.id()) - before;
                    print(w.state());
                }
                System.out.printf(
                        Locale.ROOT, "Recorded cost/unknown-cost reserve this run: $%.6f%n", consumed);
            }
        }
    }

    @Command(name = "resume", mixinStandardHelpOptions = true)
    static class Resume extends AiCommand {
        @Parameters(index = "0")
        String job;

        @Option(
                names = "--retry-uncertain",
                description = "Authorize potentially paid retry after unknown outcome")
        boolean retry;

        void execute(Store s) throws Exception {
            validateBudget();
            var w = s.job(job);
            try (var lock = s.lock(w.novelId())) {
                w = s.job(job);
                if (retry)
                    s.exec(
                            "UPDATE ai_calls SET state='retry-authorized' WHERE job_id=? AND state IN"
                                    + " ('pending','uncertain')",
                            job);
                print(pipeline(s, model).run(w, budget == 0 ? 0 : s.spent(job) + budget));
            }
        }
    }

    @Command(name = "proofread", mixinStandardHelpOptions = true)
    static class Proofread extends AiCommand {
        @Parameters(index = "0")
        String novel;

        @Option(names = "--chapter", required = true)
        int chapter;

        void execute(Store s) throws Exception {
            validateBudget();
            try (var lock = s.lock(novel)) {
                var old = s.latest(novel, chapter);
                if (old == null) throw new IllegalArgumentException("Translate first");
                var p = pipeline(s, model);
                var w = p.proofread(old);
                System.out.println("Job " + w.id());
                print(p.run(w, budget));
            }
        }
    }

    @Command(name = "status", mixinStandardHelpOptions = true)
    static class Status extends DbCommand {
        @Parameters(index = "0", arity = "0..1")
        String novel;

        void execute(Store s) throws Exception {
            print(
                    novel == null
                            ? s.rows("SELECT id,data->>'title' title FROM novels ORDER BY id")
                            : s.rows(
                            "SELECT id,chapter,revision,state,updated_at FROM jobs WHERE novel_id=? ORDER BY"
                                    + " chapter,revision",
                            novel));
        }
    }

    @Command(
            name = "glossary",
            mixinStandardHelpOptions = true,
            description = "List, import manual JSON entries, or inspect proposals")
    static class GlossaryCommand extends DbCommand {
        @Parameters(index = "0")
        String novel;

        @Option(
                names = "--file",
                description = "JSON array of entries; merged by key and marked manual")
        Path file;

        @Option(names = "--proposals")
        boolean proposals;

        void execute(Store s) throws Exception {
            try (var lock = s.lock(novel)) {
                s.novel(novel);
                if (file != null) {
                    var node = Json.read(Files.readString(file));
                    if (!node.isArray()) throw new IllegalArgumentException("Expected JSON array");
                    var old = s.glossary(novel);
                    var entries = new LinkedHashMap<String, Entry>();
                    old.entries().forEach(e -> entries.put(e.key(), e));
                    for (var item : node) {
                        var e = Json.decode(item.toString(), Entry.class);
                        Dictionary.validate(e);
                        entries.put(
                                e.key(),
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
                                        e.sourceChapter(),
                                        true));
                    }
                    s.glossary(novel, new Glossary(old.revision() + 1, List.copyOf(entries.values())));
                }
                print(
                        proposals
                                ? s.rows(
                                "SELECT id,job_id,proposal FROM glossary_proposals WHERE novel_id=? ORDER BY"
                                        + " id",
                                novel)
                                : s.glossary(novel));
            }
        }
    }

    @Command(name = "export", mixinStandardHelpOptions = true)
    static class Export extends DbCommand {
        @Parameters(index = "0")
        String novel;

        @Option(names = "--format", required = true)
        String format;

        @Option(names = "--output", required = true)
        Path output;

        void execute(Store s) throws Exception {
            new BookExporter().export(s.novel(novel), s.completed(novel), output, format);
            System.out.println(output.toAbsolutePath());
        }
    }

    @Command(
            name = "costs",
            mixinStandardHelpOptions = true,
            description = "Stored estimates, actual spend, usage and unknown costs")
    static class Costs extends DbCommand {
        @Parameters(index = "0", arity = "0..1")
        String novel;

        @Option(names = "--details")
        boolean details;

        void execute(Store s) throws Exception {
            String where = novel == null ? "" : " WHERE j.novel_id=?";
            Object[] args = novel == null ? new Object[]{} : new Object[]{novel};
            print(
                    s.rows(
                            details
                                    ? "SELECT"
                                    + " a.id,a.job_id,j.novel_id,j.chapter,a.stage,a.segment,a.model,a.provider,a.prompt_version,a.glossary_revision,a.estimated_usd,a.actual_usd,a.cost_source,a.input_tokens,a.output_tokens,a.cached_tokens,a.state,a.request_id,a.duration_ms,a.created_at"
                                    + " FROM ai_calls a JOIN jobs j ON j.id=a.job_id"
                                    + where
                                    + " ORDER BY a.created_at"
                                    : "SELECT j.novel_id,j.chapter,a.stage,a.model,count(*)"
                                    + " calls,sum(a.estimated_usd) estimated_usd,sum(a.actual_usd)"
                                    + " known_actual_usd,count(*) FILTER(WHERE a.actual_usd IS NULL)"
                                    + " unknown_cost_calls,sum(a.input_tokens)"
                                    + " input_tokens,sum(a.output_tokens) output_tokens FROM ai_calls a JOIN"
                                    + " jobs j ON j.id=a.job_id"
                                    + where
                                    + " GROUP BY j.novel_id,j.chapter,a.stage,a.model ORDER BY"
                                    + " j.novel_id,j.chapter,a.stage",
                            args));
        }
    }
}
