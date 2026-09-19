package panrid.space.novelka.core.model;

import java.util.List;

public record Glossary(long revision, List<Entry> entries) {
}
