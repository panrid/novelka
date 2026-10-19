package space.panrid.novelka.autotranslate.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.suggestion.Suggestions;
import space.panrid.novelka.suggestion.Suggestions.BlockChange;
import space.panrid.novelka.text.Chapters;
import space.panrid.novelka.text.EditorModels;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI in the chapter editor (етап 19, п. 5): other versions of one paragraph to pick from, and
 * «Перевірити главу» — the published chapter read against its original, each changed paragraph
 * a suggestion the team accepts or rejects one by one. Paid like every AI call (рішення 29).
 */
@Component
class EditorAi {

    /** The note AI's suggestions carry, so the team sees whose they are. */
    static final String NOTE = "Вичитка ШІ";

    private static final int MAX_PARAGRAPH = 3_000;
    private static final int MAX_CHAPTER = 40_000;

    private final Ai ai;
    private final Jobs jobs;
    private final Ledger ledger;
    private final JsonMapper json;
    private final Glossary glossary;
    private final Chapters chapters;
    private final Suggestions suggestions;

    EditorAi(Ai ai, Jobs jobs, Ledger ledger, JsonMapper json, Glossary glossary, Chapters chapters, Suggestions suggestions) {
        this.ai = ai;
        this.jobs = jobs;
        this.ledger = ledger;
        this.json = json;
        this.glossary = glossary;
        this.chapters = chapters;
        this.suggestions = suggestions;
    }

