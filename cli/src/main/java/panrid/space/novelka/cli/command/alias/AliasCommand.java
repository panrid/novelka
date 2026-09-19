package panrid.space.novelka.cli.command.alias;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "alias",
    mixinStandardHelpOptions = true,
    description = "Manage convenient novel aliases",
    subcommands = {AliasAddCommand.class, AliasListCommand.class, AliasRemoveCommand.class})
public final class AliasCommand implements Runnable {
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
