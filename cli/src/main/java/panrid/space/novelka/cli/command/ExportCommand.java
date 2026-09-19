package panrid.space.novelka.cli.command;

import panrid.space.novelka.core.BookExporter;
import panrid.space.novelka.core.Store;
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
    protected void execute(Store store) throws Exception {
        String id = novelId(store, novel);
        new BookExporter().export(store.novel(id), store.completed(id), output, format);
        System.out.println(output.toAbsolutePath());
    }
}
