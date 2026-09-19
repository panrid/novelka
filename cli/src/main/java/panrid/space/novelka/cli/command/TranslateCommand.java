package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.cli.support.ChapterRange;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import panrid.space.novelka.core.Syosetu;
import panrid.space.novelka.core.Tokens;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Locale;

@Command(
        name = "translate",
        mixinStandardHelpOptions = true,
        description = "Fetch missing source and translate + proofread")
public final class TranslateCommand extends AiCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--chapter")
    private Integer chapter;

    @Option(names = "--chapters")
    private String chapters;

    @Option(names = "--force")
    private boolean force;

    @Override
    protected void execute(Store store) throws Exception {
        validateBudget();
        String id = novelId(store, novel);
        try (var lock = store.lock(id)) {
            var metadata = store.novel(id);
            var range = ChapterRange.parse(chapter, chapters, metadata.chapterCount());
            var source = new Syosetu();
            var pipeline = ApplicationContext.pipeline(store, model);
            double consumed = 0;
            for (int number = range.first(); number <= range.last(); number++) {
                if (store.rows("SELECT number FROM chapters WHERE novel_id=? AND number=?", id, number)
                        .isEmpty()) {
                    store.save(id, source.fetch(metadata, number));
                }
                var imported = store.chapter(id, number);
                int tokens = Tokens.source(imported.blocks());
                System.out.printf(
                        Locale.ROOT,
                        "Chapter %d: %d source tokens (o200k_base); target $%.4f%n",
                        number,
                        tokens,
                        tokens / 5000.0 * 0.10);
                var work = pipeline.create(id, number, force);
                double before = store.spent(work.id());
                if (budget > 0 && consumed >= budget) {
                    throw new IllegalStateException("Run budget reached");
                }
                System.out.println("Job " + work.id());
                work = pipeline.run(work, budget == 0 ? 0 : before + budget - consumed);
                consumed += store.spent(work.id()) - before;
                Output.json(work.state());
            }
            System.out.printf(
                    Locale.ROOT, "Recorded cost/unknown-cost reserve this run: $%.6f%n", consumed);
        }
    }
}
