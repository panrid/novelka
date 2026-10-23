package space.panrid.novelka.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;

class ChapterFilesTests {

    @Test
    void markdownHeadingsStartChaptersAndFormattingIsKept() {
        ChapterFiles.Parsed parsed = ChapterFiles.parse("mag.md", """
                # Глава 1. Спокійне життя

                «Це вперше, коли я бачу **стелю**».
                Перше слово в новому житті.

                ---

                ## Вночі
                ![Хатина](https://example.com/hut.png)

                # Глава 2
                Далі — [посилання](https://example.com) і `код`.
                """);

        assertThat(parsed.chapters()).extracting(ChapterFiles.ParsedChapter::title)
                .containsExactly("Глава 1. Спокійне життя", "Глава 2");
        List<Block> first = parsed.chapters().getFirst().blocks();
        assertThat(first).extracting(Block::type).containsExactly("paragraph", "paragraph", "separator", "paragraph", "image");
        assertThat(first.getFirst().content()).contains(new Span("стелю", List.of("bold")));
        assertThat(first.get(4).sourceUrl()).isEqualTo("https://example.com/hut.png");
        assertThat(parsed.chapters().get(1).blocks().getFirst().text()).isEqualTo("Далі — посилання і код.");
        assertThat(parsed.simplified()).contains("Підзаголовки (##) стали звичайним текстом.", "Посилання стали звичайним текстом.");
    }

    @Test
    void textFilesSplitOnChapterLinesAndKeepAsterisksLiteral() {
        ChapterFiles.Parsed parsed = ChapterFiles.parse("novel.txt", """
                Глава 1
                Рьо прокинувся.
                *зірочки* лишаються як є
                Розділ 2: Ринок
                Ліхтарі спалахнули.
                """);

        assertThat(parsed.chapters()).extracting(ChapterFiles.ParsedChapter::title).containsExactly("Глава 1", "Розділ 2: Ринок");
        assertThat(parsed.chapters().getFirst().blocks().get(1).content()).containsExactly(Span.plain("*зірочки* лишаються як є"));
    }

    @Test
    void aFileWithoutMarkersIsOneChapterTitledByItsFirstLine() {
        ChapterFiles.Parsed parsed = ChapterFiles.parse("12.txt", "Спокійне життя\n\nАбзац один.\nАбзац два.\n");

        assertThat(parsed.chapters()).singleElement().satisfies(chapter -> {
            assertThat(chapter.title()).isEqualTo("Спокійне життя");
            assertThat(chapter.blocks()).extracting(Block::id).containsExactly("b1", "b2");
        });
    }

    @Test
    void otherFormatsAndEmptyFilesAreRefused() {
        assertThatThrownBy(() -> ChapterFiles.parse("book.docx", "x")).isInstanceOf(UserFacingException.class)
                .hasMessage("Підходять лише файли .txt і .md.");
        assertThatThrownBy(() -> ChapterFiles.parse("empty.md", "\n\n# \n")).isInstanceOf(UserFacingException.class);
    }

    // ---- one chapter's text from the API (етап 22.11) ------------------------------------------

    @Test
    void aChapterInMarkdownGetsEveryMarkOfTheSite() {
        ChapterFiles.ParsedChapter chapter = ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, """
                **жирний** *курсив* _теж курсив_ ++підкреслений++ ~~закреслений~~ і \\*зірочка\\*

                ---
                * * *
                ◇◇◇
                ![Кузня](https://example.com/forge.png "Підпис")
                ## Підзаголовок
                Текст із [посиланням](https://example.com) і `кодом`.
                """);

        assertThat(chapter.title()).as("no heading, no guessing").isNull();
        assertThat(chapter.blocks()).extracting(Block::type)
                .containsExactly("paragraph", "separator", "separator", "separator", "image", "paragraph", "paragraph");
        assertThat(chapter.blocks()).extracting(Block::id).containsExactly("b1", "b2", "b3", "b4", "b5", "b6", "b7");
        assertThat(chapter.blocks().getFirst().content()).containsExactly(
                new Span("жирний", List.of("bold")), Span.plain(" "), new Span("курсив", List.of("italic")), Span.plain(" "),
                new Span("теж курсив", List.of("italic")), Span.plain(" "), new Span("підкреслений", List.of("underline")),
                Span.plain(" "), new Span("закреслений", List.of("strike")), Span.plain(" і *зірочка*"));
        assertThat(chapter.blocks().get(4).sourceUrl()).isEqualTo("https://example.com/forge.png");
        assertThat(chapter.blocks().get(5).text()).isEqualTo("Підзаголовок");
        assertThat(chapter.blocks().get(6).text()).isEqualTo("Текст із посиланням і кодом.");
    }

    @Test
    void plainTextKeepsMarkupAsWrittenButBreaksScenes() {
        ChapterFiles.ParsedChapter chapter = ChapterFiles.parseChapter(ChapterFiles.Format.PLAIN, """
                Глава 7. Ринок
                **не жирний** і ![не картинка](https://example.com/a.png)
                ***
                # не заголовок
                """);

        assertThat(chapter.title()).as("«Глава 7.» goes, as on import").isEqualTo("Ринок");
        assertThat(chapter.blocks()).extracting(Block::type).containsExactly("paragraph", "separator", "paragraph");
        assertThat(chapter.blocks().getFirst().content()).containsExactly(Span.plain("**не жирний** і ![не картинка](https://example.com/a.png)"));
        assertThat(chapter.blocks().get(2).content()).containsExactly(Span.plain("# не заголовок"));
    }

    @Test
    void aHeadingOpeningTheTextIsTheTitleAndNowhereElse() {
        assertThat(ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "# Глава 12. *Ніч* у кузні\nТекст.").title())
                .isEqualTo("Ніч у кузні");
        assertThat(ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "# Глава 12\nТекст.").title())
                .as("a heading that is only the number stays as it is").isEqualTo("Глава 12");
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "Текст.\n# Друга\nЩе текст."))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("починає нову");
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "# Перша\nТекст.\n# Друга\nЩе."))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("починає нову");
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.PLAIN, "Текст.\nРозділ 2\nЩе текст."))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("починає нову");
    }

    @Test
    void anEmptyOrTooLongTextOrAPictureNotByHttpsIsRefused() {
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, " \n\n\t\n"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("порожній");
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "# Лише назва\n"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("порожній");
        String tooLong = ("а".repeat(999) + "\n").repeat(ChapterFiles.MAX_CHAPTER_CHARS / 999 + 1);
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.PLAIN, tooLong))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("задовга");
        assertThatThrownBy(() -> ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, "Текст.\n![](http://example.com/a.png)"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("https://");
    }

    @Test
    void aChapterGivesTheBlocksTheImportOfTheSameFileGives() {
        String file = """
                # Глава 3. Перший день

                Він **знав**, що ++це++ ~~не~~ *сон*.
                ---
                ![Хатина](https://example.com/hut.png)
                ## Вночі
                Далі — [посилання](https://example.com).
                """;

        ChapterFiles.ParsedChapter imported = ChapterFiles.parse("3.md", file).chapters().getFirst();
        ChapterFiles.ParsedChapter sent = ChapterFiles.parseChapter(ChapterFiles.Format.MARKDOWN, file);
        assertThat(sent.blocks()).isEqualTo(imported.blocks());
        String text = "Глава 3\nРьо *прокинувся*.\n◇◇◇\nРанок.";
        assertThat(ChapterFiles.parseChapter(ChapterFiles.Format.PLAIN, text).blocks())
                .isEqualTo(ChapterFiles.parse("3.txt", text).chapters().getFirst().blocks());
    }
}
