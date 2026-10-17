package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_FACT;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Хто діє в історії» for characters the glossary had before analysis started saying it: one ask
 * of the analysis model over the novel's characters with what is known of them. Acting ones stay
 * in «Персонажі»; monsters and people only mentioned keep their hints. The team corrects by hand.
 */
@Component
class StoryRoles {

    private static final int PER_CALL = 120;

    private final DSLContext db;
    private final Ai ai;
    private final Jobs jobs;
    private final Glossary glossary;
    private final JsonMapper json;

    StoryRoles(DSLContext db, Ai ai, Jobs jobs, Glossary glossary, JsonMapper json) {
        this.db = db;
        this.ai = ai;
        this.jobs = jobs;
        this.glossary = glossary;
        this.json = json;
    }

    /** {@code usd}: what the ask is expected to cost. */
    record Quote(int characters, BigDecimal usd) {
    }

    record Done(int characters, int acting, BigDecimal usd) {
    }

    private record Line(long id, String text) {
    }

    /** Main character entries not decided yet, each as one line for the model. */
    private List<Line> undecided(long editionId) {
        long novelId = glossary.scope(editionId).novelId();
        var entries = db.select(GLOSSARY_ENTRY.ID, GLOSSARY_ENTRY.UKRAINIAN, GLOSSARY_ENTRY.NOTE).from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(novelId), GLOSSARY_ENTRY.KIND.eq("character"), GLOSSARY_ENTRY.STATUS.ne("rejected"),
                        GLOSSARY_ENTRY.SAME_AS.isNull(), GLOSSARY_ENTRY.IN_STORY.isNull())
                .orderBy(GLOSSARY_ENTRY.SOURCE_CHAPTER.asc().nullsFirst(), GLOSSARY_ENTRY.ID).fetch();
        Map<Long, List<String>> facts = new HashMap<>();
        if (!entries.isEmpty()) {
            db.select(GLOSSARY_FACT.ENTRY_ID, GLOSSARY_FACT.FACT).from(GLOSSARY_FACT)
                    .where(GLOSSARY_FACT.ENTRY_ID.in(entries.map(r -> r.value1()))).orderBy(GLOSSARY_FACT.CHAPTER, GLOSSARY_FACT.ID)
                    .forEach(r -> facts.computeIfAbsent(r.value1(), key -> new ArrayList<>()).add(r.value2()));
        }
        return entries.map(r -> {
            StringBuilder line = new StringBuilder("#").append(r.value1()).append(' ').append(r.value2());
            if (r.value3() != null && !r.value3().isBlank()) {
                line.append(" — ").append(r.value3());
            }
            facts.getOrDefault(r.value1(), List.of()).stream().limit(3).forEach(fact -> line.append(' ').append(fact));
            return new Line(r.value1(), line.length() > 400 ? line.substring(0, 400) : line.toString());
        });
    }

    Quote quote(long editionId) {
        List<Line> lines = undecided(editionId);
        return new Quote(lines.size(), Settings.usdOfMicro(expected(lines)));
    }

    /** Reading costs about a token per three characters, the answer about fifteen tokens a character. */
    private long expected(List<Line> lines) {
        Settings.Stage model = jobs.settings().analyze();
        long chars = lines.stream().mapToLong(line -> line.text().length()).sum();
        long in = chars / 3 + 600L * Math.max(1, (lines.size() + PER_CALL - 1) / PER_CALL);
        long out = 15L * lines.size();
        return Math.round(in * model.inputPerMillion() + out * model.outputPerMillion());
    }

    Done run(long editionId) {
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        List<Line> lines = undecided(editionId);
        Settings.Stage model = jobs.settings().analyze();
        long spent = 0;
        int acting = 0;
        int decided = 0;
        try {
            for (int at = 0; at < lines.size(); at += PER_CALL) {
                List<Line> part = lines.subList(at, Math.min(lines.size(), at + PER_CALL));
                StringBuilder user = new StringBuilder("Characters:\n");
                part.forEach(line -> user.append(line.text()).append('\n'));
                AiAnswer answer = ai.ask(new AiRequest(model.model(), Prompts.STORY_ROLES, user.toString(), "story_roles",
                        Prompts.storyRolesSchema(), 1_000 + 30 * part.size(), model.price(), new AiTag(null, null, "glossary", at / PER_CALL), 0));
                spent += answer.costMicroUsd();
                java.util.Set<Long> asked = part.stream().map(Line::id).collect(java.util.stream.Collectors.toSet());
                for (JsonNode item : Pipeline.lenient(json, answer.content()).path("roles")) {
                    long id = item.path("id").asLong(0);
                    String role = item.path("role").asString("");
                    // Only numbers the model was shown, only roles it was offered.
                    if (!asked.contains(id) || !List.of("acting", "mentioned", "creature").contains(role)) {
                        continue;
                    }
                    boolean inStory = role.equals("acting");
                    db.update(GLOSSARY_ENTRY).set(GLOSSARY_ENTRY.IN_STORY, inStory).where(GLOSSARY_ENTRY.ID.eq(id), GLOSSARY_ENTRY.IN_STORY.isNull()).execute();
                    decided++;
                    acting += inStory ? 1 : 0;
                }
            }
        } catch (AiException error) {
            throw UserFacingException.badGateway(error.getMessage());
        }
        return new Done(decided, acting, Settings.usdOfMicro(spent));
    }
}
