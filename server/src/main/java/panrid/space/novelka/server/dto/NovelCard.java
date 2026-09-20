package panrid.space.novelka.server.dto;

import java.util.List;

public record NovelCard(String id, String title, String author, int chapterCount,
        int readyChapters, List<String> aliases) {
}
