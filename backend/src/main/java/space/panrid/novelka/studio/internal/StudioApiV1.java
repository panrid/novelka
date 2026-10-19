package space.panrid.novelka.studio.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;

/**
 * The shapes of the Studio's API, {@code /api/v1/studio} (етап 22.3). Kept apart from the Studio's
 * own JSON on purpose: the site's pages may change freely, these change only by adding fields. A
 * translation is named as in the public API — the novel's {@code slug} and its team's handle.
 */
final class StudioApiV1 {

    private StudioApiV1() {
    }

    /** A list read by key: pass {@code next} as {@code after} for the following page; null on the last. */
    record Page<T>(List<T> items, String next) {
    }

    /**
     * A translation the person works on.
     *
     * @param kind     human, machine, mixed or original
     * @param status   ongoing, completed, paused or abandoned
     * @param role     the person's role in the team: owner, translator or editor
     * @param chapters published chapters
     */
    record Translation(String novel, String team, String teamName, String title, String kind, String status, String role,
            int chapters, int pendingSuggestions, int originals) {
    }

    /**
     * A chapter as the team sees it in the list.
     *
     * @param label the number readers see when it is not the position; null shows {@code number}
     * @param draft the person has an unpublished draft of it
     */
    record ChapterEntry(int number, String label, String title, boolean published, boolean draft) {
    }

    /**
     * A chapter's text for the team: what readers see ({@code revision}, null before the first
     * publication) and the person's own draft, if any.
     */
    record Chapter(int number, String label, String title, Long revision, boolean published, List<TextBlock> blocks, Draft draft) {
    }

    /** @param baseRevision the published revision the draft was started from */
    record Draft(String title, List<TextBlock> blocks, Long baseRevision, OffsetDateTime updatedAt) {
    }

    /**
     * A block of the text: heading, paragraph, preface, afterword, separator or image.
     *
     * @param imageId  the picture of an image block (from «Мої картинки»)
     * @param imageUrl where it is shown; ignored when sent
     */
    record TextBlock(String id, String type, List<TextSpan> content, Long imageId, String imageUrl) {
    }

    /** A run of text; marks are bold, italic, underline, strike. */
    record TextSpan(String text, List<String> marks) {
    }

    /**
     * Text to save as a draft or to publish.
     *
     * @param baseRevision the revision it was edited from: publishing over someone else's newer one is refused (409)
     */
    record Text(String title, List<TextBlock> blocks, Long baseRevision) {
    }

    record Published(int number, long revision) {
    }

    /**
     * A picture of «Мої картинки».
     *
     * @param drawn drawn on the site, not uploaded
     * @param uses  published chapters showing it
     */
    record Picture(long id, String url, String fullUrl, int width, int height, String fileName, boolean drawn, OffsetDateTime createdAt,
            List<PictureUse> uses) {
    }

    record PictureUse(String novel, int chapter) {
    }

    /**
     * Pictures to put into a translation.
     *
     * @param pictures each picture, with its place or without (then its file's name says it: «27-5.png»)
     */
    record Placement(List<Spot> pictures) {
    }

    /** @param after the paragraph the picture goes after; 0 — the very start of the chapter */
    record Spot(Long id, Integer chapter, Integer after) {
    }

    /** @param problem why the picture will not be placed, null when it will */
    record Place(long id, String fileName, Integer chapter, Integer after, String quote, String problem) {
    }

    /** @param skipped the pictures left out, each with its reason */
    record Placed(int chapters, int pictures, List<String> skipped) {
    }

    // ---- conversions ------------------------------------------------------------------------------

    static List<TextBlock> blocks(List<Block> blocks, Map<Long, StoredImage> pictures) {
        return blocks.stream().map(block -> new TextBlock(block.id(), block.type(),
                block.content().stream().map(span -> new TextSpan(span.text(), span.marks() == null ? List.of() : span.marks())).toList(),
                block.imageId(), block.imageId() == null || !pictures.containsKey(block.imageId()) ? null
                        : pictures.get(block.imageId()).url(1280))).toList();
    }

    /** What was sent, as the editor's blocks; the text rules check it where it is saved. */
    static List<Block> blocks(List<TextBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream().map(block -> new Block(block.id(), block.type() == null ? "paragraph" : block.type(),
                block.content() == null ? List.of() : block.content().stream()
                        .map(span -> new Span(span.text() == null ? "" : span.text(), span.marks() == null ? List.of() : span.marks()))
                        .toList(),
                block.imageId(), null)).toList();
    }

    static Picture picture(PictureLibrary.Picture picture) {
        return new Picture(picture.id(), picture.url(), picture.fullUrl(), picture.width(), picture.height(), picture.fileName(),
                picture.drawn(), picture.createdAt(), picture.uses().stream().map(use -> new PictureUse(use.novelSlug(), use.number())).toList());
    }
}
