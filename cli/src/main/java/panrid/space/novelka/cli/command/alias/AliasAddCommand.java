package panrid.space.novelka.cli.command.alias;

import panrid.space.novelka.cli.command.DatabaseCommand;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", mixinStandardHelpOptions = true, description = "Add an alias to a novel")
public final class AliasAddCommand extends DatabaseCommand {
  @Parameters(index = "0", paramLabel = "NOVEL")
  private String novel;

  @Parameters(index = "1", paramLabel = "ALIAS")
  private String alias;

  @Override
  protected void execute(Store store) throws Exception {
    String id = novelId(store, novel);
    store.saveAlias(id, alias);
    System.out.printf("Alias '%s' now points to %s%n", Store.normalizeAlias(alias), id);
  }
}
