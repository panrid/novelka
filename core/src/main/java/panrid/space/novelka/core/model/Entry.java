package panrid.space.novelka.core.model;

import java.util.List;

public record Entry(
        String key,
        String kind,
        String japanese,
        String reading,
        String ukrainian,
        List<String> aliases,
        String gender,
        String facts,
        String certainty,
        int sourceChapter,
        boolean manual) {
}
