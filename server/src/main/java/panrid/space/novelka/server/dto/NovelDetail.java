package panrid.space.novelka.server.dto;

import java.util.List;

public record NovelDetail(String id, String title, String author, String description, int chapterCount,
        List<ChapterSummary> chapters) {
}
