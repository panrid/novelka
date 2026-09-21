package panrid.space.novelka.server.dto;

import panrid.space.novelka.core.model.Entry;
import java.util.List;

public record GlossaryUpdate(long revision, List<Entry> entries) {
}
