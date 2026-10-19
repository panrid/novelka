package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB_EVENT;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.autotranslate.internal.Checkpoint.Line;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.jooq.tables.records.JobStepRecord;
import space.panrid.novelka.platform.text.Block;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Хід перекладу» наживо (етап 20, рішення 17): what a run has really done to one chapter so far.
 * The pipeline saves every part the model answers before it goes on, so the page shows the
 * translation part by part as it comes — never a made-up «typing» over a text already finished.
 */
@Component
class LiveRun {

    /** Chapters of the run listed around the one at work: a run «до глави 4000» does not send them all. */
    static final int LISTED = 30;
    /** Glossary words shown beside a chapter. */
    static final int WORDS = 40;

    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;
    private final Jobs jobs;
    private final Originals originals;
    private final Analyses analyses;
    private final Glossary glossary;

    LiveRun(DSLContext db, JsonMapper json, Jobs jobs, Originals originals, Analyses analyses, Glossary glossary) {
        this.db = db;
        this.json = json;
        this.jobs = jobs;
        this.originals = originals;
        this.analyses = analyses;
        this.glossary = glossary;
    }

    /** A chapter of the run: its stage (fetch, analyze, translate, proofread, publish, done) and state. */
    record Step(int number, String stage, String state) {
    }

    /**
     * One paragraph: the original and the translation saved so far (null — not translated yet).
     *
     * @param revised proofreading has gone over it
     */
    record Paragraph(String id, String type, String original, String translation, boolean revised) {
    }

    /** A glossary word the chapter's original names; {@code status} new, approved. */
    record Word(long id, String ukrainian, String original, String kind, String gender, String status) {
    }

    /** What proofreading changed in a paragraph. */
    record Fix(String id, String before, String after) {
    }

    /**
     * @param title           the chapter's title in the original
     * @param translatedTitle its Ukrainian title once the glossary step named it
     * @param proofreading    the run proofreads: «Вичитка» is one of its stages
     */
    record Chapter(int number, String stage, String state, String error, String language, String title, String translatedTitle,
            boolean proofreading, List<Paragraph> paragraphs, List<Word> words, List<Fix> fixes) {
    }

    /** @param steps the run's chapters around the shown one, by number */
    record View(Jobs.JobView job, List<Step> steps, Chapter chapter) {
    }

    View view(JobRecord job, Integer number) {
        var ofJob = JOB_STEP.JOB_ID.eq(job.getId());
        JobStepRecord shown = number == null ? null
                : db.selectFrom(JOB_STEP).where(ofJob, JOB_STEP.CHAPTER_NUMBER.eq(number)).fetchOne();
        if (shown == null) {
            // The chapter at work; once the run is over, its last chapter.
            shown = db.selectFrom(JOB_STEP).where(ofJob, JOB_STEP.STATE.ne("done")).orderBy(JOB_STEP.CHAPTER_NUMBER).limit(1).fetchOne();
        }
        if (shown == null) {
            shown = db.selectFrom(JOB_STEP).where(ofJob).orderBy(JOB_STEP.CHAPTER_NUMBER.desc()).limit(1).fetchOne();
        }
        int around = shown == null ? job.getFirstNumber() : shown.getChapterNumber();
        List<Step> steps = db.select(JOB_STEP.CHAPTER_NUMBER, JOB_STEP.STAGE, JOB_STEP.STATE).from(JOB_STEP)
                .where(ofJob, JOB_STEP.CHAPTER_NUMBER.ge(around - 5))
                .orderBy(JOB_STEP.CHAPTER_NUMBER).limit(LISTED)
                .fetch(r -> new Step(r.value1(), r.value2(), r.value3()));
        return new View(jobs.view(job), steps, shown == null ? null : chapter(job, shown));
    }

    private Chapter chapter(JobRecord job, JobStepRecord step) {
        long editionId = job.getEditionId();
        int number = step.getChapterNumber();
        Settings settings = json.readValue(job.getSettings().data(), Settings.class);
        Checkpoint checkpoint = json.readValue(step.getCheckpoint().data(), Checkpoint.class);
        var novel = db.select(EDITION.NOVEL_ID, NOVEL.SOURCE, NOVEL.SOURCE_LANGUAGE).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchSingle();
        String title = "";
        String language = novel.value3() == null ? "ja" : novel.value3();
        List<Block> blocks = List.of();
        if (Originals.pasted(novel.value2())) {
            var text = originals.text(editionId, number);
            if (text.isPresent()) {
                title = text.get().title();
                language = text.get().language();
                blocks = text.get().blocks();
            }
        } else {
            // Only what the site already keeps: this page never downloads an original.
            var stored = db.select(SOURCE_CHAPTER.TITLE, SOURCE_CHAPTER.BLOCKS).from(SOURCE_CHAPTER)
                    .where(SOURCE_CHAPTER.NOVEL_ID.eq(novel.value1()), SOURCE_CHAPTER.NUMBER.eq(number)).fetchOne();
            if (stored != null) {
                title = stored.value1();
                blocks = json.readValue(stored.value2().data(), BLOCKS);
            }
        }

        Map<String, String> translated = new HashMap<>();
        checkpoint.draft.values().forEach(lines -> lines.forEach(line -> translated.put(line.id(), line.text())));
        Set<String> revised = new HashSet<>();
        for (List<Line> lines : checkpoint.revised.values()) {
            for (Line line : lines) {
                translated.put(line.id(), line.text());
                revised.add(line.id());
            }
        }
        List<Paragraph> paragraphs = new ArrayList<>();
        StringBuilder all = new StringBuilder(title == null ? "" : title);
        for (Block block : blocks) {
            String text = block.text();
            if (text.isBlank()) {
                continue;
            }
            all.append('\n').append(text);
            paragraphs.add(new Paragraph(block.id(), block.type(), text, translated.get(block.id()), revised.contains(block.id())));
        }

        String translatedTitle = analyses.find(editionId, number).map(Analyses.Analysis::title)
                .filter(found -> !found.isBlank()).orElse(null);
        if (translatedTitle == null && checkpoint.title != null && !checkpoint.title.isBlank()) {
            translatedTitle = checkpoint.title;
        }
        List<Word> words = glossary.mentionedIn(editionId, all.toString()).stream().limit(WORDS)
                .map(entry -> new Word(entry.id(), entry.ukrainian(), entry.original(), entry.kind(), entry.gender(), entry.status()))
                .toList();
        return new Chapter(number, step.getStage(), step.getState(), step.getError(), language, title, translatedTitle,
                settings.proofread().enabled() || "proofread".equals(job.getKind()), paragraphs, words, fixes(job.getId(), number));
    }

    /** What proofreading changed in the chapter, as the run's journal keeps it. */
    private List<Fix> fixes(long jobId, int number) {
        List<Fix> out = new ArrayList<>();
        db.select(JOB_EVENT.PAYLOAD).from(JOB_EVENT)
                .where(JOB_EVENT.JOB_ID.eq(jobId), JOB_EVENT.CHAPTER_NUMBER.eq(number), JOB_EVENT.KIND.eq("proofread"))
                .orderBy(JOB_EVENT.ID)
                .forEach(r -> {
                    for (var change : json.readTree(r.value1().data()).path("changes")) {
                        out.add(new Fix(change.path("id").asString(""), change.path("before").asString(""), change.path("after").asString("")));
                    }
                });
        return out;
    }
}
