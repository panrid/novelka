package panrid.space.novelka.cli.config;

import panrid.space.novelka.core.OpenRouter;
import panrid.space.novelka.core.Pipeline;
import panrid.space.novelka.core.Store;

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

    public static Store openStore() throws Exception {
        return new Store(
                environment("NOVELKA_DB_URL", "jdbc:postgresql://localhost:5432/novelka"),
                environment("NOVELKA_DB_USER", "novelka"),
                environment("NOVELKA_DB_PASSWORD", "novelka"));
    }

    public static Pipeline pipeline(Store store, String model) {
        Map<String, OpenRouter> clients = new HashMap<>();
        for (String stage : List.of("analyze", "translate", "proofread")) {
            clients.put(
                    stage,
                    new OpenRouter(
                            store,
                            System.getenv("OPENROUTER_API_KEY"),
                            environment("NOVELKA_" + stage.toUpperCase(Locale.ROOT) + "_MODEL", model),
                            stage));
        }
        return new Pipeline(
                store,
                (job, segment, stage, glossary, payload, budget) ->
                        clients.get(stage).generate(job, segment, stage, glossary, payload, budget),
                Integer.parseInt(environment("NOVELKA_SEGMENT_CHARS", "1500")));
    }
}
