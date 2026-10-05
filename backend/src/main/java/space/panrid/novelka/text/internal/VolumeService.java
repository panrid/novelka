package space.panrid.novelka.text.internal;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.VOLUME;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.Volumes;

@Service
class VolumeService implements Volumes {

    private static final Set<String> NUMBERINGS = Set.of("continuous", "per_volume");

    private final DSLContext db;

    VolumeService(DSLContext db) {
        this.db = db;
    }

    /** A chapter's number after a change: the label readers see and whether it was typed in. */
    record Numbered(int number, String label, boolean manual) {
    }

    record Row(long id, int number, String label, boolean manual) {
    }

    /**
     * The rules (етап 15): a typed number stays; a chapter in a prologue, side stories or
     * extras has none; others count through the counted volumes, or from 1 in each. Without
     * volumes and with numbering through everything, a chapter shows its position as before.
     */
    static List<Numbered> number(List<Row> chapters, List<Volume> volumes, String numbering, Set<Integer> automatic,
            Set<Integer> unnumbered) {
        List<Volume> sorted = volumes.stream().sorted(Comparator.comparingInt(Volume::firstNumber)).toList();
        boolean plain = sorted.isEmpty() && "continuous".equals(numbering);
        List<Numbered> out = new ArrayList<>();
        int uncountedBefore = 0;
        Volume current = null;
        int inVolume = 0;
        int v = 0;
        for (Row chapter : chapters) {
            while (v < sorted.size() && sorted.get(v).firstNumber() <= chapter.number()) {
                current = sorted.get(v++);
                inVolume = 0;
            }
            inVolume++;
            boolean counted = current == null || current.counted();
            boolean manual = chapter.manual();
            String label = chapter.label();
            if (unnumbered.contains(chapter.number())) {
                manual = true;
                label = "";
            } else if (automatic.contains(chapter.number())) {
                manual = false;
            }
            if (!manual) {
                if (plain) {
                    label = null;
                } else if (!counted) {
                    label = "";
                } else {
                    int value = "per_volume".equals(numbering) && current != null ? inVolume : chapter.number() - uncountedBefore;
                    label = value == chapter.number() ? null : String.valueOf(value);
                }
            }
            if (!counted) {
                uncountedBefore++;
            }
            out.add(new Numbered(chapter.number(), label, manual));
        }
        return out;
    }

    private List<Row> rows(long editionId) {
        return db.select(CHAPTER.ID, CHAPTER.NUMBER, CHAPTER.LABEL, CHAPTER.LABEL_MANUAL).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(CHAPTER.NUMBER)
                .fetch(r -> new Row(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    @Override
    public List<Volume> volumes(long editionId) {
        return db.select(VOLUME.FIRST_NUMBER, VOLUME.TITLE, VOLUME.KIND).from(VOLUME).where(VOLUME.EDITION_ID.eq(editionId))
                .orderBy(VOLUME.FIRST_NUMBER).fetch(r -> new Volume(r.value1(), r.value2(), r.value3()));
    }

    private String numbering(long editionId) {
        return db.select(EDITION.NUMBERING).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOptional(EDITION.NUMBERING)
                .orElseThrow(() -> UserFacingException.notFound("Такого перекладу немає."));
    }

    @Override
    public Structure structure(long editionId) {
        String numbering = numbering(editionId);
        var title = db.select(REVISION.TITLE).from(REVISION).where(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID)).asField("title");
        List<ChapterLine> chapters = db.select(CHAPTER.NUMBER, title, CHAPTER.LABEL, CHAPTER.LABEL_MANUAL, CHAPTER.PUBLISHED_REVISION_ID)
                .from(CHAPTER).where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(CHAPTER.NUMBER)
                .fetch(r -> new ChapterLine(r.value1(), Objects.requireNonNullElse((String) r.value2(), ""), r.value3(), r.value4(),
                        r.value5() != null));
        return new Structure(numbering, volumes(editionId), chapters);
    }

    @Override
    public Map<Integer, String> preview(long editionId, Change change) {
        Change checked = checked(change);
        Map<Integer, String> labels = new LinkedHashMap<>();
        number(rows(editionId), checked.volumes(), checked.numbering(), Set.copyOf(checked.automatic()), Set.copyOf(checked.unnumbered()))
                .forEach(line -> labels.put(line.number(), line.label() == null ? String.valueOf(line.number()) : line.label()));
        return labels;
    }

    @Override
    @Transactional
    public Structure save(long editionId, Change change) {
        Change checked = checked(change);
        numbering(editionId);
        db.execute("SELECT 1 FROM edition WHERE id = ? FOR UPDATE", editionId);
        db.deleteFrom(VOLUME).where(VOLUME.EDITION_ID.eq(editionId)).execute();
        for (Volume volume : checked.volumes()) {
            db.insertInto(VOLUME).set(VOLUME.EDITION_ID, editionId).set(VOLUME.FIRST_NUMBER, volume.firstNumber())
                    .set(VOLUME.TITLE, volume.title()).set(VOLUME.KIND, volume.kind()).execute();
        }
        db.update(EDITION).set(EDITION.NUMBERING, checked.numbering()).where(EDITION.ID.eq(editionId)).execute();
        apply(editionId, checked.volumes(), checked.numbering(), Set.copyOf(checked.automatic()), Set.copyOf(checked.unnumbered()));
        return structure(editionId);
    }

    @Override
    @Transactional
    public void renumber(long editionId) {
        apply(editionId, volumes(editionId), numbering(editionId), Set.of(), Set.of());
    }

    private void apply(long editionId, List<Volume> volumes, String numbering, Set<Integer> automatic, Set<Integer> unnumbered) {
        List<Row> rows = rows(editionId);
        List<Numbered> numbered = number(rows, volumes, numbering, automatic, unnumbered);
        for (int i = 0; i < rows.size(); i++) {
            Row before = rows.get(i);
            Numbered after = numbered.get(i);
            if (!Objects.equals(before.label(), after.label()) || before.manual() != after.manual()) {
                db.update(CHAPTER).set(CHAPTER.LABEL, after.label()).set(CHAPTER.LABEL_MANUAL, after.manual())
                        .where(CHAPTER.ID.eq(before.id())).execute();
            }
        }
    }

    private static Change checked(Change change) {
        String numbering = change.numbering() == null ? "continuous" : change.numbering();
        if (!NUMBERINGS.contains(numbering)) {
            throw UserFacingException.badRequest("Невідома нумерація.");
        }
        List<Volume> volumes = new ArrayList<>();
        Set<Integer> starts = new HashSet<>();
        for (Volume volume : change.volumes() == null ? List.<Volume>of() : change.volumes()) {
            String title = volume.title() == null ? "" : volume.title().strip();
            if (volume.firstNumber() < 1 || !starts.add(volume.firstNumber())) {
                throw UserFacingException.badRequest("Два томи не можуть починатися з однієї глави.");
            }
            if (volume.kind() == null || !KINDS.contains(volume.kind())) {
                throw UserFacingException.badRequest("Невідомий вид тому.");
            }
            if (title.length() > 120) {
                throw UserFacingException.badRequest("Назва тому — до 120 знаків.");
            }
            volumes.add(new Volume(volume.firstNumber(), title, volume.kind()));
        }
        if (volumes.size() > 500) {
            throw UserFacingException.badRequest("Забагато томів.");
        }
        return new Change(numbering, volumes, change.automatic() == null ? List.of() : change.automatic(),
                change.unnumbered() == null ? List.of() : change.unnumbered());
    }
}
