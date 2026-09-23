package panrid.space.novelka.server.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import panrid.space.novelka.core.integration.ai.AiClient;
import panrid.space.novelka.core.integration.ai.OpenRouter;
import panrid.space.novelka.core.repository.AiCallRepository;
import panrid.space.novelka.server.settings.SiteSettings;

import java.util.HashMap;

@Component
public final class OpenRouterTaskAiFactory implements TaskAiFactory {
    private final String key;

    public OpenRouterTaskAiFactory(@Value("${novelka.openrouter.key:}") String key) { this.key = key; }

    @Override
    public AiClient create(AiCallRepository calls, SiteSettings settings, int dictionarySearchLimit) {
        var clients = new HashMap<String, OpenRouter>();
        for (var stage : settings.stages()) clients.put(stage.stage(),
                new OpenRouter(calls, key, stage.model(), stage.inputUsdM(), stage.outputUsdM(), dictionarySearchLimit));
        return (job, segment, stage, glossary, payload, budget) -> clients.get(stage).generate(job, segment, stage, glossary, payload, budget);
    }
}
