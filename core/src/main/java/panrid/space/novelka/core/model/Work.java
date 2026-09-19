package panrid.space.novelka.core.model;

import java.util.List;

public record Work(
    String id,
    String novelId,
    int chapter,
    String sourceHash,
    int revision,
    List<Segment> segments,
    String state,
    String summary) {}
