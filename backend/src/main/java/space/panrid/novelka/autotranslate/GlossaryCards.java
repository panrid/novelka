package space.panrid.novelka.autotranslate;

import java.util.List;

/**
 * «Хто це» for readers: the glossary entries a selected bit of text names, with what the novel
 * has told about them by the reader's chapter — nothing from later chapters.
 */
public interface GlossaryCards {

    /**
     * @param kind  character, place, organization, term or other
     * @param facts what is known by the chapter, in the order the novel told it
     */
    record Card(long id, String ukrainian, String kind, String gender, List<String> facts) {
    }

    /** A word the reader's «лампочка» marks in the text: the reader may ask who it is. */
    /** @param looseForms found as before: any ending and any case (a short name that declines) */
    record Term(long id, String ukrainian, String kind, boolean looseForms) {
    }

    /** Entries the novel has met by {@code chapter} and told something about (a note or a fact). */
    List<Term> known(long editionId, int chapter);

    /** Entries named in {@code selection} (in any of their Ukrainian forms), the longest names first; at most a few. */
    List<Card> named(long editionId, int chapter, String selection);
}
