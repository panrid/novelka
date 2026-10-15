package space.panrid.novelka.studio.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PictureLibraryNameTests {

    @Test
    void aFileNameSaysChapterAndParagraph() {
        assertThat(PictureLibrary.spot("27-5.png")).containsExactly(27, 5);
        assertThat(PictureLibrary.spot("027-05.jpg")).containsExactly(27, 5);
        assertThat(PictureLibrary.spot("c027-s5.png")).containsExactly(27, 5);
        assertThat(PictureLibrary.spot("27_5 (2).png")).containsExactly(27, 5);
        assertThat(PictureLibrary.spot("1-0.png")).containsExactly(1, 0);
    }

    @Test
    void otherNamesSayNothing() {
        assertThat(PictureLibrary.spot("picture.png")).isNull();
        assertThat(PictureLibrary.spot("Сцена в лісі.png")).isNull();
        assertThat(PictureLibrary.spot("27.png")).isNull();
        assertThat(PictureLibrary.spot(null)).isNull();
    }
}
