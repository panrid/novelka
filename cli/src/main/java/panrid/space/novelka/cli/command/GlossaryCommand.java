package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Dictionary;
import panrid.space.novelka.core.Json;
import panrid.space.novelka.core.Store;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

@Command(
        name = "glossary",
        mixinStandardHelpOptions = true,
        description = "List, import manual JSON entries, or inspect proposals")
public final class GlossaryCommand extends DatabaseCommand {
    @Parameters(index = "0", paramLabel = "NOVEL")
    private String novel;

    @Option(names = "--file", description = "JSON array of entries; merged by key and marked manual")
    private Path file;

    @Option(names = "--proposals")
    private boolean proposals;

    @Override
    protected void execute(Store store) throws Exception {
        String id = novelId(store, novel);
        try (var lock = store.lock(id)) {
            if (file != null) {
                var node = Json.read(Files.readString(file));
                if (!node.isArray()) {
                    throw new IllegalArgumentException("Expected JSON array");
                }
                var old = store.glossary(id);
                var entries = new LinkedHashMap<String, Entry>();
                old.entries().forEach(entry -> entries.put(entry.key(), entry));
                for (var item : node) {
                    var entry = Json.decode(item.toString(), Entry.class);
                    Dictionary.validate(entry);
                    entries.put(
                            entry.key(),
                            new Entry(
                                    entry.key(),
                                    entry.kind(),
                                    entry.japanese(),
                                    entry.reading(),
                                    entry.ukrainian(),
                                    entry.aliases(),
                                    entry.gender(),
                                    entry.facts(),
                                    entry.certainty(),
                                    entry.sourceChapter(),
                                    true));
                }
                store.glossary(id, new Glossary(old.revision() + 1, List.copyOf(entries.values())));
            }
            Output.json(
                    proposals
                            ? store.rows(
                            "SELECT id,job_id,proposal FROM glossary_proposals WHERE novel_id=? ORDER BY id",
                            id)
                            : store.glossary(id));
        }
    }
}
