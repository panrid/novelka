package space.panrid.novelka.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.text.Markup;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Turns an uploaded .txt or .md file into chapters (рішення 23).
 *
 * <ul>
 *   <li>Every non-empty line is a paragraph; blank lines between paragraphs are fine too.</li>
 *   <li>.md: {@code # Назва} starts a chapter; {@code ---} is a scene break; a line with only
 *       {@code ![опис](https://…)} is a picture by link; inline marks as in {@link Markup}.
 *       Other Markdown (##, lists, links, code) becomes plain text.</li>
 *   <li>.txt: a line starting with «Глава N» or «Розділ N» starts a chapter; no formatting.</li>
 *   <li>Without chapter markers the whole file is one chapter; its first short line is the title.</li>
 * </ul>
 */
public final class ChapterFiles {

    public record ParsedChapter(String title, List<Block> blocks) {
    }

    public record Parsed(List<ParsedChapter> chapters, List<String> simplified) {
    }

    public static final int MAX_CHAPTERS = 500;
    public static final int MAX_CHAPTER_CHARS = 200_000;
    private static final int TITLE_MAX = 200;

    private static final Pattern TXT_CHAPTER = Pattern.compile("^(глава|розділ|частина|chapter)\\s+\\d+.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MD_CHAPTER = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern MD_SUBHEADING = Pattern.compile("^#{2,6}\\s+(.+)$");
    private static final Pattern SEPARATOR = Pattern.compile("^(\\*\\s*){3,}$|^(-\\s*){3,}$|^(_\\s*){3,}$|^◇+$|^◆+$");
    private static final Pattern IMAGE_LINE = Pattern.compile("^!\\[[^\\]]*]\\((\\S+?)(?:\\s+\"[^\"]*\")?\\)$");
    private static final Pattern LINK = Pattern.compile("(?<!!)\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private ChapterFiles() {
    }

    public static Parsed parse(String fileName, String content) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean markdown = lower.endsWith(".md") || lower.endsWith(".markdown");
        if (!markdown && !lower.endsWith(".txt")) {
            throw UserFacingException.badRequest("Підходять лише файли .txt і .md.");
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n').replace("﻿", "");
        List<String> simplified = new ArrayList<>();
        List<Draft> drafts = new ArrayList<>();
        Draft current = null;

        for (String raw : text.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String chapterTitle = markdown ? group(MD_CHAPTER, line) : (TXT_CHAPTER.matcher(line).matches() ? line : null);
            if (chapterTitle != null) {
                current = new Draft(plainTitle(chapterTitle, markdown));
                drafts.add(current);
                continue;
            }
            if (current == null) {
                current = new Draft(null);
                drafts.add(current);
            }
            current.add(line, markdown, simplified);
        }

        List<ParsedChapter> chapters = new ArrayList<>();
        for (Draft draft : drafts) {
            ParsedChapter chapter = draft.finish(baseName(fileName));
            if (!chapter.blocks().isEmpty() || drafts.size() == 1) {
                chapters.add(chapter);
            }
        }
        if (chapters.isEmpty() || chapters.stream().allMatch(chapter -> chapter.blocks().isEmpty())) {
            throw UserFacingException.badRequest("У файлі немає тексту.");
        }
        if (chapters.size() > MAX_CHAPTERS) {
            throw UserFacingException.badRequest("Забагато глав в одному файлі: до %d.".formatted(MAX_CHAPTERS));
        }
        for (ParsedChapter chapter : chapters) {
            int chars = chapter.blocks().stream().mapToInt(block -> block.text().length()).sum();
            if (chars > MAX_CHAPTER_CHARS) {
                throw UserFacingException.badRequest("Глава «%s» задовга: до %d знаків.".formatted(chapter.title(), MAX_CHAPTER_CHARS));
            }
        }
        return new Parsed(chapters, simplified.stream().distinct().toList());
    }

    /** Lines of one chapter before they become blocks. */
    private static final class Draft {
        private final String title;
        private final List<Block> blocks = new ArrayList<>();
        private String firstLine;

        Draft(String title) {
            this.title = title;
        }

        void add(String line, boolean markdown, List<String> simplified) {
            String id = "b" + (blocks.size() + 1);
            if (SEPARATOR.matcher(line).matches()) {
                blocks.add(Block.separator(id));
                return;
            }
            if (markdown) {
                String url = group(IMAGE_LINE, line);
                if (url != null) {
                    if (url.startsWith("https://")) {
                        blocks.add(Block.imageLink(id, url));
                    } else {
                        simplified.add("Картинки підтримуються лише за посиланнями https://.");
                    }
                    return;
                }
                String sub = group(MD_SUBHEADING, line);
                if (sub != null) {
                    simplified.add("Підзаголовки (##) стали звичайним текстом.");
                    line = sub;
                }
                if (LINK.matcher(line).find()) {
                    simplified.add("Посилання стали звичайним текстом.");
                    line = LINK.matcher(line).replaceAll("$1");
                }
                if (CODE.matcher(line).find()) {
                    line = CODE.matcher(line).replaceAll("$1");
                }
            }
            if (firstLine == null) {
                firstLine = line;
            }
            blocks.add(Block.paragraph(id, markdown ? Markup.parse(line) : List.of(Span.plain(line))));
        }

        ParsedChapter finish(String fallbackTitle) {
            if (title != null) {
                return new ParsedChapter(title, blocks);
            }
            // No explicit title: a short first line is the title, as in most pasted chapters.
            if (!blocks.isEmpty() && blocks.getFirst().type().equals("paragraph") && firstLine.length() <= 120) {
                return new ParsedChapter(blocks.getFirst().text(), renumber(blocks.subList(1, blocks.size())));
            }
            return new ParsedChapter(fallbackTitle, blocks);
        }
    }

    private static List<Block> renumber(List<Block> blocks) {
        List<Block> out = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            out.add(new Block("b" + (i + 1), block.type(), block.content(), block.imageId(), block.sourceUrl()));
        }
        return out;
    }

    private static String plainTitle(String title, boolean markdown) {
        String plain = markdown ? Markup.parse(title).stream().map(Span::text).reduce("", String::concat) : title;
        plain = plain.strip();
        return plain.length() > TITLE_MAX ? plain.substring(0, TITLE_MAX) : plain;
    }

    private static String group(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String baseName(String fileName) {
        String name = fileName == null ? "Глава" : fileName.replaceAll(".*[/\\\\]", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
