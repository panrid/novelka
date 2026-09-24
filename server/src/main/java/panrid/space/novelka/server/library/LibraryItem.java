package panrid.space.novelka.server.library;

/** A novel on the reader's shelf with the chosen status. */
public record LibraryItem(String id, String title, String author, int chapterCount, int readyChapters, String status, String updatedAt) {
}
