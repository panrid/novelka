package panrid.space.novelka.server.dto;

public record NovelDetail(String id, String title, String author, String description, int chapterCount,
        long readyChapters, Integer firstChapter, Integer resumeChapter, java.util.List<TagView> tags,
        panrid.space.novelka.server.vote.VoteSummary rating, String libraryStatus, String translator) {
}
