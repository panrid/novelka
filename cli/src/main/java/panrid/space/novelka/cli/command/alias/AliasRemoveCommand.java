package panrid.space.novelka.cli.command.alias;

import panrid.space.novelka.cli.command.DatabaseCommand;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.repository.NovelRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "remove", mixinStandardHelpOptions = true, description = "Remove an alias")
public final class AliasRemoveCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "ALIAS")
    private String alias;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        if (!database.novels().removeAlias(alias)) {
            throw new IllegalArgumentException("Alias not found: " + alias);
        }
        System.out.println("Removed alias '" + NovelRepository.normalizeAlias(alias) + "'");
    }
}
