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
        // DeepSeek V4 Flash, «Меджик Мейкер» chapter 52: refused nine times as a slip, split down to ten lines.
        assertThat(Pipeline.sameStart("「……ブリジット・ギーテ」", "「……")).as("a quote of punctuation only").isTrue();
        assertThat(Pipeline.sameStart("「……ブリジット・ギーテ」", "「...")).isTrue();
        assertThat(Pipeline.sameStart("「……ブリジット・ギーテ」", "「……お")).as("a letter that is not its own").isFalse();
        assertThat(Pipeline.sameStart("何か", null)).as("an answer without «start»").isTrue();
    }

    private static tools.jackson.databind.JsonNode answer(List<Block> blocks, String... texts) {
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        var lines = json.createArrayNode();
        for (int i = 0; i < texts.length; i++) {
            lines.addObject().put("id", blocks.get(i).id()).put("start", blocks.get(i).text().substring(0, 2)).put("text", texts[i]);
        }
        var out = json.createObjectNode();
        out.set("blocks", lines);
        return out;
    }

    @Test
    void aTranslationSlippedByALineIsCaughtByWhereTheSpeechIs() {
        // «Меджик Мейкер», chapter 21: the narrator's thoughts came back as dialogue a line late.
        List<Block> blocks = List.of(
                line(1, "「よくわからんがわかった。しかし電気か……。"),
                line(2, "雷ほどではないが、似たような現象を見たことがある、と聞いたような」"),
                line(3, "静電気の類だろうか？"),
                line(4, "視認できるのならば、それなりの電力が発生しているということ。"),
                line(5, "「グラストがそんなようなことを言っていた気がする。"));
        assertThat(Pipeline.usable(answer(blocks, "— Не зовсім розумію, але гаразд. Електрика, кажеш…",
                "— Здається, я чув, що хтось бачив подібне явище.", "Може, це статична електрика?",
                "Якщо її видно, то й потужність там чимала.", "— Здається, Ґраст казав щось подібне."), blocks))
                .as("in step; the speech's second line may have a dash or not").isNull();
        assertThat(Pipeline.usable(answer(blocks, "— Не зовсім розумію, але гаразд.", "Здається, я чув про подібне.",
                "— Можливо, це статична електрика?", "— Якщо її видно, то й потужність чимала.",
                "— Здається, Ґраст казав щось подібне."), blocks))
                .as("narration came back as speech twice in a row").contains("зсунулися");
        assertThat(Pipeline.usable(answer(blocks, "— Не зовсім розумію, але гаразд.", "— Чув про подібне.",
                "— Можливо, це статична електрика?", "Якщо її видно, то й потужність чимала.",
                "— Здається, Ґраст казав щось подібне."), blocks))
                .as("one odd line is not a slip").isNull();
        assertThat(Pipeline.speech(List.of(line(1, "それは悲しみから生まれたものではない。"), line(2, "『ファイ』に来た。"))))
                .as("a part may begin inside a speech; 『』 is a quote, not dialogue").containsExactly(null, null);
    }

    @Test
    void narrationKeepsNoDashWhateverTheModelMadeOfIt() {
        // «Меджик Мейкер», chapter 95: the narrator answers a question in thought, and every model put a dash.
        List<Block> blocks = List.of(
                line(1, "「ま、ま、まさか、ま、まだお済みでないとか！？」"),
                line(2, "お済みです。"),
                line(3, "もうすでに十三歳なので、完全にお済みでございます。"),
                line(4, "「いやそういう意味じゃないんだけど」"));
        assertThat(Pipeline.narration(blocks)).containsExactlyInAnyOrder("s2", "s3");
        assertThat(Pipeline.withoutDash("— Уже.")).isEqualTo("Уже.");
        assertThat(Pipeline.withoutDash("Уже.")).isEqualTo("Уже.");
        assertThat(Pipeline.usable(answer(blocks, "— Невже ви ще не?..", "— Уже.", "— Мені тринадцять, тож цілком уже.",
                "— Я не про те."), blocks)).startsWith(Pipeline.MISPLACED);
    }

    private static tools.jackson.databind.JsonNode changes(List<Block> blocks, int[] at, String... texts) {
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        var lines = json.createArrayNode();
        for (int i = 0; i < texts.length; i++) {
            Block block = blocks.get(at[i]);
            lines.addObject().put("id", block.id()).put("start", block.text().substring(0, 2)).put("text", texts[i]);
        }
        var out = json.createObjectNode();
        out.set("blocks", lines);
        return out;
    }

    @Test
    void theEditorReturnsOnlyTheLinesItChanges() {
        List<Block> blocks = List.of(
                line(1, "「よくわからんがわかった。しかし電気か……。"),
                line(2, "雷ほどではないが、似たような現象を見たことがある、と聞いたような」"),
                line(3, "静電気の類だろうか？"),
                line(4, "視認できるのならば、それなりの電力が発生しているということ。"),
                line(5, "「グラストがそんなようなことを言っていた気がする。"));
        assertThat(Pipeline.edits(changes(blocks, new int[0]), blocks)).as("a draft with nothing to fix").isNull();
        assertThat(Pipeline.edits(changes(blocks, new int[] {2}, "Може, це статична електрика?"), blocks)).isNull();
        assertThat(Pipeline.edits(changes(blocks, new int[] {2, 0}, "Може, це статична електрика?", "— Гаразд."), blocks))
                .as("out of order").contains("переставлений");
        assertThat(Pipeline.edits(changes(blocks, new int[] {2, 3}, "— Може, це статична електрика?", "— Якщо її видно…"), blocks))
                .as("neighbouring narration turned into speech").contains("зсунулися");
        assertThat(Pipeline.edits(changes(blocks, new int[] {0, 2}, "Не зовсім розумію.", "— Може, це статична електрика?"), blocks))
                .as("two odd lines apart are not a slip").isNull();
        assertThat(Pipeline.edits(changes(blocks, new int[] {4}, "— Ґраст казав щось подібне [term]."), blocks)).isNotNull();
    }

    @Test
    void aChapterShowsItsProgressPartByPart() {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.parts = 7;
        checkpoint.draft.put("0", List.of());
        checkpoint.draft.put("1", List.of());
        Jobs.StepView translating = Jobs.step(51, "translate", "running", null, checkpoint, false, true);
        assertThat(translating.part()).isEqualTo(2);
        assertThat(translating.parts()).isEqualTo(7);
        assertThat(translating.progress()).isBetween(0.27, 0.28);
        assertThat(Jobs.step(51, "translate", "running", null, checkpoint, false, false).progress())
                .as("without proofreading translation is nearly all of it").isGreaterThan(translating.progress());
        assertThat(Jobs.step(51, "analyze", "running", null, new Checkpoint(), false, true).progress()).isPositive();
        assertThat(Jobs.step(51, "translate", "running", null, new Checkpoint(), false, true).parts())
                .as("parts not cut yet").isZero();
    }

    @Test
    void everyLineOfASpeechGetsItsDash() {
        // «Меджик Мейкер», chapter 51: the duke's speech over four lines, the dash only on the first.
        List<Block> blocks = List.of(
                line(1, "「もうやだああっ！　なんなの！？"),
                line(2, "なんで儂がイストリア領を統治することになってからこんなことになっとるの！？"),
                line(3, "儂はもうイストリア領主やめる！」"),
                line(4, "バルフ公爵は駄々っ子のようにばたばたと手足を動かした。"),
                line(5, "彼は「はい」と言った。"));
        assertThat(Pipeline.spoken(blocks)).containsExactlyInAnyOrder("s1", "s2", "s3");
        assertThat(Pipeline.withDash("Чому відтоді, як я став правити?")).isEqualTo("— Чому відтоді, як я став правити?");
        assertThat(Pipeline.withDash("— Усе, не можу більше!")).isEqualTo("— Усе, не можу більше!");
        assertThat(Pipeline.withDash("– Годі!")).isEqualTo("— Годі!");
        assertThat(Pipeline.withDash("«Годі!»")).as("a speech set in quotes stays so").isEqualTo("«Годі!»");
    }

    @Test
    void japaneseOrAMarkerLeftInTheTranslationIsNotAccepted() {
        assertThat(Pipeline.leftover("s1", "Повернемося до перевірки водяної магії [term].")).isNotNull();
        assertThat(Pipeline.leftover("s1", "Він сказав: まあいいか.")).isNotNull();
        assertThat(Pipeline.leftover("s1", "Дякую! (^^)ノ")).as("a kana in a smiley is not untranslated text").isNull();
        assertThat(Pipeline.leftover("s1", "— Флер!")).isNull();
    }

    @Test
    void theNarratorIsToldWithTheGrammarTheirGenderNeeds() {
        assertThat(Prompts.narrator("Сіон", "male")).contains("Сіон").contains("я думав");
        assertThat(Prompts.narrator("Каталіна", "female")).contains("я думала");
        assertThat(Prompts.narrator("Рьо", "unknown")).isEqualTo("The text is told in the first person by Рьо.");
        assertThat(Prompts.narrator("", "male")).as("third person: nothing to say").isEmpty();
        assertThat(Prompts.narrator(null, null)).isEmpty();
    }

    @Test
    void anAnswerThatIgnoredTheSchemaIsReadIfItsContentIsThere() {
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        // What DeepSeek V3.2 via Venice returned on 2026-10-06 instead of {"blocks": […], "summary": "…"}.
        String answer = """
                ```json
                [
                  {"id": "s262", "start": "僕が何", "translation": "Ще я не встиг нічого сказати."},
                  {"id": "s263", "start": "「交易", "translation": "— На біржі."}
                ]
                ```

                **Короткий зміст:** Ґраст каже, що слюда дорога.""";
        tools.jackson.databind.JsonNode read = Pipeline.lenient(json, answer);
        org.assertj.core.api.Assertions.assertThat(read.path("blocks").path(1).path("text").asString()).isEqualTo("— На біржі.");
        org.assertj.core.api.Assertions.assertThat(read.path("blocks").path(0).path("start").asString()).isEqualTo("僕が何");
        org.assertj.core.api.Assertions.assertThat(read.path("summary").asString()).isEqualTo("Ґраст каже, що слюда дорога.");
        org.assertj.core.api.Assertions.assertThat(Pipeline.lenient(json, "{\"blocks\":[],\"summary\":\"x\"}").path("summary").asString())
                .as("a proper answer is read as it is").isEqualTo("x");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Pipeline.lenient(json, "Вибачте, не можу.")).isInstanceOf(RuntimeException.class);
    }
}
