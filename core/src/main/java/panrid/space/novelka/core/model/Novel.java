package panrid.space.novelka.core.model;

public record Novel(
        String id,
        String title,
        String titleUk,
        String author,
        String authorUk,
        String descriptionUk,
        String url,
        int chapterCount,
        boolean shortStory) {
    public Novel(String id, String title, String titleUk, String author, String url, int chapterCount, boolean shortStory) {
        this(id, title, titleUk, author, null, null, url, chapterCount, shortStory);
    }

    public Novel(String id, String title, String author, String url, int chapterCount, boolean shortStory) {
        this(id, title, null, author, null, null, url, chapterCount, shortStory);
    }

    public String displayTitle() {
        return titleUk == null || titleUk.isBlank() ? title : titleUk;
    }

    public String displayAuthor() {
        return authorUk == null || authorUk.isBlank() ? author : authorUk;
    }
}
