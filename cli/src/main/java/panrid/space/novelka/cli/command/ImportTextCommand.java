package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.integration.source.text.PlainText;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.support.Tokens;
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
    protected void execute(DatabaseSession database) throws Exception {
        String id = novelId(database, novel);
        try (var lock = database.lock(id)) {
            var metadata = database.novels().novel(id);
            if (chapter < 1 || chapter > metadata.chapterCount()) {
                throw new IllegalArgumentException("Chapter outside novel range");
            }
            var imported =
                    PlainText.parse(
                            chapter, file.toAbsolutePath().toUri().toString(), Files.readString(file));
            database.chapters().save(id, imported);
            Output.json(
                    Map.of(
                            "novel", id,
                            "chapter", chapter,
                            "blocks", imported.blocks().size(),
                            "sourceTokens", Tokens.source(imported.blocks())));
        }
    }
}
