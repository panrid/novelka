package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "chapters", mixinStandardHelpOptions = true)
public final class ChaptersCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Override
    protected void execute(Store store) throws Exception {
        String id = novelId(store, novel);
        Output.json(store.novel(id));
        Output.json(
                store.rows(
                        "SELECT number,data->>'title' title,source_hash FROM chapters WHERE novel_id=? ORDER BY number",
                        id));
    }
}
