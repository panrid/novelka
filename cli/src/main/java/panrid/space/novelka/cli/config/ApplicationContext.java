package panrid.space.novelka.cli.config;

import panrid.space.novelka.core.integration.ai.OpenRouter;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.service.translation.Pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ApplicationContext {
    private ApplicationContext() {
    }

    public static String environment(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    public static DatabaseSession openDatabase() throws Exception {
        return new DatabaseSession(
                environment("NOVELKA_DB_URL", "jdbc:postgresql://localhost:5432/novelka"),
                environment("NOVELKA_DB_USER", "novelka"),
                environment("NOVELKA_DB_PASSWORD", "novelka"));
    }

    public static Pipeline pipeline(DatabaseSession database, String model) {
        Map<String, OpenRouter> clients = new HashMap<>();
        for (String stage : List.of("analyze", "translate", "proofread")) {
            clients.put(
                    stage,
                    new OpenRouter(
                            database.calls(),
                            System.getenv("OPENROUTER_API_KEY"),
                            environment("NOVELKA_" + stage.toUpperCase(Locale.ROOT) + "_MODEL", model),
                            stage));
        }
        return new Pipeline(
                database,
                (job, segment, stage, glossary, payload, budget) ->
                        clients.get(stage).generate(job, segment, stage, glossary, payload, budget),
                Integer.parseInt(environment("NOVELKA_SEGMENT_CHARS", "1500")));
    }
}
