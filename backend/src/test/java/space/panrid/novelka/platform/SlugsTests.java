package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import space.panrid.novelka.platform.text.Slugs;

class SlugsTests {

    @ParameterizedTest
    @CsvSource({
            "Маг води, mah-vody",
            "Відьма з чайної крамниці, vidma-z-chainoi-kramnytsi",
            "Юлія і Яків, yuliia-i-yakiv",
            "Згадка про Їжака, zghadka-pro-yizhaka",
            "Щастя в м'ятному саду, shchastia-v-miatnomu-sadu",
            "Re:Zero — Життя в іншому світі!, re-zero-zhyttia-v-inshomu-sviti",
            "!!!, n",
    })
    void transliteratesUkrainianTitles(String title, String slug) {
        assertThat(Slugs.slug(title, 60)).isEqualTo(slug);
    }
}
