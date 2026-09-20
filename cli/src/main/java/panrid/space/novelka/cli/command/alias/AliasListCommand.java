package panrid.space.novelka.cli.command.alias;

import panrid.space.novelka.cli.command.DatabaseCommand;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "list", mixinStandardHelpOptions = true, description = "List novel aliases")
public final class AliasListCommand extends DatabaseCommand {
    @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
    private String novel;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        Output.json(database.novels().aliases(novel == null ? null : novelId(database, novel)));
    }
}
