package panrid.space.novelka.core.model;

import java.util.List;

public record Chapter(int number, String url, String title, List<Block> blocks, String rawHtml) {
}
