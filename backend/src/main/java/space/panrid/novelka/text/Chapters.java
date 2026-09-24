package space.panrid.novelka.text;

import java.util.List;

public interface Chapters {

    /**
     * Adds chapters after the edition's last one and publishes them at once,
     * each as its first revision.
     *
     * @return numbers given to the chapters, in order
     */
    List<Integer> publishNew(long editionId, List<ChapterFiles.ParsedChapter> chapters, String origin, Long authorId);
}
