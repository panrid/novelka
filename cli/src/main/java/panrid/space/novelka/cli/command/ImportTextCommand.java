package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.PlainText;
import panrid.space.novelka.core.Store;
import panrid.space.novelka.core.Tokens;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Command(
        name = "import-text",
        mixinStandardHelpOptions = true,
        description = "Import a user-provided UTF-8 chapter; first line is its title")
public final class ImportTextCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--chapter", required = true)
    private int chapter;

    @Option(names = "--file", required = true)
    private Path file;

    @Override
    protected void execute(Store store) throws Exception {
        String id = novelId(store, novel);
        try (var lock = store.lock(id)) {
            var metadata = store.novel(id);
            if (chapter < 1 || chapter > metadata.chapterCount()) {
                throw new IllegalArgumentException("Chapter outside novel range");
            }
            var imported =
                    PlainText.parse(
                            chapter, file.toAbsolutePath().toUri().toString(), Files.readString(file));
            store.save(id, imported);
            Output.json(
                    Map.of(
                            "novel", id,
                            "chapter", chapter,
                            "blocks", imported.blocks().size(),
                            "sourceTokens", Tokens.source(imported.blocks())));
        }
    }
}