    /**
     * «Інший варіант»: up to three other Ukrainian versions of the paragraph, with the glossary's
     * names; from its original when the chapter has one.
     *
     * @param payer an account whose шаги pay for it, or null when the site pays (its owner)
     */
    List<String> variants(long editionId, int number, String blockId, String text, Long payer) {
        String paragraph = text == null ? "" : text.strip();
        if (paragraph.isEmpty() || paragraph.length() > MAX_PARAGRAPH) {
            throw UserFacingException.badRequest("Поставте курсор в абзац з текстом (до 3000 знаків).");
        }
        String original = blockId == null ? null : glossary.originalBlock(editionId, number, blockId).orElse(null);
        String language = glossary.scope(editionId, number).language();
        Map<String, String> input = new LinkedHashMap<>();
        if (original != null) {
            input.put("original", original);
        }
        input.put("translation", paragraph);
        String user = "Glossary:\n" + glossaryLines(editionId, paragraph + "\n" + (original == null ? "" : original)) + "\n\n"
                + json.writeValueAsString(input);
        JsonNode answer = ask(Prompts.variants(Prompts.languageName(language), original != null), user, "variants",
                Prompts.variantsSchema(), paragraph.length() + (original == null ? 0 : original.length()), 3,
                "Інший варіант абзацу", payer);
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode variant : answer.path("variants")) {
            String line = variant.asString("").strip();
            if (!line.isEmpty() && !line.equals(paragraph) && line.length() <= MAX_PARAGRAPH * 2) {
                out.add(line);
            }
        }
        if (out.isEmpty()) {
            throw UserFacingException.badGateway("ШІ не запропонував інших варіантів. Спробуйте ще раз.");
        }
        return out.stream().limit(3).toList();
    }

    /**
     * «Вичитати абзац»: the paragraph with the cursor read against its original, as proofreading reads
     * a chapter; the team sees the change before taking it.
     *
     * @return the proofread paragraph, or null when the model would change nothing
     */
    String proofread(long editionId, int number, String blockId, String text, Long payer) {
        String paragraph = text == null ? "" : text.strip();
        if (paragraph.isEmpty() || paragraph.length() > MAX_PARAGRAPH) {
            throw UserFacingException.badRequest("Поставте курсор в абзац з текстом (до 3000 знаків).");
        }
        String original = blockId == null ? null : glossary.originalBlock(editionId, number, blockId).orElse(null);
        if (original == null) {
            throw UserFacingException.badRequest("У цього абзацу немає оригіналу: вичитувати нема з чим.");
        }
        Map<String, String> pair = new LinkedHashMap<>();
        pair.put("id", blockId);
        pair.put("original", original);
        pair.put("draft", paragraph);
        String language = glossary.scope(editionId, number).language();
        String user = "Glossary:\n" + glossaryLines(editionId, original + "\n" + paragraph) + "\n\nBlocks (JSON):\n"
                + json.writeValueAsString(List.of(pair));
        JsonNode answer = ask(Prompts.proofread(language), user, "proofread", Prompts.blocksSchema(false),
                original.length() + paragraph.length(), 1, "Вичитати абзац", payer);
        for (JsonNode line : answer.path("blocks")) {
            String fixed = line.path("text").asString("").strip();
            if (blockId.equals(line.path("id").asString("")) && !fixed.isEmpty() && !fixed.equals(paragraph)) {
                return fixed;
            }
        }
        return null;
    }

    /**
     * «Перевірити главу»: the published text read against the original; each paragraph the model
     * changes becomes a suggestion of {@code viewer} with the note {@link #NOTE}. Paragraphs with
     * formatting inside are left alone, so no bold or italics are lost.
     *
     * @return how many suggestions it made
     */
    int check(Viewer viewer, long editionId, int number, Long payer) {
        EditorModels.CurrentText current = chapters.current(editionId, number);
        Map<String, String> originals = glossary.originalBlocks(editionId, number)
                .orElseThrow(() -> UserFacingException.badRequest("У цієї глави немає оригіналу: перевіряти нема з чим."));
        List<Map<String, String>> pairs = new ArrayList<>();
        Map<String, String> drafts = new HashMap<>();
        StringBuilder text = new StringBuilder();
        for (Block block : current.blocks()) {
            String original = originals.get(block.id());
            boolean plain = block.content().size() == 1 && block.content().getFirst().marks().isEmpty();
            if (original == null || !plain || block.text().isBlank()) {
                continue;
            }
            Map<String, String> pair = new LinkedHashMap<>();
            pair.put("id", block.id());
            pair.put("original", original);
            pair.put("draft", block.text());
            pairs.add(pair);
            drafts.put(block.id(), block.text());
            text.append(original).append('\n').append(block.text()).append('\n');
        }
        if (pairs.isEmpty()) {
            throw UserFacingException.badRequest("Немає абзаців, які можна звірити з оригіналом.");
        }
        if (text.length() > MAX_CHAPTER) {
            throw UserFacingException.badRequest("Глава задовга, щоб перевірити її однією дією.");
        }
        String language = glossary.scope(editionId, number).language();
        String user = "Glossary:\n" + glossaryLines(editionId, text.toString()) + "\n\nBlocks (JSON):\n" + json.writeValueAsString(pairs);
        JsonNode answer = ask(Prompts.proofread(language), user, "proofread", Prompts.blocksSchema(false), text.length(), 1,
                "Перевірити главу %d".formatted(number), payer);
        List<BlockChange> changes = new ArrayList<>();
        for (JsonNode line : answer.path("blocks")) {
            String id = line.path("id").asString("");
            String fixed = line.path("text").asString("").strip();
            if (drafts.containsKey(id) && !fixed.isEmpty() && !fixed.equals(drafts.get(id))) {
                changes.add(new BlockChange(number, id, List.of(Span.plain(fixed))));
                drafts.remove(id);
            }
        }
        return suggestions.propose(viewer, editionId, changes, NOTE, false);
    }

    /** One paid call: шаги held for the estimate, then charged what it really cost. */
    private JsonNode ask(String system, String user, String schemaName, Map<String, Object> schema, int chars, int answers, String what,
            Long payer) {
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        Settings.Stage model = jobs.settings().proofread();
        // About a token per three characters, read once and written once per answer, with room to spare.
        long expected = Math.round((chars / 3.0) * (model.inputPerMillion() + answers * model.outputPerMillion()) * 1.5 + 2_000);
        Long hold = payer == null ? null : ledger.hold(payer, Math.max(1, ledger.shahOf(expected)), what);
        long spent = 0;
        try {
            AiAnswer answer = ai.ask(new AiRequest(model.model(), system, user, schemaName, schema,
                    Math.min(16_000, 1_000 + chars * answers / 2), model.price(), new AiTag(null, null, "editor", 0), 0));
            spent = answer.costMicroUsd();
            return Pipeline.lenient(json, answer.content());
        } catch (AiException error) {
            throw UserFacingException.badGateway(error.getMessage());
        } finally {
            if (hold != null) {
                ledger.settle(hold, spent);
            }
        }
    }

    private String glossaryLines(long editionId, String text) {
        List<Glossary.Entry> entries = glossary.mentionedIn(editionId, text);
        if (entries.isEmpty()) {
            return "(empty)";
        }
        StringBuilder out = new StringBuilder();
        entries.forEach(entry -> out.append(entry.line()).append('\n'));
        return out.toString();
    }
}
