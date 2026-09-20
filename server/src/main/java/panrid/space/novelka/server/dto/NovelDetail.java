package panrid.space.novelka.server.dto;

import java.util.List;

public record NovelDetail(String id, String title, String author, int chapterCount,
        List<ChapterSummary> chapters) {
}
