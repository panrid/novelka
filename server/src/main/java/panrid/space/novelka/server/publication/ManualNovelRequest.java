package panrid.space.novelka.server.publication;

import java.util.List;

/** A novel added without import: Ukrainian metadata is required, original metadata is optional. */
public record ManualNovelRequest(String titleUk, String title, String authorUk, String author, String descriptionUk, List<String> tags) {
}
