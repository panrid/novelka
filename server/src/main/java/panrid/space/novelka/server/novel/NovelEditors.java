package panrid.space.novelka.server.novel;

import java.util.List;
import java.util.Map;

/** Who decides on corrections of a novel. {@code owner} is null for novels imported from the CLI (administrators manage them). */
public record NovelEditors(Map<String, Object> owner, boolean openReview, List<Map<String, Object>> editors) {
}
