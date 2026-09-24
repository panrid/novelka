package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.Markup;
import space.panrid.novelka.platform.text.Span;

class MarkupTests {

    @Test
    void allFourMarksAndBothBoldAndItalicSpellings() {
        assertThat(Markup.parse("**ж** __ж__ *к* _к_ ++п++ ~~з~~")).containsExactly(
                new Span("ж", List.of("bold")), Span.plain(" "), new Span("ж", List.of("bold")), Span.plain(" "),
                new Span("к", List.of("italic")), Span.plain(" "), new Span("к", List.of("italic")), Span.plain(" "),
                new Span("п", List.of("underline")), Span.plain(" "), new Span("з", List.of("strike")));
    }

    @Test
    void marksNest() {
        assertThat(Markup.parse("**дуже *важливо***")).containsExactly(
                new Span("дуже ", List.of("bold")), new Span("важливо", List.of("bold", "italic")));
    }

    @Test
    void unmatchedOrIntrawordDelimitersStayAsTyped() {
        assertThat(Markup.parse("2 * 3 = 6, snake_case_name, **не закрито")).containsExactly(
                Span.plain("2 * 3 = 6, snake_case_name, **не закрито"));
    }

    @Test
    void backslashMakesADelimiterLiteral() {
        assertThat(Markup.parse("\\*зірочка\\*")).containsExactly(Span.plain("*зірочка*"));
    }
}
