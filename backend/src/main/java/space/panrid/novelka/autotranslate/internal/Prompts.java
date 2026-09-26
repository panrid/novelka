package space.panrid.novelka.autotranslate.internal;

import java.util.List;
import java.util.Map;

/**
 * Instructions and answer schemas. Everything the model must return is described by a
 * strict JSON schema, so a missing paragraph is caught by the code, not by a reader.
 */
final class Prompts {

    private Prompts() {
    }

    static final String NAMES = """
            Japanese names go into Ukrainian by the Ukrainian system (Коваленко): し → сі, ち → ті, つ → цу,
            じ → дзі, しゃ → ся, ちゃ → тя, じゃ → дзя, ふ → фу, を → о, ん → н (м before б/п/м is not used);
            long vowels are not doubled (Ōsaka → Осака, Yūki → Юкі). Names written in katakana that come from
            other languages take their usual Ukrainian form (アリス → Аліса, ルイ → Луї).
            Honorifics (-san, -sama, -kun, -chan, -dono) are kept as -сан, -сама, -кун, -тян, -доно.""";

    static final String METADATA = """
            You translate the page of a Japanese web novel into Ukrainian for a reading site.
            Return the novel's title, the author's pen name and the description in natural literary Ukrainian.
            Keep the description's paragraph breaks as blank lines. Do not add anything that is not in the original.
            """ + NAMES;

    static final String ANALYZE = """
            You prepare the glossary for translating a Japanese web novel into Ukrainian.
            From the given text list proper names (characters, places, organizations) and recurring special terms
            (skills, titles, magic, races, items) that are NOT in the given glossary yet.
            For each: the original form exactly as written in the text, its reading in hiragana if known (else ""),
            the Ukrainian form, the kind, the gender for characters if the text shows it (else "unknown"),
            and a short Ukrainian note (who or what it is, at most one sentence). Never guess a reading or gender.
            The Ukrainian form is exactly what the translation will write: ONE form, no parentheses, no
            alternatives, no romaji. Transliterate only proper names; skills, spells, items, titles and other
            meaningful terms are translated into Ukrainian words (鑑定スキル → навичка оцінки, 氷の槍 → Крижаний спис,
            火打ち石 → кресало). A part of a name already listed (a given name, a surname) is not a new person: add
            it as the same person only if the glossary lacks it, with the same Ukrainian name. Skip everyday words
            that need no fixed translation.
            Also translate the chapter title when one is given, WITHOUT its numbering: drop 第3話, 其の三, 003, 3.,
            Episode 3 and the like and return only the name (the site shows the number itself). Keep words that are
            the name, such as Пролог, Епілог, Інтерлюдія, Побічна історія. If the title is only a number or none
            is given, return "".
            Readings may appear in the text as 漢字《かんじ》; they are hints, not part of the name.
            Entries listed as «#number → Ukrainian» are already in the glossary but have no form in this text's
            language yet. When the text names one of them, return it in «known» with its number and the form
            exactly as written here, and do not list it again among the new entries.
            """ + NAMES;

    static final String TRANSLATE = """
            You are a literary translator from Japanese into Ukrainian. Translate the chapter part into natural,
            fluent Ukrainian prose that reads as if written in Ukrainian: keep the meaning, tone, humour and the
            voices of the characters; restructure sentences where Japanese syntax would sound foreign.
            Rules:
            - Return every block id exactly once, in the same order, each with its full translation. Never merge,
              split, skip or add blocks.
            - Each block is translated only from its own original text: never move words or sentences into
              another block, even when a sentence goes on in the next block.
            - For each block also return «start»: the first 4 characters of that block's original text, copied
              exactly. It shows the translation stayed with its own line.
            - Use the glossary forms for names and terms, declined by Ukrainian grammar. Keep gender agreement.
            - Readings written as 漢字《かんじ》 are hints; do not put them in the translation.
            - Dialogue in 「」 becomes Ukrainian dialogue with a dash (— Так, — сказав він.) or «» for quotes inside text.
            - Onomatopoeia becomes Ukrainian onomatopoeia or a short description.
            - Blocks of type preface and afterword are the author's notes; translate them fully too, including
              announcements, dates and thanks.
            - No notes, no comments, no untranslated Japanese.
            Also write a short summary (2–4 sentences, Ukrainian) of what happens in this part, for the next part's context.
            """ + NAMES;

    static final String PROOFREAD = """
            You are a Ukrainian literary editor. You get the Japanese original and its Ukrainian draft translation,
            block by block. Fix mistranslations, omissions, unnatural phrasing, wrong names (use the glossary),
            gender agreement, punctuation and typos. Keep what is already good; do not rewrite for taste.
            Return every block id exactly once, in the same order, with the final Ukrainian text, and «start»:
            the first 4 characters of that block's original, copied exactly. Never move text between blocks.
            Readings written as 漢字《かんじ》 are hints; they never appear in the translation.""";

    // ---- schemas ----------------------------------------------------------------------------

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
                "required", properties.keySet().stream().sorted().toList(), "additionalProperties", false);
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    static Map<String, Object> metadataSchema() {
        return object(Map.of("title", string(), "author", string(), "description", string()));
    }

    static Map<String, Object> analyzeSchema() {
        Map<String, Object> entry = object(Map.of(
                "original", string(), "reading", string(), "ukrainian", string(),
                "kind", Map.of("type", "string", "enum", List.of("character", "place", "organization", "term", "other")),
                "gender", Map.of("type", "string", "enum", List.of("male", "female", "unknown")),
                "note", string()));
        Map<String, Object> known = object(Map.of("id", Map.of("type", "integer"), "original", string()));
        return object(Map.of("title", string(), "entries", Map.of("type", "array", "items", entry),
                "known", Map.of("type", "array", "items", known)));
    }

    static Map<String, Object> blocksSchema(boolean withSummary) {
        Map<String, Object> block = object(Map.of("id", string(), "start", string(), "text", string()));
        Map<String, Object> blocks = Map.of("type", "array", "items", block);
        return withSummary ? object(Map.of("blocks", blocks, "summary", string())) : object(Map.of("blocks", blocks));
    }
}
