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
            other languages take their usual Ukrainian form (アリス → Аліса, ルイ → Луї); a katakana word borrowed
            from English is written as the English word sounds, not syllable by syllable (ゴッズ → Ґодз,
            レイス → рейф).
            Honorifics (-san, -sama, -kun, -chan, -dono) are not kept: a name is written without them.""";

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
            alternatives, no romaji, no honorific. Transliterate only proper names; skills, spells, items, titles
            and other meaningful terms are translated into Ukrainian words (鑑定スキル → навичка оцінки,
            氷の槍 → Крижаний спис, 火打ち石 → кресало). Capital letters only for names of people, places,
            organizations and named spells, skills and items; races, monsters, animals, minerals, materials and
            common terms are lowercase (ゴブリン → ґоблін, 雷鉱石 → громовий камінь). A part of a name already listed
            (a given name, a surname) is not a new person: add it as the same person only if the glossary lacks it,
            with the same Ukrainian name. Skip everyday words that need no fixed translation, family words
            (父さん, 姉さん) and single characters used as ordinary words (水, 火).
            Say who tells the text in the first person (僕, 俺, 私, あたし): «narrator» is their Ukrainian name as in
            the glossary, or "" when the text is told in the third person or the narrator is not clear, and their
            gender (僕 and 俺 usually mean a male narrator; else "unknown" unless the text shows it).
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
            - Use the glossary forms for names and terms, declined by Ukrainian grammar. Keep gender agreement,
              also for the narrator when the request says who tells the text.
            - A name in direct address takes the vocative case (Сіоне, Ґрасте). Honorifics are not kept: polite
              address to an adult becomes пане / пані + name or just the name; -ちゃん from a parent becomes an
              affectionate form (Сіончику) or the plain name.
            - Family words spoken or narrated by the family become тато, мама, сестра, брат, бабуся, дідусь
              (父さん, お父様 → тато; 母さん, お母様 → мама); not батько, мати.
            - Readings written as 漢字《かんじ》 are hints; do not put them in the translation.
            - Speech in 「」 becomes a dialogue line starting with a dash (— Так.). When one speech goes on over several
              blocks, each of those blocks starts with a dash. Quotes, titles and recalled words in 『』 become «».
              Thoughts stay as the original marks them.
            - Never add words the original does not have: no speaker tags (— сказав він, — запитала вона), no
              explanations. Speaker tags the original has are kept.
            - The original may have a typo (a wrong character with the same reading); translate what the author meant.
            - Write Ukrainian, not Russian calques: ліхтар (not фонар), крамниця (a shop, not магазин), трохи (not
              немножко).
            - Onomatopoeia becomes Ukrainian onomatopoeia or a short description.
            - Blocks of type preface and afterword are the author's notes; translate them fully too, including
              announcements, dates and thanks.
            - No notes, no comments, no untranslated Japanese.
            Also write a short summary (2–4 sentences, Ukrainian) of what happens in this part, for the next part's context.
            """ + NAMES;

    static final String PROOFREAD = """
            You are a Ukrainian literary editor. You get the Japanese original and its Ukrainian draft translation,
            block by block. First check that each block's draft translates its own original and not a neighbour's:
            if the drafts have slipped by a line, put each translation back with its own original. Then fix
            mistranslations (meaning turned around, wrong speaker, wrong subject), omissions, words the original
            does not have (added speaker tags such as «— сказав він»), wrong names (use the glossary), gender
            agreement (also the narrator's, when the request names them), the vocative case in address,
            honorifics left in (-сан, -кун, -чан: remove them), a dash at the start of every block of a speech in
            「」, punctuation, typos and Russian calques. Keep what is already right; do not reword good sentences.
            Return every block id exactly once, in the same order, with the final Ukrainian text, and «start»:
            the first 4 characters of that block's original, copied exactly. Never move text between blocks.
            Readings written as 漢字《かんじ》 are hints; they never appear in the translation.""";

    /** After a glossary word was changed in finished chapters: agreement only, nothing else. */
    static String agreement(String from, String to) {
        return """
                You are a Ukrainian copy editor. In these paragraphs of a translated novel the word or name «%s» was
                mechanically replaced by «%s», keeping the old endings, so the grammar may now be wrong.
                Fix only the grammatical agreement of «%s» and of the words that agree with it (case, gender, number,
                verb endings). Do not change anything else: no other words, no punctuation, no style.
                Return every id exactly once with its full text.""".formatted(from, to, to);
    }

    /** A character's gender changed in the glossary: only what refers to them follows. */
    static String gender(String name, String gender) {
        String now = "female".equals(gender) ? "a woman or a girl (feminine)" : "male".equals(gender) ? "a man or a boy (masculine)" : "of unknown gender";
        return """
                You are a Ukrainian copy editor. In these paragraphs of a translated novel the character «%s» is %s,
                but the text may treat them as the other gender. Fix only the words that refer to «%s»: verb endings in
                the past tense, adjectives, participles and pronouns. Do not change anything else: no other words or
                characters, no punctuation, no style. If a paragraph is already right, return it unchanged.
                Return every id exactly once with its full text.""".formatted(name, now, name);
    }

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
        Map<String, Object> narrator = object(Map.of("name", string(),
                "gender", Map.of("type", "string", "enum", List.of("male", "female", "unknown"))));
        return object(Map.of("title", string(), "entries", Map.of("type", "array", "items", entry),
                "known", Map.of("type", "array", "items", known), "narrator", narrator));
    }

    /** What the translation and the proofreading are told about the narrator; empty when analysis found none. */
    static String narrator(String name, String gender) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String grammar = switch (gender == null ? "" : gender) {
            case "male" -> " (male: past-tense verbs and adjectives about «я» are masculine — я думав, я радий)";
            case "female" -> " (female: past-tense verbs and adjectives about «я» are feminine — я думала, я рада)";
            default -> "";
        };
        return "The text is told in the first person by " + name.strip() + grammar + ".";
    }

    static Map<String, Object> blocksSchema(boolean withSummary) {
        Map<String, Object> block = object(Map.of("id", string(), "start", string(), "text", string()));
        Map<String, Object> blocks = Map.of("type", "array", "items", block);
        return withSummary ? object(Map.of("blocks", blocks, "summary", string())) : object(Map.of("blocks", blocks));
    }
}
