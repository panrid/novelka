package panrid.space.novelka.cli;

import panrid.space.novelka.cli.command.ChaptersCommand;
import panrid.space.novelka.cli.command.CostsCommand;
import panrid.space.novelka.cli.command.ExportCommand;
import panrid.space.novelka.cli.command.GlossaryCommand;
import panrid.space.novelka.cli.command.ImportCommand;
import panrid.space.novelka.cli.command.ImportTextCommand;
import panrid.space.novelka.cli.command.InitCommand;
import panrid.space.novelka.cli.command.ProofreadCommand;
import panrid.space.novelka.cli.command.ResumeCommand;
import panrid.space.novelka.cli.command.StatusCommand;
import panrid.space.novelka.cli.command.TranslateCommand;
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
