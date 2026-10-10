package space.panrid.novelka.text;

import java.util.List;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.text.EditorModels.Contribution;
import space.panrid.novelka.text.EditorModels.EditorState;
import space.panrid.novelka.text.EditorModels.RevisionInfo;
import space.panrid.novelka.text.EditorModels.RevisionText;
import space.panrid.novelka.text.EditorModels.StudioChapter;

/** Chapter text for the Studio. Callers check team rights first (AccessPolicy). */
public interface Chapters {

    /**
     * Adds chapters after the edition's last one and publishes them at once,
     * each as its first revision.
     *
     * @return numbers given to the chapters, in order
     */
    List<Integer> publishNew(long editionId, List<ChapterFiles.ParsedChapter> chapters, String origin, Long authorId);

    /**
     * Publishes a machine translation of an original chapter as a new revision, creating the
     * chapter under that number if it does not exist yet. Pictures must already be stored.
     */
    long publishMachine(long editionId, int number, String label, String title, List<Block> blocks,
            long sourceChapterId, int sourceChars, long jobId, String sourceHash);

    /**
     * The number readers see: «0», «31.1»; empty for none («Пролог»); null for the position.
     */
    void setLabel(long editionId, int number, String label);

    /**
     * Removes a chapter with its history, drafts and suggestions. A chapter readers never saw
     * goes any time; a published one only if it is the last, so no hole opens in the middle.
     */
    void deleteChapter(long editionId, int number);

    /** A new empty chapter at the end, unpublished until its first «Опублікувати». */
    int createChapter(long editionId);

    EditorState editorState(long editionId, int number, long accountId);

    void saveDraft(long editionId, int number, long accountId, String title, List<Block> blocks, Long baseRevisionId);

    void discardDraft(long editionId, int number, long accountId);

    /**
     * Publishes the text as a new revision. Fails with 409 if someone else published the
     * chapter after {@code baseRevisionId}, so no one silently overwrites a colleague.
     *
     * @param mayAddPictures whether the author may add pictures that are not in the current text
     */
    long publish(long editionId, int number, long accountId, String title, List<Block> blocks, Long baseRevisionId,
            boolean mayAddPictures);

    /**
     * Publishes a machine-proofread text of a published chapter as a new revision, if the chapter
     * still has {@code baseRevisionId}: a person who published meanwhile is never overwritten.
     *
     * @return the new revision, or null when the chapter changed since
     */
    Long publishProofread(long editionId, int number, String title, List<Block> blocks, long baseRevisionId, long jobId);

    /** The published text of a chapter, or 404 if it has none. */
    EditorModels.CurrentText current(long editionId, int number);

    /**
     * Publishes a revision made from other people's suggestions: one revision for the whole
     * batch, each suggestion's author credited with their share.
     */
    long publishFromSuggestions(long editionId, int number, String title, List<Block> blocks, long baseRevisionId,
            long reviewerId, java.util.Map<Long, ChangeStats> credits);

    /**
     * The team's chapters, a page at a time.
     *
     * @param filter «suggestions» (with readers' suggestions waiting), «drafts» (the person's drafts),
     *               «unpublished»; anything else: all
     */
    EditorModels.StudioChapters studioChapters(long editionId, long accountId, String filter, boolean oldestFirst, int page, int size);

    List<RevisionInfo> revisions(long editionId, int number);

    RevisionText revision(long editionId, int number, long revisionId);

    List<Contribution> contributions(long editionId);
}
