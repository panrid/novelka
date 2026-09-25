package space.panrid.novelka.source;

/**
 * A novel as Syosetu describes it, in Japanese.
 *
 * @param chapters  number of episodes; a short story has one
 * @param serial    a serial with separate chapter pages, not a short story
 * @param completed the author finished it
 */
public record SourceNovel(SyosetuLink link, String title, String author, String story, int chapters, boolean serial,
        boolean completed) {
}
