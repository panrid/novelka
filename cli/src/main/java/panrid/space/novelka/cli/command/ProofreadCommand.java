package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "proofread", mixinStandardHelpOptions = true)
public final class ProofreadCommand extends AiCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--chapter", required = true)
    private int chapter;

    @Override
    protected void execute(Store store) throws Exception {
        validateBudget();
        String id = novelId(store, novel);
        try (var lock = store.lock(id)) {
            var previous = store.latest(id, chapter);
            if (previous == null) {
                throw new IllegalArgumentException("Translate first");
            }
            var pipeline = ApplicationContext.pipeline(store, model);
            var work = pipeline.proofread(previous);
            System.out.println("Job " + work.id());
            Output.json(pipeline.run(work, budget));
        }
    }
}
