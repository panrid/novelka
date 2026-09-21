package panrid.space.novelka.server.task;

import panrid.space.novelka.core.integration.ai.AiClient;
import panrid.space.novelka.core.repository.AiCallRepository;
import panrid.space.novelka.server.settings.SiteSettings;

@FunctionalInterface
public interface TaskAiFactory {
    AiClient create(AiCallRepository calls, SiteSettings settings);
}
