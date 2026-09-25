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
            For each: the Japanese form exactly as written in the text, its reading in hiragana if known (else ""),
            the Ukrainian form, the kind, the gender for characters if the text shows it (else "unknown"),
            and a short Ukrainian note (who or what it is, at most one sentence). Never guess a reading or gender.
            Also translate the chapter title when one is given; otherwise return "".
            Readings may appear in the text as 漢字《かんじ》; they are hints, not part of the name.
            """ + NAMES;

    static final String TRANSLATE = """
            You are a literary translator from Japanese into Ukrainian. Translate the chapter part into natural,
            fluent Ukrainian prose that reads as if written in Ukrainian: keep the meaning, tone, humour and the
            voices of the characters; restructure sentences where Japanese syntax would sound foreign.
            Rules:
            - Return every block id exactly once, in the same order, each with its full translation. Never merge,
              split, skip or add blocks.
            - Use the glossary forms for names and terms, declined by Ukrainian grammar. Keep gender agreement.
            - Readings written as 漢字《かんじ》 are hints; do not put them in the translation.
            - Dialogue in 「」 becomes Ukrainian dialogue with a dash (— Так, — сказав він.) or «» for quotes inside text.
            - Onomatopoeia becomes Ukrainian onomatopoeia or a short description.
            - No notes, no comments, no untranslated Japanese.
            Also write a short summary (2–4 sentences, Ukrainian) of what happens in this part, for the next part's context.
            """ + NAMES;

    static final String PROOFREAD = """
            You are a Ukrainian literary editor. You get the Japanese original and its Ukrainian draft translation,
            block by block. Fix mistranslations, omissions, unnatural phrasing, wrong names (use the glossary),
            gender agreement, punctuation and typos. Keep what is already good; do not rewrite for taste.
            Return every block id exactly once, in the same order, with the final Ukrainian text.
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
                "japanese", string(), "reading", string(), "ukrainian", string(),
                "kind", Map.of("type", "string", "enum", List.of("character", "place", "organization", "term", "other")),
                "gender", Map.of("type", "string", "enum", List.of("male", "female", "unknown")),
                "note", string()));
        return object(Map.of("title", string(), "entries", Map.of("type", "array", "items", entry)));
    }

    static Map<String, Object> blocksSchema(boolean withSummary) {
        Map<String, Object> block = object(Map.of("id", string(), "text", string()));
        Map<String, Object> blocks = Map.of("type", "array", "items", block);
        return withSummary ? object(Map.of("blocks", blocks, "summary", string())) : object(Map.of("blocks", blocks));
    }
}
