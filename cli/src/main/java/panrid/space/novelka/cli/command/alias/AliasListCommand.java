package panrid.space.novelka.cli.command.alias;

import panrid.space.novelka.cli.command.DatabaseCommand;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "list", mixinStandardHelpOptions = true, description = "List novel aliases")
public final class AliasListCommand extends DatabaseCommand {
  @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
  private String novel;

  @Override
  protected void execute(Store store) throws Exception {
    Output.json(store.aliases(novel == null ? null : novelId(store, novel)));
  }
}
