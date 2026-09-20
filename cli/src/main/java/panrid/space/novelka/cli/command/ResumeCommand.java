package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.persistence.DatabaseSession;
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
    protected void execute(DatabaseSession database) throws Exception {
        validateBudget();
        var work = database.jobs().job(job);
        try (var lock = database.lock(work.novelId())) {
            work = database.jobs().job(job);
            if (retry) {
                database.calls().authorizeRetry(job);
            }
            Output.json(
                    ApplicationContext.pipeline(database, model)
                            .run(work, budget == 0 ? 0 : database.calls().spent(job) + budget));
        }
    }
}
