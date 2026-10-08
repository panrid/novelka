package space.panrid.novelka.catalog;

import java.util.List;

/**
 * What a novel is beyond its Ukrainian title: its other names and how the original stands.
 * Shared by every translation of the novel. All optional.
 *
 * @param titleOriginal      the name in the original language (Japanese for Syosetu)
 * @param altTitles          other names readers search by
 * @param sourceStatus       the original: {@code ongoing}, {@code completed} or {@code paused}; null if unknown
 * @param sourceChapterCount chapters in the original so far; null if unknown
 */
public record NovelFacts(String titleOriginal, String titleEnglish, List<String> altTitles, String sourceStatus,
        Integer sourceChapterCount) {
}
