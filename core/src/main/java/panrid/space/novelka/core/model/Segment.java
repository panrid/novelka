package panrid.space.novelka.core.model;

import java.util.List;

public record Segment(
    List<Block> source, List<Block> draft, List<Block> revised, String context, String state) {}
