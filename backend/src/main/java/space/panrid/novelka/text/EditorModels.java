package space.panrid.novelka.text;

import java.time.OffsetDateTime;
import java.util.List;

import space.panrid.novelka.platform.text.Block;

/** Shapes the Studio works with. */
public final class EditorModels {

    private EditorModels() {
    }

    public record Draft(String title, List<Block> blocks, Long baseRevisionId, OffsetDateTime updatedAt) {
    }

    /** One chapter as the editor opens it: the published text and, if any, my unsaved draft. */
    /** @param label the number readers see (see ChapterLabels); null means the position */
    public record EditorState(long chapterId, int number, String title, List<Block> blocks, Long revisionId,
            boolean published, Draft draft, String label) {
    }

    public record StudioChapter(int number, String title, boolean published, boolean hasMyDraft,
            OffsetDateTime updatedAt, String label) {
    }

    public record RevisionInfo(long id, String authorNick, String origin, OffsetDateTime createdAt,
            int blocksChanged, int charsChanged, boolean published) {
    }

    public record RevisionText(long id, String title, List<Block> blocks, String parentTitle, List<Block> parentBlocks) {
    }

    public record CurrentText(long chapterId, int number, long revisionId, String title, List<Block> blocks) {
    }

    public record Contribution(String nick, int revisions, int blocksChanged, int charsChanged) {
    }
}
