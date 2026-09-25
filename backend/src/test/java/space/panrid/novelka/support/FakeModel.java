package space.panrid.novelka.support;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import space.panrid.novelka.ai.AiTransport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A model that «translates» by rule, costs $0.001 per call and can be told to fail the
 * next calls in a given way. Never talks to OpenRouter.
 */
public class FakeModel implements AiTransport {

    public enum Trouble { NONE, RATE_LIMIT, LOST, DROP_BLOCK, GARBLE, CUT, NO_CREDITS }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Deque<Trouble> troubles = new ConcurrentLinkedDeque<>();
    /** Schema name of every request that reached the «provider». */
    public final List<String> calls = new CopyOnWriteArrayList<>();
    /** How many blocks each translation request carried. */
    public final List<Integer> translatedBlocks = new CopyOnWriteArrayList<>();

    public void troubleNext(Trouble... next) {
        troubles.addAll(List.of(next));
    }

    public void reset() {
        troubles.clear();
        calls.clear();
        translatedBlocks.clear();
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public Reply credits() {
        return new Reply(200, "{\"data\":{\"total_credits\":10,\"total_usage\":2.5}}");
    }

    @Override
    public Reply chat(String body) throws NotSent, Lost {
        JsonNode request = JSON.readTree(body);
        String schema = request.path("response_format").path("json_schema").path("name").asString("");
        String user = request.path("messages").path(1).path("content").asString();
        calls.add(schema);
        Trouble trouble = troubles.poll();
        if (trouble == Trouble.RATE_LIMIT) {
            return new Reply(429, "{\"error\":{\"message\":\"rate limited\"}}");
        }
        if (trouble == Trouble.NO_CREDITS) {
            return new Reply(402, "{\"error\":{\"message\":\"no credits\"}}");
        }
        if (trouble == Trouble.LOST) {
            throw new Lost("timeout");
        }
        if (request.has("modalities")) {
            String picture = java.util.Base64.getEncoder().encodeToString(Pictures.png(400, 500, java.awt.Color.CYAN));
            return new Reply(200, JSON.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", "",
                            "images", List.of(Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + picture)))))),
                    "usage", Map.of("prompt_tokens", 60, "completion_tokens", 1290, "cost", 0.039))));
        }
        Object answer = switch (schema) {
            case "illustration_prompt" -> Map.of("prompt", "A young man by a lighthouse at night, lantern light, "
                    + (user.contains("light novel") ? "light novel style" : "no style"));
            case "novel" -> Map.of("title", "Ліхтарник із туману", "author", "Сакура Юкі",
                    "description", "Перший абзац опису.\n\nДругий абзац опису.");
            case "glossary" -> Map.of(
                    // Like a real model, it sometimes keeps the number the site must strip.
                    "title", user.contains("Chapter title: (none)") ? "" : user.contains("閑話") ? "Інтерлюдія" : "Глава 9. Світло",
                    "entries", user.contains("ユキ") && !user.contains("→ Юкі")
                            ? List.of(Map.of("japanese", "ユキ", "reading", "ゆき", "ukrainian", "Юкі", "kind", "character",
                                    "gender", "female", "note", "Головна героїня."))
                            : List.of());
            case "translation" -> {
                List<Map<String, String>> blocks = new ArrayList<>();
                boolean known = user.contains("→ Юкі");
                for (JsonNode block : JSON.readTree(user.substring(user.indexOf("Blocks to translate (JSON):\n") + 28))) {
                    blocks.add(Map.of("id", block.path("id").asString(),
                            "text", (known ? "Юкі: " : "") + "переклад " + block.path("id").asString()));
                }
                translatedBlocks.add(blocks.size());
                if (trouble == Trouble.DROP_BLOCK) {
                    blocks.removeLast();
                }
                if (trouble == Trouble.GARBLE) {
                    blocks.addFirst(Map.of("id", "x1", "text", "чужий абзац"));
                }
                yield Map.of("blocks", blocks, "summary", "Коротко про частину.");
            }
            case "proofread" -> {
                List<Map<String, String>> blocks = new ArrayList<>();
                for (JsonNode block : JSON.readTree(user.substring(user.indexOf("Blocks (JSON):\n") + 15))) {
                    blocks.add(Map.of("id", block.path("id").asString(), "text", block.path("draft").asString() + " ✓"));
                }
                if (trouble == Trouble.DROP_BLOCK) {
                    blocks.removeLast();
                }
                yield Map.of("blocks", blocks);
            }
            default -> Map.of();
        };
        String content = JSON.writeValueAsString(answer);
        return new Reply(200, JSON.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content),
                        "finish_reason", trouble == Trouble.CUT ? "length" : "stop")),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", 50, "cost", 0.001))));
    }
}
