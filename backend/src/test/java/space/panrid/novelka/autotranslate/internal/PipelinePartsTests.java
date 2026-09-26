package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Span;

class PipelinePartsTests {

    private static Block line(int number, String text) {
        return new Block("s" + number, "paragraph", List.of(new Span(text, List.of())), null, null);
    }

    @Test
    void manyShortLinesGoInSeveralRequests() {
        List<Block> blocks = IntStream.rangeClosed(1, 150).mapToObj(n -> line(n, "短い行。")).toList();
        List<List<Block>> parts = Pipeline.parts(blocks, 4_000, Pipeline.LINES_PER_PART);
        assertThat(parts).hasSize(4).allSatisfy(part -> assertThat(part).hasSizeLessThanOrEqualTo(40));
        assertThat(Pipeline.parts(blocks, 4_000)).as("parts saved before the limit keep their cuts").hasSize(1);
    }

    @Test
    void aQuotedStartShowsWhetherTheLineIsItsOwn() {
        assertThat(Pipeline.sameStart("「槍の長さは、兵に安心感を与える」", "「槍の長")).isTrue();
        assertThat(Pipeline.sameStart("『ファイ』に来て十二日目。", "ファイ")).as("brackets do not count").isTrue();
        assertThat(Pipeline.sameStart("『ファイ』に来て十二日目。", "ついに、")).as("another line's start").isFalse();
        assertThat(Pipeline.sameStart("The spear gives soldiers courage.", "the s")).as("any language of the original").isTrue();
        assertThat(Pipeline.sameStart("……", "")).as("a line of dots has nothing to compare").isTrue();
        assertThat(Pipeline.sameStart("何か", null)).as("an answer without «start»").isTrue();
    }
}
