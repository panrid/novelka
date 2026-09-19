package panrid.space.novelka.core;

import java.util.*;

public final class Domain {
  public record Block(String id, String kind, String text) {}

  public record Chapter(int number, String url, String title, List<Block> blocks, String rawHtml) {}

  public record Novel(
      String id, String title, String author, String url, int chapterCount, boolean shortStory) {}

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
      boolean manual) {}

  public record Glossary(long revision, List<Entry> entries) {}

  public record Segment(
      List<Block> source, List<Block> draft, List<Block> revised, String context, String state) {}

  public record Work(
      String id,
      String novelId,
      int chapter,
      String sourceHash,
      int revision,
      List<Segment> segments,
      String state,
      String summary) {}

  public record Call(
      String id,
      String jobId,
      String stage,
      int segment,
      String model,
      String promptVersion,
      long glossaryRevision,
      String contextJson,
      double estimateUsd,
      String state) {}

  public static String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(Json.write(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
