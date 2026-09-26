package space.panrid.novelka.source;

import java.util.List;

/**
 * A novel as its site describes it, in the original language.
 *
 * @param language  ISO 639-1 code of the original: {@code ja}, {@code en}…
 * @param completed the author finished it
 */
public record SourceNovel(SourceLink link, String language, String title, String author, String story, boolean completed,
        List<SourceEntry> chapters) {

    /** The last chapter that can be taken: locked ones at the end do not count. */
    public int lastAvailable() {
        return chapters.stream().filter(SourceEntry::available).mapToInt(SourceEntry::number).max().orElse(0);
    }
}
