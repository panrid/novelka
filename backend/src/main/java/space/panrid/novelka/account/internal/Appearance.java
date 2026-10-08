package space.panrid.novelka.account.internal;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * How a person wants the site and the reader to look (етап 18): {@code {"site": {...}, "siteOwn": {...},
 * "reader": {...}, "readerOwn": {...}}} — the own ones wait while ready styles are tried. Only the choices the site offers get in: known keys, values from their
 * lists or ranges, colours as #rrggbb. Nothing of the person's own (files, fonts, styles).
 */
final class Appearance {

    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> FONTS = Set.of("inter", "rubik", "nunito", "plex", "comfortaa", "jetbrains",
            "literata", "lora", "ptserif", "merriweather", "playfair");

    private static final Map<String, Predicate<JsonNode>> SITE = Map.ofEntries(
            Map.entry("preset", oneOf("default", "light", "bookish", "parchment", "night", "contrast", "minimal",
                    "sakura", "ocean", "forest", "neon", "terminal")),
            Map.entry("custom", JsonNode::isBoolean),
            Map.entry("base", oneOf("light", "dark")),
            Map.entry("bg", whole(0, 9)),
            Map.entry("accent", Appearance::color),
            Map.entry("ui", oneOf(FONTS)),
            Map.entry("head", oneOf(FONTS)),
            Map.entry("scale", number(0.85, 1.25)),
            Map.entry("icons", oneOf("lucide", "tabler", "phosphor", "iconoir", "heroicons")),
            Map.entry("iconWeight", number(1, 2.5)),
            Map.entry("iconColor", oneOf("text", "accent", "muted")),
            Map.entry("iconFill", JsonNode::isBoolean),
            Map.entry("radius", whole(0, 24)),
            Map.entry("density", oneOf("compact", "normal", "airy")),
            Map.entry("cards", oneOf("flat", "border", "shadow")),
            Map.entry("nav", oneOf("top", "side")),
            Map.entry("catalog", oneOf("rows", "grid", "shelf")),
            Map.entry("anim", oneOf("system", "full", "light", "off")));

    private static final Map<String, Predicate<JsonNode>> READER = Map.ofEntries(
            Map.entry("preset", oneOf("site", "paper", "sepia", "gray", "night", "black", "book", "dyslexia")),
            Map.entry("custom", JsonNode::isBoolean),
            Map.entry("mode", oneOf("scroll", "pages")),
            Map.entry("pageAnim", oneOf("none", "slide", "fade", "curl")),
            Map.entry("font", oneOf(FONTS)),
            Map.entry("size", whole(14, 28)),
            Map.entry("lineHeight", number(1.2, 2.2)),
            Map.entry("align", oneOf("left", "justify")),
            Map.entry("paragraphs", oneOf("gap", "indent")),
            Map.entry("width", oneOf("narrow", "medium", "wide", "full")),
            Map.entry("margin", whole(0, 48)),
            Map.entry("colors", oneOf("site", "paper", "sepia", "gray", "night", "black", "tea", "dusk", "own")),
            Map.entry("bg", Appearance::color),
            Map.entry("text", Appearance::color),
            Map.entry("taps", oneOf("sides", "forward", "vertical", "none")),
            Map.entry("accent", Appearance::color),
            Map.entry("hideBars", JsonNode::isBoolean),
            Map.entry("clock", JsonNode::isBoolean),
            Map.entry("percent", JsonNode::isBoolean),
            Map.entry("awake", JsonNode::isBoolean));

    private Appearance() {
    }

    /** The settings as they will be stored, or an error naming what the site does not offer. */
    static ObjectNode checked(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw UserFacingException.badRequest("Налаштування вигляду мають бути об'єктом.");
        }
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (String section : body.propertyNames()) {
            Map<String, Predicate<JsonNode>> rules = switch (section) {
                // «Свій стиль» kept aside while the person tries ready ones.
                case "site", "siteOwn" -> SITE;
                case "reader", "readerOwn" -> READER;
                default -> throw UserFacingException.badRequest("Невідомий розділ вигляду «%s».".formatted(section));
            };
            JsonNode values = body.get(section);
            if (!values.isObject()) {
                throw UserFacingException.badRequest("Розділ «%s» має бути об'єктом.".formatted(section));
            }
            ObjectNode kept = out.putObject(section);
            for (String key : values.propertyNames()) {
                Predicate<JsonNode> rule = rules.get(key);
                if (rule == null || !rule.test(values.get(key))) {
                    throw UserFacingException.badRequest("Такого варіанта вигляду немає: %s.%s.".formatted(section, key));
                }
                kept.set(key, values.get(key));
            }
        }
        return out;
    }

    private static Predicate<JsonNode> oneOf(String... values) {
        return oneOf(Set.of(values));
    }

    private static Predicate<JsonNode> oneOf(Set<String> values) {
        return node -> node.isString() && values.contains(node.asString());
    }

    private static Predicate<JsonNode> whole(int min, int max) {
        return node -> node.isIntegralNumber() && node.asInt() >= min && node.asInt() <= max;
    }

    private static Predicate<JsonNode> number(double min, double max) {
        return node -> node.isNumber() && node.asDouble() >= min && node.asDouble() <= max;
    }

    private static boolean color(JsonNode node) {
        return node.isString() && COLOR.matcher(node.asString()).matches();
    }
}
