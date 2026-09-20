package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "chapters", mixinStandardHelpOptions = true)
public final class ChaptersCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        String id = novelId(database, novel);
        Output.json(database.novels().novel(id));
        Output.json(database.chapters().list(id));
    }
}
