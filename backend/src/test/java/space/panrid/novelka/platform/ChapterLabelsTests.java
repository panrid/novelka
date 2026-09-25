package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.platform.text.ChapterLabels;

class ChapterLabelsTests {

    @Test
    void numbersComeFromOriginalTitles() {
        assertThat(ChapterLabels.fromJapanese("第0話　プロローグ")).contains("0");
        assertThat(ChapterLabels.fromJapanese("第3話　灯り")).contains("3");
        assertThat(ChapterLabels.fromJapanese("３１．１話　続き")).contains("31.1");
        assertThat(ChapterLabels.fromJapanese("31-1 続き")).contains("31.1");
        assertThat(ChapterLabels.fromJapanese("031　灯り")).contains("31");
        assertThat(ChapterLabels.fromJapanese("0000 プロローグ")).contains("0");
        assertThat(ChapterLabels.fromJapanese("第十二話　夜")).contains("12");
        assertThat(ChapterLabels.fromJapanese("其の三")).contains("3");
        assertThat(ChapterLabels.fromJapanese("第百五話")).contains("105");
        assertThat(ChapterLabels.fromJapanese("3人の魔女")).as("a count, not a number").isEmpty();
        assertThat(ChapterLabels.fromJapanese("プロローグ")).isEmpty();
        assertThat(ChapterLabels.isSpecial("閑話　ある日の話")).isTrue();
    }

    @Test
    void translatedTitlesLoseTheirNumber() {
        assertThat(ChapterLabels.withoutNumber("Глава 3. Світло")).isEqualTo("Світло");
        assertThat(ChapterLabels.withoutNumber("Розділ 31.1: Продовження")).isEqualTo("Продовження");
        assertThat(ChapterLabels.withoutNumber("3 — Світло")).isEqualTo("Світло");
        assertThat(ChapterLabels.withoutNumber("Епізод 12")).isEmpty();
        assertThat(ChapterLabels.withoutNumber("Пролог")).isEqualTo("Пролог");
        assertThat(ChapterLabels.splitUkrainian("Глава 31.1. Ніч")).hasValueSatisfying(parts ->
                assertThat(parts).containsExactly("31.1", "Ніч"));
        assertThat(ChapterLabels.splitUkrainian("3 бажання")).isEmpty();
    }
}
