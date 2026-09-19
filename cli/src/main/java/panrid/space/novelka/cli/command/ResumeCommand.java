package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "resume", mixinStandardHelpOptions = true)
public final class ResumeCommand extends AiCommand {
    @Parameters(index = "0", paramLabel = "JOB_ID")
    private String job;

    @Option(
            names = "--retry-uncertain",
            description = "Authorize potentially paid retry after unknown outcome")
    private boolean retry;

    @Override
    protected void execute(Store store) throws Exception {
        validateBudget();
        var work = store.job(job);
        try (var lock = store.lock(work.novelId())) {
            work = store.job(job);
            if (retry) {
                store.exec(
                        "UPDATE ai_calls SET state='retry-authorized' WHERE job_id=? AND state IN ('pending','uncertain')",
                        job);
            }
            Output.json(
                    ApplicationContext.pipeline(store, model)
                            .run(work, budget == 0 ? 0 : store.spent(job) + budget));
        }
    }
}
