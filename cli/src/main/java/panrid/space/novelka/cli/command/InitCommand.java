package panrid.space.novelka.cli.command;

import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;

@Command(name = "init", mixinStandardHelpOptions = true, description = "Apply database migrations")
public final class InitCommand extends DatabaseCommand {
    @Override
    protected void execute(DatabaseSession database) {
        System.out.println("Database ready");
    }
}
