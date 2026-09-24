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
}
