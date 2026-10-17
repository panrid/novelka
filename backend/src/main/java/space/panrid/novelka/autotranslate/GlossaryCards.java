package space.panrid.novelka.autotranslate;

import java.util.List;

/**
 * «Хто це» for readers: the glossary entries a selected bit of text names, with what the novel
 * has told about them by the reader's chapter — nothing from later chapters. Where the chapter's
 * original is on the site, an entry counts only in a paragraph whose original names it («до» in
 * «до монети» is not the kendo strike 胴); the team ties and unties words by hand on top of that.
 */
public interface GlossaryCards {

    /**
     * @param kind  character, place, organization, term or other
     * @param facts    what is known by the chapter, in the order the novel told it
     * @param mentions how often the novel named it by the chapter (all its names); 0 where not counted
     */
    record Card(long id, String ukrainian, String kind, String gender, List<String> facts, int mentions) {

        public Card(long id, String ukrainian, String kind, String gender, List<String> facts) {
            this(id, ukrainian, kind, gender, facts, 0);
        }
    }

    /** How «Персонажі» are ordered: the most named first, by name, or in the order the novel met them. */
    enum Sort {
        MENTIONS, NAME, APPEARANCE
    }

    /**
     * A word the reader's «лампочка» marks in the text: the reader may ask who it is.
     *
     * @param looseForms found as before: any ending and any case (a short name that declines)
     */
    record Term(long id, String ukrainian, String kind, boolean looseForms) {
    }

    /**
     * An entry in one paragraph: by its own Ukrainian forms ({@code text} null — the original of
     * the paragraph names it), or by the words the team tied to it; {@code linked} false — the
     * team untied those words from it there.
     */
    record BlockLink(String blockId, long entryId, String text, boolean linked) {
    }

    /**
     * What the lamp marks in a chapter.
     *
     * @param byOriginal the chapter's original is on the site: an entry counts only in the
     *                   paragraphs {@code links} name; otherwise wherever its words stand
     */
    record Hints(boolean byOriginal, List<Term> terms, List<BlockLink> links) {
    }

    /**
     * «Персонажі» on the novel page: the characters the novel has met by {@code chapter}, each with
     * what it told by then and how often it named them; {@code chapter} null — all of them, with
     * everything (spoilers, the reader asked for it).
     */
    List<Card> characters(long editionId, Integer chapter, Sort sort);

    /** How many characters the glossary has: the novel page shows «Персонажі» when there are some. */
    int characterCount(long editionId);

    /** Entries the novel has met by {@code chapter} and told something about (a note or a fact). */
    List<Term> known(long editionId, int chapter);

    Hints hints(long editionId, int chapter);

    /**
     * Entries named in {@code selection}, the longest names first; at most a few. In paragraph
     * {@code blockId} (when given) only those the paragraph is tied to, as the lamp marks them.
     */
    List<Card> named(long editionId, int chapter, String selection, String blockId);
}
