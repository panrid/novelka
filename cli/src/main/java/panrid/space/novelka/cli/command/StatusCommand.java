package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "status", mixinStandardHelpOptions = true)
public final class StatusCommand extends DatabaseCommand {
    @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
    private String novel;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        if (novel == null) {
            Output.json(database.novels().list());
            return;
        }
        String id = novelId(database, novel);
        Output.json(database.jobs().status(id));
    }
}
