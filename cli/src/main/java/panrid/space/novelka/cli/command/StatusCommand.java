package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "status", mixinStandardHelpOptions = true)
public final class StatusCommand extends DatabaseCommand {
    @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
    private String novel;

    @Override
    protected void execute(Store store) throws Exception {
        if (novel == null) {
            Output.json(
                    store.rows(
                            "SELECT n.id,n.data->>'title' title,"
                                    + "COALESCE(jsonb_agg(a.alias ORDER BY a.alias) FILTER (WHERE a.alias IS NOT NULL),'[]'::jsonb) aliases"
                                    + " FROM novels n LEFT JOIN novel_aliases a ON a.novel_id=n.id"
                                    + " GROUP BY n.id,n.data ORDER BY n.id"));
            return;
        }
        String id = novelId(store, novel);
        Output.json(
                store.rows(
                        "SELECT id,chapter,revision,state,updated_at FROM jobs WHERE novel_id=? ORDER BY chapter,revision",
                        id));
    }
}
