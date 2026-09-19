package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.ChapterRange;
import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import panrid.space.novelka.core.Syosetu;
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
  protected void execute(Store store) throws Exception {
    var source = new Syosetu();
    var novel = source.inspect(url);
    try (var lock = store.lock(novel.id())) {
      store.save(novel);
      if (alias != null) {
        store.saveAlias(novel.id(), alias);
      }
      Output.json(novel);
      if (chapter != null || chapters != null) {
        var range = ChapterRange.parse(chapter, chapters, novel.chapterCount());
        for (int number = range.first(); number <= range.last(); number++) {
          store.save(novel.id(), source.fetch(novel, number));
          System.out.println("Imported chapter " + number);
        }
      }
    }
  }
}
