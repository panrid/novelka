package panrid.space.novelka.core.model;

public record Novel(
        String id, String title, String author, String url, int chapterCount, boolean shortStory) {
}
