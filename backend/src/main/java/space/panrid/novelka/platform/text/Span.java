package space.panrid.novelka.platform.text;

import java.util.List;

/** A run of text with the same marks: bold, italic, underline, strike. */
public record Span(String text, List<String> marks) {

    public static final List<String> MARKS = List.of("bold", "italic", "underline", "strike");

    public Span {
        marks = List.copyOf(marks);
    }

    public static Span plain(String text) {
        return new Span(text, List.of());
    }
}
