package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "costs",
        mixinStandardHelpOptions = true,
        description = "Stored estimates, actual spend, usage and unknown costs")
public final class CostsCommand extends DatabaseCommand {
    @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--details")
    private boolean details;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        Output.json(database.calls().costs(novel == null ? null : novelId(database, novel), details));
    }
}
