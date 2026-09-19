package panrid.space.novelka.cli.command;

import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;

@Command(name = "init", mixinStandardHelpOptions = true, description = "Apply database migrations")
public final class InitCommand extends DatabaseCommand {
  @Override
  protected void execute(Store store) {
    System.out.println("Database ready");
  }
}
