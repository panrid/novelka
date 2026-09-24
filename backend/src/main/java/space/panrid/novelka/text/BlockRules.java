package space.panrid.novelka.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * What the server accepts as chapter text, whatever the editor sent: known block types and
 * marks only, sane sizes, unique ids. Missing or repeated ids get fresh ones, so a
 * paragraph split in two keeps its id in the first half.
 */
public final class BlockRules {

    public static final int MAX_BLOCKS = 5_000;
    public static final int MAX_BLOCK_CHARS = 20_000;
    public static final int MAX_CHAPTER_CHARS = 200_000;
    public static final int TITLE_MAX = 200;

    private BlockRules() {
    }

    public static String title(String raw) {
        String title = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            throw UserFacingException.badRequest("Дайте главі назву.");
        }
        if (title.length() > TITLE_MAX) {
            throw UserFacingException.badRequest("Назва глави — до %d символів.".formatted(TITLE_MAX));
        }
        return title;
    }

    public static List<Block> normalize(List<Block> input) {
        if (input == null || input.isEmpty()) {
            throw UserFacingException.badRequest("Глава порожня.");
        }
        if (input.size() > MAX_BLOCKS) {
            throw UserFacingException.badRequest("Забагато абзаців в одній главі.");
        }
        Set<String> used = new HashSet<>();
        int next = 1;
        for (Block block : input) {
            if (block.id() != null) {
                used.add(block.id());
            }
        }
        Set<String> taken = new HashSet<>();
        List<Block> out = new ArrayList<>(input.size());
        int total = 0;
        for (Block block : input) {
            if (block == null || !Block.TYPES.contains(block.type())) {
                throw UserFacingException.badRequest("У тексті є невідомий елемент.");
            }
            String id = block.id();
            if (id == null || id.isBlank() || id.length() > 24 || !taken.add(id)) {
                do {
                    id = "n" + next++;
                } while (used.contains(id) || !taken.add(id));
            }
            List<Span> content = spans(block);
            int chars = content.stream().mapToInt(span -> span.text().length()).sum();
            if (chars > MAX_BLOCK_CHARS) {
                throw UserFacingException.badRequest("Один з абзаців задовгий: до %d знаків.".formatted(MAX_BLOCK_CHARS));
            }
            total += chars;
            switch (block.type()) {
                case "separator" -> out.add(Block.separator(id));
                case "image" -> {
                    if (block.imageId() == null) {
                        throw UserFacingException.badRequest("Картинку ще не завантажено.");
                    }
                    out.add(new Block(id, "image", List.of(), block.imageId(), null));
                }
                default -> {
                    if (!content.isEmpty()) {
                        out.add(new Block(id, block.type(), content, null, null));
                    }
                }
            }
        }
        if (total > MAX_CHAPTER_CHARS) {
            throw UserFacingException.badRequest("Глава задовга: до %d знаків.".formatted(MAX_CHAPTER_CHARS));
        }
        if (out.stream().noneMatch(block -> !block.text().isBlank() || block.type().equals("image"))) {
            throw UserFacingException.badRequest("Глава порожня.");
        }
        return out;
    }

    /** Known marks only, empty runs dropped, neighbours with the same marks merged. */
    private static List<Span> spans(Block block) {
        List<Span> out = new ArrayList<>();
        for (Span span : block.content()) {
            if (span == null || span.text() == null || span.text().isEmpty()) {
                continue;
            }
            List<String> marks = Span.MARKS.stream().filter(span.marks()::contains).toList();
            String text = span.text().replace("\r", "").replace('\n', ' ');
            if (!out.isEmpty() && out.getLast().marks().equals(marks)) {
                Span last = out.removeLast();
                out.add(new Span(last.text() + text, marks));
            } else {
                out.add(new Span(text, marks));
            }
        }
        return out;
    }

    /** Ids of pictures in the text. */
    public static Set<Long> images(List<Block> blocks) {
        Set<Long> ids = new HashSet<>();
        for (Block block : blocks) {
            if (block.imageId() != null) {
                ids.add(block.imageId());
            }
        }
        return ids;
    }
}
