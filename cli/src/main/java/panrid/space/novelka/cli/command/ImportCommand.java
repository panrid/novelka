package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.ChapterRange;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.integration.source.syosetu.Syosetu;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.repository.NovelRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "import",
        mixinStandardHelpOptions = true,
        description = "Import metadata and optional chapter range")
public final class ImportCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "URL")
    private String url;

    @Option(names = "--chapter")
    private Integer chapter;

    @Option(names = "--chapters")
    private String chapters;

    @Option(names = "--alias", description = "Create a convenient alias for the imported novel")
    private String alias;

    @Override
    protected void execute(DatabaseSession database) throws Exception {
        if (alias != null) NovelRepository.normalizeAlias(alias);
        var source = new Syosetu();
        var novel = source.inspect(url);
        var range = chapter == null && chapters == null
                ? null
                : ChapterRange.parse(chapter, chapters, novel.chapterCount());
        try (var lock = database.lock(novel.id())) {
            database.transaction(() -> {
                database.novels().save(novel);
                if (alias != null) database.novels().saveAlias(novel.id(), alias);
                return null;
            });
            Output.json(novel);
            if (range != null) {
                for (int number = range.first(); number <= range.last(); number++) {
                    database.chapters().save(novel.id(), source.fetch(novel, number));
                    System.out.println("Imported chapter " + number);
                }
            }
        }
    }
}
