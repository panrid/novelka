package space.panrid.novelka.platform.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one light markup of the site (рішення 24), for chapters imported from .md, and later
 * for comments and messages: {@code **жирний**} or {@code __жирний__}, {@code *курсив*} or
 * {@code _курсив_}, {@code ++підкреслений++}, {@code ~~закреслений~~}. A backslash makes the
 * next character literal. Anything unmatched stays as typed.
 */
public final class Markup {

    private record Delimiter(String token, String mark) {
    }

    // Longest first, so "**" is not read as two "*".
    private static final List<Delimiter> DELIMITERS = List.of(
            new Delimiter("**", "bold"), new Delimiter("__", "bold"), new Delimiter("++", "underline"),
            new Delimiter("~~", "strike"), new Delimiter("*", "italic"), new Delimiter("_", "italic"));

    private Markup() {
    }

    public static List<Span> parse(String text) {
        List<Span> spans = new ArrayList<>();
        parse(text, new LinkedHashSet<>(), spans);
        return merge(spans);
    }

    private static void parse(String text, Set<String> marks, List<Span> out) {
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && isPunctuation(text.charAt(i + 1))) {
                plain.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            int[] match = match(text, i);
            if (match != null) {
                Delimiter delimiter = DELIMITERS.get(match[0]);
                int close = match[1];
                flush(plain, marks, out);
                Set<String> inner = new LinkedHashSet<>(marks);
                inner.add(delimiter.mark());
                parse(text.substring(i + delimiter.token().length(), close), inner, out);
                i = close + delimiter.token().length();
                continue;
            }
            plain.append(c);
            i++;
        }
        flush(plain, marks, out);
    }

    /** {delimiter index, closing position} for an opener at {@code at}, or null. */
    private static int[] match(String text, int at) {
        for (int d = 0; d < DELIMITERS.size(); d++) {
            String token = DELIMITERS.get(d).token();
            if (!text.startsWith(token, at)) {
                continue;
            }
            int start = at + token.length();
            if (start >= text.length() || Character.isWhitespace(text.charAt(start))) {
                continue;
            }
            boolean underscore = token.charAt(0) == '_';
            // snake_case_words and 2*3*4 are not formatting.
            if (underscore && at > 0 && Character.isLetterOrDigit(text.charAt(at - 1))) {
                continue;
            }
            int close = findClose(text, token, start, underscore);
            if (close > start) {
                return new int[]{d, close};
            }
        }
        return null;
    }

    private static int findClose(String text, String token, int from, boolean underscore) {
        int i = from;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (text.startsWith(token, i) && !Character.isWhitespace(text.charAt(i - 1))) {
                // In a run like "***" the outer "**" closes at the end: "**а *б***" → bold(а, italic(б)).
                int run = 0;
                while (i + run < text.length() && text.charAt(i + run) == token.charAt(0)) {
                    run++;
                }
                if (token.length() > 1 && run > token.length()) {
                    i += run - token.length();
                }
                int after = i + token.length();
                boolean single = token.length() == 1;
                // A single "*" right before another "*" belongs to a "**" closer.
                boolean partOfDouble = single && after < text.length() && text.charAt(after) == token.charAt(0);
                boolean wordAfter = underscore && after < text.length() && Character.isLetterOrDigit(text.charAt(after));
                if (!partOfDouble && !wordAfter) {
                    return i;
                }
                if (partOfDouble) {
                    i = after + 1;
                    continue;
                }
            }
            i++;
        }
        return -1;
    }

    private static void flush(StringBuilder plain, Set<String> marks, List<Span> out) {
        if (!plain.isEmpty()) {
            out.add(new Span(plain.toString(), ordered(marks)));
            plain.setLength(0);
        }
    }

    private static List<String> ordered(Set<String> marks) {
        return Span.MARKS.stream().filter(marks::contains).toList();
    }

    private static List<Span> merge(List<Span> spans) {
        List<Span> merged = new ArrayList<>();
        for (Span span : spans) {
            if (!merged.isEmpty() && merged.getLast().marks().equals(span.marks())) {
                Span last = merged.removeLast();
                merged.add(new Span(last.text() + span.text(), last.marks()));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    private static boolean isPunctuation(char c) {
        return "\\*_~+`#[]()!-".indexOf(c) >= 0;
    }
}
