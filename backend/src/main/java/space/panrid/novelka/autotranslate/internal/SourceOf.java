package space.panrid.novelka.autotranslate.internal;

import java.util.Map;

import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Словник» in the editor (етап 19, п. 4): which piece of the original paragraph a word or phrase
 * selected in its translation was translated from. The answer is the original's own text, never
 * a translation back: a piece the paragraph does not have is no answer.
 */
@Component
class SourceOf {

    private static final int MAX_SELECTION = 100;

    private final Ai ai;
    private final Jobs jobs;
    private final Ledger ledger;
    private final JsonMapper json;

    SourceOf(Ai ai, Jobs jobs, Ledger ledger, JsonMapper json) {
        this.ai = ai;
        this.jobs = jobs;
        this.ledger = ledger;
        this.json = json;
    }

    /**
     * @param payer an account whose шаги pay for it, or null when the site pays (its owner)
     * @return the piece of {@code original}, or null when the model found none in it
     */
    String find(String original, String translation, String selection, String language, Long payer) {
        String selected = selection == null ? "" : selection.strip();
        if (selected.isEmpty() || selected.length() > MAX_SELECTION) {
            throw UserFacingException.badRequest("Виділіть слово чи словосполучення, до 100 знаків.");
        }
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        Settings.Stage model = jobs.settings().analyze();
        long chars = original.length() + translation.length();
        // About a token per three characters read, a short answer, and room to spare.
        long expected = Math.round((chars / 3.0) * model.inputPerMillion() + 200 * model.outputPerMillion() + 500);
        Long hold = payer == null ? null : ledger.hold(payer, Math.max(1, ledger.shahOf(expected)), "Словник: з оригіналу «%s»".formatted(selected));
        long spent = 0;
        String found;
        try {
            AiAnswer answer = ai.ask(new AiRequest(model.model(), Prompts.sourceOf(Prompts.languageName(language)),
                    json.writeValueAsString(Map.of("original", original, "translation", translation, "selection", selected)),
                    "source_of", Prompts.sourceOfSchema(), 400, model.price(), new AiTag(null, null, "glossary", 0), 0));
            spent = answer.costMicroUsd();
            found = Pipeline.lenient(json, answer.content()).path("original").asString("").strip();
        } catch (AiException error) {
            if (hold != null) {
                ledger.settle(hold, spent);
            }
            throw UserFacingException.badGateway(error.getMessage());
        }
        if (hold != null) {
            ledger.settle(hold, Math.max(1, spent));
        }
        return !found.isEmpty() && original.contains(found) ? found : null;
    }
}
