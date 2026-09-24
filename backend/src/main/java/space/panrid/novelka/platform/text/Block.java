package space.panrid.novelka.platform.text;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One piece of a chapter. {@code id} stays the same across revisions for unchanged
 * paragraphs, so suggestions and diffs can follow them.
 *
 * @param type      heading, paragraph, preface, afterword, separator or image
 * @param content   text runs (empty for separator and image)
 * @param imageId   picture for type image, once stored
 * @param sourceUrl picture link from an import, before the server copies it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Block(String id, String type, List<Span> content, Long imageId, String sourceUrl) {

    public static final List<String> TYPES = List.of("heading", "paragraph", "preface", "afterword", "separator", "image");

    public Block {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static Block paragraph(String id, List<Span> content) {
        return new Block(id, "paragraph", content, null, null);
    }

    public static Block separator(String id) {
        return new Block(id, "separator", List.of(), null, null);
    }

    public static Block imageLink(String id, String url) {
        return new Block(id, "image", List.of(), null, url);
    }

    /** Plain text of the block, marks dropped. */
    public String text() {
        StringBuilder out = new StringBuilder();
        content.forEach(span -> out.append(span.text()));
        return out.toString();
    }
}
