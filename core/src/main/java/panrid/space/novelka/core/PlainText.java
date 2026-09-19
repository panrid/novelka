package panrid.space.novelka.core;

import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;

import java.util.*;

/** Import user-provided UTF-8 text: first nonblank line is the heading. */
public final class PlainText {
  public static Chapter parse(int number, String source, String text) {
    if (number < 1) throw new IllegalArgumentException("Chapter number must be positive");
    var lines =
        text.replace("\uFEFF", "")
            .lines()
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .toList();
    if (lines.size() < 2)
      throw new IllegalArgumentException("Text must include a title and chapter body");
    var blocks = new ArrayList<Block>();
    blocks.add(new Block("title", "heading", lines.getFirst()));
    for (int i = 1; i < lines.size(); i++)
      blocks.add(
          new Block(
              "paragraph-" + i,
              lines.get(i).matches("[＊*◇◆○●ー—\\s]{3,}") ? "separator" : "paragraph",
              lines.get(i)));
    return new Chapter(number, source, lines.getFirst(), List.copyOf(blocks), "");
  }
}
