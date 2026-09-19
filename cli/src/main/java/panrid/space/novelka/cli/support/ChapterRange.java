package panrid.space.novelka.cli.support;

public record ChapterRange(int first, int last) {
  public static ChapterRange parse(Integer chapter, String chapters, int maximum) {
    if ((chapter == null) == (chapters == null)) {
      throw new IllegalArgumentException("Specify exactly one of --chapter or --chapters");
    }
    int first;
    int last;
    if (chapter != null) {
      first = chapter;
      last = chapter;
    } else {
      if (!chapters.matches("[0-9]+-[0-9]+")) {
        throw new IllegalArgumentException("Use --chapters 1-10");
      }
      var parts = chapters.split("-");
      first = Integer.parseInt(parts[0]);
      last = Integer.parseInt(parts[1]);
    }
    if (first < 1 || last < first || last > maximum) {
      throw new IllegalArgumentException("Invalid chapter range (1-" + maximum + ")");
    }
    return new ChapterRange(first, last);
  }
}
