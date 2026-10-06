package space.panrid.novelka.suggestion;

import java.util.List;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.text.Span;

/** Suggestions made by the site on someone's behalf, such as a glossary word changed in every chapter (етап 17). */
public interface Suggestions {

    /** A paragraph of a published chapter as it should read. */
    record BlockChange(int chapterNumber, String blockId, List<Span> proposed) {
    }

    /**
     * The changes as one batch from {@code author}: sent to the team for review, or, with
     * {@code acceptNow}, accepted at once by the author (who must be able to edit the text),
     * chapter by chapter, so every change still shows in the chapter's history.
     *
     * @return how many paragraphs were changed or proposed
     */
    int propose(Viewer author, long editionId, List<BlockChange> changes, String note, boolean acceptNow);
}
