package space.panrid.novelka.reading.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchWordsTests {

    @Test
    void wordsAreLettersAndDigitsWithoutApostrophes() {
        assertThat(Search.words("  Пам’ять — «Маг води»: том 2!! маг ")).containsExactly("память", "маг", "води", "том", "2");
        assertThat(Search.words("水属性の魔法使い Mizu-Zokusei")).containsExactly("水属性の魔法使い", "mizu", "zokusei");
        assertThat(Search.words("!!! ' —")).isEmpty();
        assertThat(Search.words(null)).isEmpty();
        assertThat(Search.words("a b c d e f g h")).hasSize(Search.MAX_WORDS);
    }

    @Test
    void ukrainianEndingsAreDroppedButAStemKeepsThreeLetters() {
        assertThat(Search.stem("магії")).isEqualTo("маг");
        assertThat(Search.stem("магія")).isEqualTo("маг");
        assertThat(Search.stem("водяний")).isEqualTo("водян");
        assertThat(Search.stem("води")).isEqualTo("вод");
        assertThat(Search.stem("маяк")).isEqualTo("маяк");
        assertThat(Search.stem("моя")).isEqualTo("моя");
        assertThat(Search.stem("magician")).isEqualTo("magician");
    }
}
