package space.panrid.novelka.autotranslate.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.suggestion.Suggestions.BlockChange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * After a glossary word was changed in the chapters (етап 17), a model puts the new word and the
 * words that agree with it into the right case and gender. Only that: an answer that rewrites
 * more of a paragraph is dropped and the plain replacement stays. Paragraphs with formatting
 * inside keep the plain replacement too, so no bold or italics are lost.
 */
@Component
class Agreement {

    private static final int PER_CALL = 40;

    private final Ai ai;
    private final Jobs jobs;
    private final Ledger ledger;
    private final JsonMapper json;

    Agreement(Ai ai, Jobs jobs, Ledger ledger, JsonMapper json) {
        this.ai = ai;
        this.jobs = jobs;
        this.ledger = ledger;
        this.json = json;
    }

    /** @param payer an account whose шаги pay for it, or null when the site pays (its owner) */
    List<BlockChange> fix(List<BlockChange> changes, Map<String, String> before, String from, String to, Long payer) {
        return ask(changes, before, Prompts.agreement(from, to), "Словник: «%s» → «%s»".formatted(from, to), payer, 3);
    }

    /**
     * A character's gender changed: verbs, adjectives and pronouns that refer to them follow.
     * Paragraphs the model leaves as they were are not changes and are left out.
     */
    List<BlockChange> regender(List<BlockChange> paragraphs, String name, String gender, Long payer) {
        Map<String, String> before = new HashMap<>();
        paragraphs.forEach(change -> before.put(key(change), change.proposed().getFirst().text()));
        return ask(paragraphs, before, Prompts.gender(name, gender), "Словник: рід «%s»".formatted(name), payer, 6).stream()
                .filter(change -> !change.proposed().getFirst().text().equals(before.get(key(change))))
                .toList();
    }

    private List<BlockChange> ask(List<BlockChange> changes, Map<String, String> before, String system, String what, Long payer,
            int wordsPerChange) {
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        Settings.Stage model = jobs.settings().proofread();
        List<BlockChange> plain = changes.stream().filter(change -> change.proposed().size() == 1).toList();
        long chars = plain.stream().mapToLong(change -> change.proposed().getFirst().text().length()).sum();
        // About a token per three characters of Ukrainian, read once and written once, with room to spare.
        long expected = Math.round((chars / 3.0) * (model.inputPerMillion() + model.outputPerMillion()) * 2 + 2_000);
        Long hold = payer == null ? null : ledger.hold(payer, Math.max(1, ledger.shahOf(expected)), what);
        long spent = 0;
        Map<String, String> fixed = new HashMap<>();
        try {
            for (int at = 0; at < plain.size(); at += PER_CALL) {
                List<BlockChange> part = plain.subList(at, Math.min(plain.size(), at + PER_CALL));
                List<Map<String, String>> input = new ArrayList<>();
                for (BlockChange change : part) {
                    input.add(Map.of("id", key(change), "text", change.proposed().getFirst().text()));
                }
                AiAnswer answer = ai.ask(new AiRequest(model.model(), system, json.writeValueAsString(input), "agreement",
                        Prompts.blocksSchema(false), Math.min(16_000, 1_000 + (int) (chars / 2)), model.price(),
                        new AiTag(null, null, "glossary", at / PER_CALL), 0));
                spent += answer.costMicroUsd();
                for (JsonNode line : Pipeline.lenient(json, answer.content()).path("blocks")) {
                    fixed.put(line.path("id").asString(""), line.path("text").asString("").strip());
                }
            }
        } catch (AiException error) {
            if (hold != null) {
                ledger.settle(hold, spent);
            }
            throw UserFacingException.badGateway(error.getMessage());
        }
        if (hold != null) {
            ledger.settle(hold, Math.max(1, spent));
        }
        List<BlockChange> out = new ArrayList<>();
        for (BlockChange change : changes) {
            String replaced = change.proposed().getFirst().text();
            String answer = change.proposed().size() == 1 ? fixed.get(key(change)) : null;
            boolean usable = answer != null && !answer.isBlank()
                    && small(before.getOrDefault(key(change), replaced), replaced, answer, wordsPerChange);
            out.add(usable ? new BlockChange(change.chapterNumber(), change.blockId(),
                    List.of(new Span(answer, change.proposed().getFirst().marks()))) : change);
        }
        return out;
    }

    static String key(BlockChange change) {
        return change.chapterNumber() + ":" + change.blockId();
    }

    /**
     * The model touched only the words around the change: no more words differ from the plain
     * replacement than the replacement itself changed, plus two for agreeing words each time.
     */
    static boolean small(String original, String replaced, String answer) {
        return small(original, replaced, answer, 3);
    }

    static boolean small(String original, String replaced, String answer, int wordsPerChange) {
        List<String> base = words(replaced);
        List<String> got = words(answer);
        if (Math.abs(base.size() - got.size()) > 1) {
            return false;
        }
        int changedByReplacement = differing(words(original), base);
        return differing(base, got) <= Math.max(1, changedByReplacement) * wordsPerChange;
    }

    private static List<String> words(String text) {
        return Arrays.stream(text.strip().split("\\s+")).filter(word -> !word.isEmpty()).toList();
    }

    private static int differing(List<String> a, List<String> b) {
        int same = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (a.get(i).equals(b.get(i))) {
                same++;
            }
        }
        return Math.max(a.size(), b.size()) - same;
    }
}
