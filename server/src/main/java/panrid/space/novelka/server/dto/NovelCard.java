package panrid.space.novelka.server.dto;

import java.util.List;

public record NovelCard(String id, String title, String author, String description, int chapterCount,
        int readyChapters, List<String> aliases, List<TagView> tags) {
}
