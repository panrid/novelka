package panrid.space.novelka.server.dto;

import panrid.space.novelka.core.model.Block;

import java.util.List;

public record ReaderChapter(String novelId, int number, int revision, String title, List<Block> blocks) {
}
