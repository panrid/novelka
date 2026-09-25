package space.panrid.novelka.autotranslate.internal;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.ImportedNovel;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceNovel;
import space.panrid.novelka.source.Sources;
import space.panrid.novelka.source.SyosetuLink;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Підготувати»: the novel's page from Syosetu, its title, author and description
 * translated at once (рішення 8: no Japanese on the site), and the team's edition.
 */
@Component
class Preparation {

    private final Sources sources;
    private final Catalog catalog;
    private final Ai ai;
    private final Jobs jobs;
    private final JsonMapper json;

    Preparation(Sources sources, Catalog catalog, Ai ai, Jobs jobs, JsonMapper json) {
        this.sources = sources;
        this.catalog = catalog;
        this.ai = ai;
        this.jobs = jobs;
        this.json = json;
    }

    EditionRef prepare(SyosetuLink link, long teamId) {
        boolean known = catalog.novelBySource(link.key()).isPresent();
        if (!known && !ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері: назву й опис нема чим перекласти.");
        }
        SourceNovel novel = sources.novel(link);
        if (known) {
            return catalog.importNovel(new ImportedNovel(link.key(), link.url(), novel.title(), novel.author(),
                    novel.title(), novel.author(), List.of(), novel.chapters(), link.adult(), teamId));
        }
        Settings.Stage model = jobs.settings().analyze();
        String user = "Title: " + novel.title() + "\nAuthor: " + novel.author() + "\nDescription:\n" + novel.story();
        JsonNode answer = null;
        for (int attempt = 0; attempt < 2 && answer == null; attempt++) {
            AiAnswer reply;
            try {
                reply = ai.ask(new AiRequest(model.model(), Prompts.METADATA, user, "novel", Prompts.metadataSchema(), 4_000,
                        model.price(), new AiTag(null, null, "metadata", null), attempt));
            } catch (AiException error) {
                throw UserFacingException.badGateway(error.getMessage());
            }
            try {
                JsonNode parsed = json.readTree(reply.content());
                if (!parsed.path("title").asString("").isBlank()) {
                    answer = parsed;
                }
            } catch (RuntimeException notJson) {
                // asked again below
            }
        }
        if (answer == null) {
            throw UserFacingException.badGateway("Модель не змогла перекласти назву новели. Спробуйте ще раз.");
        }
        return catalog.importNovel(new ImportedNovel(link.key(), link.url(), novel.title(), novel.author(),
                answer.path("title").asString().strip(), answer.path("author").asString("").strip(),
                paragraphs(answer.path("description").asString("")), novel.chapters(), link.adult(), teamId));
    }

    static List<Block> paragraphs(String text) {
        List<Block> blocks = new ArrayList<>();
        for (String line : text.split("\\n")) {
            if (!line.isBlank()) {
                blocks.add(Block.paragraph("d" + (blocks.size() + 1), List.of(Span.plain(line.strip()))));
            }
        }
        return blocks;
    }
}
