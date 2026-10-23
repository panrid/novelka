package space.panrid.novelka.platform;

import java.util.List;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Каркас (the site's build: menus, fonts, shapes) and тема (colours) — two separate choices
 * (doc/redesign/README.md, рішення 30–33, 38). A person's own choice lives in their appearance; this
 * is the site's own, for guests and for those who never chose: Пульт · Сланець unless the owner
 * picked another pair.
 */
@Component
public class SiteLook {

    /** Пульт · Звичний · Палітурка · Обкладинка. */
    public static final List<String> FRAMES = List.of("pult", "zv", "pal", "obl");
    /** The four of the new design, then the colours of the site's twelve styles («Звичайний» is Графіт). */
    public static final List<String> THEMES = List.of("slanets", "graphite", "sukno", "slyva", "light", "bookish",
            "parchment", "night", "contrast", "minimal", "sakura", "ocean", "forest", "neon", "terminal");
    /** «Свої кольори»: a person's, never the site's default. */
    public static final String OWN_THEME = "own";

    static final String KEY = "look.default";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Default(String frame, String theme) {
    }

    private final SiteSettings settings;

    SiteLook(SiteSettings settings) {
        this.settings = settings;
    }

    public Default current() {
        String frame = "pult";
        String theme = "slanets";
        JsonNode stored = settings.json(KEY).map(JSON::readTree).orElse(null);
        if (stored != null && stored.isObject()) {
            if (FRAMES.contains(stored.path("frame").asString(""))) frame = stored.path("frame").asString();
            if (THEMES.contains(stored.path("theme").asString(""))) theme = stored.path("theme").asString();
        }
        return new Default(frame, theme);
    }

    public void save(String frame, String theme, long changedBy) {
        if (!FRAMES.contains(frame)) {
            throw UserFacingException.badRequest("Такого каркаса немає.");
        }
        if (!THEMES.contains(theme)) {
            throw UserFacingException.badRequest("Такої теми немає.");
        }
        settings.put(KEY, JsonNodeFactory.instance.objectNode().put("frame", frame).put("theme", theme).toString(), changedBy);
    }
}
