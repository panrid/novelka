package space.panrid.novelka.reading.internal;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The shapes of the public reading API, {@code /api/v1} (етап 22.2). Kept apart from the site's
 * own JSON ({@link Views}) on purpose: the site may change its pages freely, while these change
 * only by adding fields. No internal ids — a novel is its {@code slug}, a translation its team's
 * {@code team} handle — and never the original's text.
 */
final class ApiV1 {

    private ApiV1() {
    }

    /** A list read by key: pass {@code next} as {@code after} for the following page; null on the last. */
    record Page<T>(List<T> items, String next) {
    }

    /**
     * A translation (or an original work) of a novel.
     *
     * @param kind     human, machine, mixed or original
     * @param status   ongoing, completed, paused or abandoned
     * @param chapters published chapters
     * @param rating   readers' average (1–5), null before the first score
     */
    record Translation(String novel, String team, String teamName, String title, String author, String coverUrl, String kind,
            String status, boolean adult, int chapters, List<String> tags, OffsetDateTime lastPublishedAt, Double rating, int ratings) {
    }

    /**
     * @param origin   translation or original
     * @param language the original's language (ja, en…), null when not known
     */
    record Novel(String slug, String title, String author, String origin, String language, List<Block> description,
            List<String> tags, boolean adult, List<Translation> translations) {
    }

    /**
     * A chapter in the list.
     *
     * @param label  the number readers see when it is not the position («12.5», «Екстра»); null shows {@code number}
     * @param volume «Том 2. Назва», null before the first volume
     */
    record ChapterEntry(int number, String label, String title, OffsetDateTime publishedAt, String volume) {
    }

    /** A published chapter's text; {@code previous} and {@code next} are the neighbouring published numbers. */
    record Chapter(String novel, String team, int number, String label, String title, String volume, List<Block> blocks,
            Integer previous, Integer next) {
    }

    /**
     * A block of the text: heading, paragraph, preface, afterword, separator or image.
     *
     * @param imageUrl the picture of an image block
     */
    record Block(String id, String type, List<Span> content, String imageUrl) {
    }

    /** A run of text; marks are bold, italic, underline, strike. */
    record Span(String text, List<String> marks) {
    }

    /** @param lastNumber the last chapter number it may hold; null for the last volume */
    record Volume(int firstNumber, Integer lastNumber, String title, int chapters) {
    }

    record Tag(String name, String slug, int novels) {
    }

    /**
     * «Хто це»: a name or a term of the translation's glossary, with what the novel told by the chapter asked about.
     *
     * @param kind character, place, organization, term or other
     */
    record Character(String name, String kind, String gender, List<String> facts, String portraitUrl) {
    }
}
