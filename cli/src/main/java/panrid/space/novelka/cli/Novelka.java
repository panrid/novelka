package panrid.space.novelka.cli;

import panrid.space.novelka.cli.command.*;
import panrid.space.novelka.cli.command.alias.AliasCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "novelka",
    mixinStandardHelpOptions = true,
    version = "Novelka 0.2",
    description = "Japanese → Ukrainian novel translation",
    subcommands = {
      InitCommand.class,
      ImportCommand.class,
      ImportTextCommand.class,
      ChaptersCommand.class,
      TranslateCommand.class,
      ResumeCommand.class,
      ProofreadCommand.class,
      StatusCommand.class,
      GlossaryCommand.class,
      ExportCommand.class,
      CostsCommand.class,
      AliasCommand.class
    })
public final class Novelka implements Runnable {
  public static void main(String[] args) {
    var cli = new CommandLine(new Novelka());
    cli.setExecutionExceptionHandler(
        (error, command, parseResult) -> {
          command.getErr().println("Error: " + error.getMessage());
          return 1;
        });
    System.exit(cli.execute(args));
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
