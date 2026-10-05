package space.panrid.novelka.text;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Volumes of a translation and the chapter numbers readers see (етап 15). A volume runs from
 * its first chapter to the next volume; chapters before the first one belong to none.
 */
public interface Volumes {

    Set<String> KINDS = Set.of("volume", "prologue", "side", "extra");

    /** @param kind volume, prologue, side or extra; only a volume counts in the numbering */
    record Volume(int firstNumber, String title, String kind) {

        public boolean counted() {
            return "volume".equals(kind);
        }
    }

    /** @param manual the number was typed in (or came from the original) and is kept */
    record ChapterLine(int number, String title, String label, boolean manual, boolean published) {
    }

    /** @param numbering continuous (through all volumes) or per_volume (from 1 in each) */
    record Structure(String numbering, List<Volume> volumes, List<ChapterLine> chapters) {
    }

    /**
     * A change of the structure.
     *
     * @param automatic chapters whose typed number is dropped for the automatic one
     * @param unnumbered chapters that get no number at all
     */
    record Change(String numbering, List<Volume> volumes, List<Integer> automatic, List<Integer> unnumbered) {
    }

    Structure structure(long editionId);

    /** What the change would make of every chapter's number, without saving it. */
    Map<Integer, String> preview(long editionId, Change change);

    Structure save(long editionId, Change change);

    /** The volumes alone, for readers. */
    List<Volume> volumes(long editionId);

    /** Works the automatic numbers out again, after chapters came or went. */
    void renumber(long editionId);
}
