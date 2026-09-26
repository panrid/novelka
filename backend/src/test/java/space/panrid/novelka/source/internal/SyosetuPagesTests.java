package space.panrid.novelka.source.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.support.FakeSyosetu;

class SyosetuPagesTests {

    @Test
    void aChapterPageBecomesBlocksWithReadingsKeptAndEmptyLinesDropped() {
        SourceProvider.Page page = SyosetuPages.chapter(FakeSyosetu.chapterPage("n0001aa", 3));

        assertThat(page.title()).isEqualTo("第3話　灯り");
        List<Block> blocks = page.blocks();
        assertThat(blocks).extracting(Block::type)
                .containsExactly("preface", "paragraph", "paragraph", "separator", "paragraph", "afterword");
        assertThat(blocks).extracting(Block::id).containsExactly("s1", "s2", "s3", "s4", "s5", "s6");
        assertThat(blocks.get(1).text()).as("ruby kept, indent dropped").isEqualTo("雪《ユキ》は灯台を見た。");
        assertThat(blocks.get(2).text()).isEqualTo("「ユキ、行こう」と彼は言った。第3話。");
        assertThat(SourceService.chars(blocks)).isEqualTo(
                blocks.stream().mapToInt(block -> block.text().replaceAll("\\s", "").length()).sum());
    }

    @Test
    void picturesBecomeLinksToCopy() {
        SourceProvider.Page page = SyosetuPages.chapter("""
                <div class="js-novel-text p-novel__text">
                <p id="L1">本文。</p>
                <p id="L2"><a href="//12345.mitemin.net/i1/"><img src="//12345.mitemin.net/userpageimage/viewimagebig/icode/i1/" alt=""></a></p>
                </div>""");
        assertThat(page.title()).as("no heading: the service takes the table of contents' or the novel's").isEmpty();
        assertThat(page.blocks().get(1).type()).isEqualTo("image");
        assertThat(page.blocks().get(1).sourceUrl()).isEqualTo("https://12345.mitemin.net/userpageimage/viewimagebig/icode/i1/");
    }

    @Test
    void aPageWithoutTextIsAnError() {
        assertThatThrownBy(() -> SyosetuPages.chapter("<html><body>немає</body></html>"))
                .isInstanceOf(UserFacingException.class);
    }

    @Test
    void sitesArePausedTogetherAcrossSubdomains() {
        assertThat(JdkSourceHttp.site("api.syosetu.com")).isEqualTo("syosetu.com");
        assertThat(JdkSourceHttp.site("NCODE.syosetu.com")).isEqualTo("syosetu.com");
        assertThat(JdkSourceHttp.site("fenrirealm.com")).isEqualTo("fenrirealm.com");
    }

    @Test
    void linksAndCodesAreRecognised() {
        assertThat(SyosetuLink.parse("https://ncode.syosetu.com/n0022gd/12/")).contains(new SyosetuLink("n0022gd", false));
        assertThat(SyosetuLink.parse("  N0022GD ")).contains(new SyosetuLink("n0022gd", false));
        assertThat(SyosetuLink.parse("novel18.syosetu.com/n1234ab").orElseThrow().adult()).isTrue();
        assertThat(SyosetuLink.fromKey(new SyosetuLink("n1234ab", true).key())).isEqualTo(new SyosetuLink("n1234ab", true));
        assertThat(SyosetuLink.parse("https://example.com/n0022gd/")).isEmpty();
        assertThat(new SyosetuLink("n1234ab", true).link())
                .isEqualTo(new SourceLink("syosetu", "novel18:n1234ab", "https://novel18.syosetu.com/n1234ab/", true));
    }
}
