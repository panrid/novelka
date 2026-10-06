package space.panrid.novelka.autotranslate.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WordFormsTests {

    @Test
    void aNameIsFoundAndChangedInEveryCaseKeepingItsEnding() {
        String text = "Орест пішов. Ореста не було. Я дав Орестові ключ. О, Оресте! Орестина річ лишилась. Простий Оре.";
        assertThat(WordForms.find(text, "Орест")).hasSize(5);
        assertThat(WordForms.replace(text, "Орест", "Остап"))
                .isEqualTo("Остап пішов. Остапа не було. Я дав Остапові ключ. О, Остапе! Остапина річ лишилась. Простий Оре.");
    }

    @Test
    void aWordEndingInAVowelChangesItsStem() {
        assertThat(WordForms.replace("Марія співала, а Марії аплодували.", "Марія", "Мірела"))
                .isEqualTo("Мірела співала, а Мірелі аплодували.");
        assertThat(WordForms.replace("мавка і мавки", "мавка", "русалка")).as("lower case stays lower").isEqualTo("русалка і русалки");
    }

    @Test
    void aTermOfSeveralWordsKeepsEachWordsEnding() {
        assertThat(WordForms.replace("Він кинув Крижаний спис. Крижаного списа не стало.", "Крижаний спис", "Льодяний спис"))
                .isEqualTo("Він кинув Льодяний спис. Льодяного списа не стало.");
        assertThat(WordForms.replace("Вона взяла Крижаний спис.", "Крижаний спис", "Спис"))
                .as("another number of words: the whole term as written").isEqualTo("Вона взяла Спис.");
    }
}
