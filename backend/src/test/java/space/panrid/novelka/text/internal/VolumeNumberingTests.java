package space.panrid.novelka.text.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.text.Volumes.Volume;

/** The numbering rules of етап 15, on chapters 1–8 of a made-up novel. */
class VolumeNumberingTests {

    private static List<VolumeService.Row> chapters(int count) {
        List<VolumeService.Row> rows = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            rows.add(new VolumeService.Row(number, number, null, false));
        }
        return rows;
    }

    private static List<String> shown(List<VolumeService.Numbered> numbered) {
        return numbered.stream().map(line -> line.label() == null ? String.valueOf(line.number()) : line.label()).toList();
    }

    @Test
    void withoutVolumesAChapterShowsItsPositionAsBefore() {
        List<VolumeService.Numbered> numbered = VolumeService.number(chapters(3), List.of(), "continuous", Set.of(), Set.of());
        assertThat(numbered).extracting(VolumeService.Numbered::label).containsOnlyNulls();
    }

    @Test
    void aPrologueAndSideStoriesTakeNoNumberAndTheRestCountOn() {
        List<Volume> volumes = List.of(new Volume(1, "Пролог", "prologue"), new Volume(2, "Том 1", "volume"),
                new Volume(5, "Побічні історії", "side"), new Volume(7, "Том 2", "volume"));
        assertThat(shown(VolumeService.number(chapters(8), volumes, "continuous", Set.of(), Set.of())))
                .containsExactly("", "1", "2", "3", "", "", "4", "5");
        assertThat(shown(VolumeService.number(chapters(8), volumes, "per_volume", Set.of(), Set.of())))
                .as("from 1 in each volume").containsExactly("", "1", "2", "3", "", "", "1", "2");
    }

    @Test
    void aTypedNumberStaysUntilItIsMadeAutomaticAgain() {
        List<VolumeService.Row> rows = new ArrayList<>(chapters(4));
        rows.set(2, new VolumeService.Row(3, 3, "2.1", true));
        List<Volume> volumes = List.of(new Volume(1, "Пролог", "prologue"), new Volume(2, "", "volume"));
        assertThat(shown(VolumeService.number(rows, volumes, "continuous", Set.of(), Set.of()))).containsExactly("", "1", "2.1", "3");
        assertThat(shown(VolumeService.number(rows, volumes, "continuous", Set.of(3), Set.of()))).containsExactly("", "1", "2", "3");
        assertThat(shown(VolumeService.number(rows, List.of(), "continuous", Set.of(), Set.of(4))))
                .as("«без номера» for one chapter").containsExactly("1", "2", "2.1", "");
    }
}
