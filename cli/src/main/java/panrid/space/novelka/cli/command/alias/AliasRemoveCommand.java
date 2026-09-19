package panrid.space.novelka.cli.command.alias;

import panrid.space.novelka.cli.command.DatabaseCommand;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "remove", mixinStandardHelpOptions = true, description = "Remove an alias")
public final class AliasRemoveCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "ALIAS")
    private String alias;

    @Override
    protected void execute(Store store) throws Exception {
        if (!store.removeAlias(alias)) {
            throw new IllegalArgumentException("Alias not found: " + alias);
        }
        System.out.println("Removed alias '" + Store.normalizeAlias(alias) + "'");
    }
}
