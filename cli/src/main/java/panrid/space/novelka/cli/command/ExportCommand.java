package panrid.space.novelka.cli.command;

import panrid.space.novelka.core.export.BookExporter;
import panrid.space.novelka.core.persistence.DatabaseSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(name = "export", mixinStandardHelpOptions = true)
public final class ExportCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--format", required = true)
    private String format;

    @Option(names = "--output", required = true)
    private Path output;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        String id = novelId(database, novel);
        new BookExporter().export(database.novels().novel(id), database.jobs().completed(id), output, format);
        System.out.println(output.toAbsolutePath());
    }
}
