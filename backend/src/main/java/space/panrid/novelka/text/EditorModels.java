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

    /** @param pending readers' suggestions waiting for the team */
    public record StudioChapter(int number, String title, boolean published, boolean hasMyDraft,
            OffsetDateTime updatedAt, String label, int pending) {
    }

    public record StudioChapters(java.util.List<StudioChapter> items, int total, int page, boolean hasMore) {
    }

    /**
     * One version of a chapter in its history (етап 20, історія глави).
     *
     * @param stage     for AI: «translate» or «proofread»; null otherwise
     * @param model     for AI: the models of that stage; null otherwise
     * @param jobId     for AI: the run that made it
     * @param proposers for readers' правки: who proposed them; the reviewer who took them is {@code authorNick}
     * @param notes     what the правки said about themselves (a reader's reason, «Вичитка ШІ», a glossary change)
     */
    public record RevisionInfo(long id, String authorNick, String origin, OffsetDateTime createdAt,
            int blocksChanged, int charsChanged, boolean published, String stage, String model, Long jobId,
            List<String> proposers, List<String> notes) {
    }

    public record RevisionText(long id, String title, List<Block> blocks, String parentTitle, List<Block> parentBlocks) {
    }

    public record CurrentText(long chapterId, int number, long revisionId, String title, List<Block> blocks) {
    }

    public record Contribution(String nick, int revisions, int blocksChanged, int charsChanged) {
    }
}
