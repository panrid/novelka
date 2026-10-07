package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTag;
import space.panrid.novelka.autotranslate.internal.Checkpoint.Line;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.BadOutput;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.Cancelled;
import space.panrid.novelka.autotranslate.internal.PipelineErrors.OverBudget;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.jooq.tables.records.JobStepRecord;
import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.ChapterLabels;
import space.panrid.novelka.platform.text.Span;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceText;
import space.panrid.novelka.source.Sources;
import space.panrid.novelka.text.Chapters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One chapter: fetch the original → analyze (glossary, title) → translate part by part →
 * proofread part by part → publish. The checkpoint is saved after every answer.
 */
@Component
class Pipeline {

    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);
    private static final int ATTEMPTS = 3;

    private final DSLContext db;
    private final Ai ai;
    private final Sources sources;
    private final Chapters chapters;
    private final Images images;
    private final Glossary glossary;
    private final Analyses analyses;
    private final JsonMapper json;
    private final Clock clock;
    private final Progress progress;

    private final JobLog journal;
    private final Shutdown shutdown;

    Pipeline(DSLContext db, Ai ai, Sources sources, Chapters chapters, Images images, Glossary glossary, Analyses analyses,
            JsonMapper json, Clock clock, Progress progress, JobLog journal, Shutdown shutdown) {
        this.shutdown = shutdown;
        this.journal = journal;
        this.progress = progress;
        this.analyses = analyses;
        this.db = db;
        this.ai = ai;
        this.sources = sources;
        this.chapters = chapters;
        this.images = images;
        this.glossary = glossary;
        this.json = json;
        this.clock = clock;
    }

    void run(JobRecord job, JobStepRecord step) {
        Settings settings = json.readValue(job.getSettings().data(), Settings.class);
        Checkpoint checkpoint = json.readValue(step.getCheckpoint().data(), Checkpoint.class);
        long editionId = job.getEditionId();
        int number = step.getChapterNumber();
        long novelId = db.select(space.panrid.novelka.jooq.Tables.EDITION.NOVEL_ID)
                .from(space.panrid.novelka.jooq.Tables.EDITION)
                .where(space.panrid.novelka.jooq.Tables.EDITION.ID.eq(editionId)).fetchSingle().value1();

        SourceText source = sources.chapter(novelId, number);
        if (checkpoint.sourceId == null) {
            checkpoint.sourceId = source.id();
            checkpoint.chars = source.chars();
            save(step, "analyze", checkpoint);
            journal.add(job.getId(), number, "start", Map.of("chars", source.chars(), "paragraphs", source.blocks().size()));
        }
        List<Block> text = source.blocks().stream().filter(block -> !block.text().isBlank()).toList();
        Calls calls = new Calls(job, settings, number);

        // Analysis done earlier (maybe by an «analysis only» job, maybe corrected since) is reused.
        // «Зробити заново» for analysis asks the model again even where an analysis exists.
        boolean redoAnalysis = "analyze".equals(job.getKind()) && settings.redo() != null;
        boolean analyzedBefore = !redoAnalysis
                && analyses.find(editionId, number).filter(done -> done.sourceChapterId() == source.id()).isPresent();
        List<List<Block>> bigParts = parts(text, settings.segmentChars() * 3);
        for (int part = 0; part < bigParts.size() && !analyzedBefore; part++) {
            if (checkpoint.analyzed.contains(part)) {
                continue;
            }
            JsonNode answer = analyze(calls, editionId, part, part == 0 ? source.title() : "", bigParts.get(part));
            if (part == 0) {
                checkpoint.title = answer.path("title").asString("").strip();
            }
            String narrator = answer.path("narrator").path("name").asString("").strip();
            if ((checkpoint.narrator == null || checkpoint.narrator.isEmpty()) && !narrator.isEmpty()) {
                checkpoint.narrator = narrator;
                checkpoint.narratorGender = answer.path("narrator").path("gender").asString("unknown");
            }
            List<Glossary.Proposed> proposed = new ArrayList<>();
            for (JsonNode entry : answer.path("entries")) {
                proposed.add(new Glossary.Proposed(entry.path("original").asString(""), entry.path("reading").asString(""),
                        entry.path("ukrainian").asString(""), entry.path("kind").asString(""),
                        entry.path("gender").asString(""), entry.path("note").asString("")));
            }
            List<Glossary.Known> known = new ArrayList<>();
            for (JsonNode item : answer.path("known")) {
                known.add(new Glossary.Known(item.path("id").asLong(0), item.path("original").asString("")));
            }
            int linked = glossary.link(editionId, known);
            List<String> added = new ArrayList<>();
            glossary.addFromAnalysis(editionId, number, proposed, added);
            journal.add(job.getId(), number, "analysis", Map.of("part", part, "added", added, "linked", linked,
                    "title", part == 0 && checkpoint.title != null ? checkpoint.title : ""));
            checkpoint.analyzed.add(part);
            save(step, "analyze", checkpoint);
        }

        if (analyzedBefore && checkpoint.analyzed.isEmpty() && !checkpoint.reusedNoted) {
            journal.add(job.getId(), number, "analysis_reused", Map.of());
            checkpoint.reusedNoted = true;
        }
        if (!analyzedBefore) {
            String label = analyses.label(novelId, source.title());
            String title = checkpoint.title == null ? "" : checkpoint.title;
            if (label != null && !label.isEmpty() || ChapterLabels.fromJapanese(source.title()).isPresent()) {
                title = ChapterLabels.withoutNumber(title);
            }
            analyses.save(editionId, number, source.id(), title, label, checkpoint.narrator, checkpoint.narratorGender, job.getId());
        }
        if ("analyze".equals(job.getKind())) {
            save(step, "done", checkpoint);
            return;
        }
        calls.narrator = analyses.find(editionId, number)
                .map(done -> Prompts.narrator(done.narrator(), done.narratorGender())).orElse("");

        if (checkpoint.linesPerPart == null) {
            checkpoint.linesPerPart = checkpoint.draft.isEmpty() ? LINES_PER_PART : Integer.MAX_VALUE;
        }
        List<List<Block>> parts = parts(text, settings.segmentChars(), checkpoint.linesPerPart);
        String previousSummary = previousChapterSummary(editionId, number);
        for (int part = 0; part < parts.size(); part++) {
            String key = String.valueOf(part);
            if (checkpoint.draft.containsKey(key)) {
                continue;
            }
            String context = part == 0 ? previousSummary : checkpoint.summaries.get(String.valueOf(part - 1));
            List<Line> before = part == 0 ? List.of() : tail(checkpoint.draft.get(String.valueOf(part - 1)));
            Translated translated = translate(calls, editionId, part, parts.get(part), context, before);
            journal.add(job.getId(), number, "translated", Map.of("part", part, "of", parts.size(), "lines", translated.lines().size(),
                    "glossary", glossary.mentionedIn(editionId, joined(parts.get(part))).stream().map(Glossary.Entry::ukrainian).toList()));
            checkpoint.draft.put(key, translated.lines());
            checkpoint.summaries.put(key, translated.summary());
            save(step, "translate", checkpoint);
        }

        if (settings.proofread().enabled()) {
            for (int part = 0; part < parts.size(); part++) {
                String key = String.valueOf(part);
                if (checkpoint.revised.containsKey(key)) {
                    continue;
                }
                List<Line> revised = proofread(calls, editionId, part, parts.get(part), checkpoint.draft.get(key));
                journal.add(job.getId(), number, "proofread", Map.of("part", part, "of", parts.size(),
                        "changes", changes(checkpoint.draft.get(key), revised)));
                checkpoint.revised.put(key, revised);
                save(step, "proofread", checkpoint);
            }
        }

        if (checkpoint.revisionId == null) {
            save(step, "publish", checkpoint);
            Map<String, String> translated = new HashMap<>();
            for (int part = 0; part < parts.size(); part++) {
                String key = String.valueOf(part);
                List<Line> lines = settings.proofread().enabled() ? checkpoint.revised.get(key) : checkpoint.draft.get(key);
                lines.forEach(line -> translated.put(line.id(), line.text()));
            }
            List<Block> blocks = new ArrayList<>();
            for (Block block : source.blocks()) {
                switch (block.type()) {
                    case "separator" -> blocks.add(block);
                    case "image" -> picture(job, block).ifPresent(blocks::add);
                    default -> {
                        String line = translated.get(block.id());
                        if (line != null && !line.isBlank()) {
                            blocks.add(new Block(block.id(), block.type(), List.of(Span.plain(line.strip())), null, null));
                        }
                    }
                }
            }
            Analyses.Analysis analysis = analyses.find(editionId, number).orElseThrow();
            checkpoint.summary = summary(checkpoint, parts.size());
            checkpoint.revisionId = chapters.publishMachine(editionId, number, analysis.label(), analysis.title(), blocks,
                    source.id(), source.chars(), job.getId(), source.hash());
            journal.add(job.getId(), number, "published", Map.of("title", analysis.title(),
                    "label", analysis.label() == null ? String.valueOf(number) : analysis.label(), "paragraphs", blocks.size()));
        }
        save(step, "done", checkpoint);
    }

    // ---- the three kinds of calls -----------------------------------------------------------

    private JsonNode analyze(Calls calls, long editionId, int part, String title, List<Block> blocks) {
        String text = joined(blocks);
        StringBuilder user = new StringBuilder();
        user.append("Glossary (already known, do not repeat):\n").append(glossaryLines(editionId, text)).append("\n\n");
        List<Glossary.Entry> unlinked = glossary.unlinked(editionId);
        if (!unlinked.isEmpty()) {
            user.append("Glossary entries with no form in this language yet:\n");
            unlinked.forEach(entry -> user.append(entry.unlinkedLine()).append('\n'));
            user.append('\n');
        }
        user.append("Chapter title: ").append(title.isBlank() ? "(none)" : title).append("\n\nText:\n").append(text);
        return calls.ask("analyze", part, Prompts.ANALYZE, user.toString(), "glossary", Prompts.analyzeSchema(),
                Math.min(16_000, 2_000 + blocks.size() * 40), answer -> null);
    }

    record Translated(List<Line> lines, String summary) {
    }

    /**
     * A part in one request. Models sometimes leave out a few lines (an author's note, an
     * announcement); those are then asked for on their own instead of paying for the whole
     * part again.
     */
    private Translated translate(Calls calls, long editionId, int part, List<Block> blocks, String context, List<Line> before) {
        JsonNode answer;
        try {
            answer = calls.ask("translate", part, Prompts.TRANSLATE, translateRequest(editionId, calls.narrator, blocks, context, before),
                    "translation", Prompts.blocksSchema(true), maxTokens(joined(blocks)), reply -> usable(reply, blocks));
        } catch (BadOutput unusable) {
            if (blocks.size() < MIN_SPLIT) {
                throw unusable;
            }
            // A long run of short lines is where a model loses its place: halves keep it in step.
            log.info("Chapter {} part {}: {}; translating it in halves", calls.number, part, unusable.getMessage());
            journal.add(calls.job.getId(), calls.number, "split", Map.of("part", part, "lines", blocks.size(), "reason", unusable.getMessage()));
            int half = blocks.size() / 2;
            Translated first = translate(calls, editionId, part, blocks.subList(0, half), context, before);
            Translated second = translate(calls, editionId, part, blocks.subList(half, blocks.size()), first.summary(), first.lines());
            List<Line> all = new ArrayList<>(first.lines());
            all.addAll(second.lines());
            return new Translated(all, (first.summary() + " " + second.summary()).strip());
        }
        List<Line> lines = lines(answer);
        List<Block> missing = missing(blocks, lines);
        if (!missing.isEmpty()) {
            log.info("Chapter {} part {}: {} lines left out, asking for them alone", calls.number, part, missing.size());
            journal.add(calls.job.getId(), calls.number, "missing", Map.of("part", part, "lines", missing.size()));
            JsonNode extra = calls.ask("translate", part, Prompts.TRANSLATE, translateRequest(editionId, calls.narrator, missing, context, lines),
                    "translation", Prompts.blocksSchema(true), maxTokens(joined(missing)), reply -> complete(reply, missing));
            lines = merged(blocks, lines, lines(extra));
        }
        return new Translated(lines, answer.path("summary").asString("").strip());
    }

    private String translateRequest(long editionId, String narrator, List<Block> blocks, String context, List<Line> before) {
        String text = joined(blocks);
        StringBuilder user = new StringBuilder();
        user.append("Glossary:\n").append(glossaryLines(editionId, text)).append("\n\n");
        if (!narrator.isEmpty()) {
            user.append(narrator).append("\n\n");
        }
        if (context != null && !context.isBlank()) {
            user.append("What happened just before (context only, do not translate):\n").append(context).append("\n\n");
        }
        if (!before.isEmpty()) {
            user.append("The previous translated lines (context only):\n");
            tail(before).forEach(line -> user.append(line.text()).append('\n'));
            user.append('\n');
        }
        user.append("Blocks to translate (JSON):\n").append(json.writeValueAsString(input(blocks)));
        return user.toString();
    }

    /** The edited part; a line the editor left out keeps its draft translation. */
    private List<Line> proofread(Calls calls, long editionId, int part, List<Block> blocks, List<Line> draft) {
        String text = joined(blocks);
        Map<String, String> byId = new HashMap<>();
        draft.forEach(line -> byId.put(line.id(), line.text()));
        List<Map<String, String>> pairs = new ArrayList<>();
        for (Block block : blocks) {
            Map<String, String> pair = new LinkedHashMap<>();
            pair.put("id", block.id());
            pair.put("original", block.text());
            pair.put("draft", byId.getOrDefault(block.id(), ""));
            pairs.add(pair);
        }
        String user = "Glossary:\n" + glossaryLines(editionId, text) + "\n\n"
                + (calls.narrator.isEmpty() ? "" : calls.narrator + "\n\n") + "Blocks (JSON):\n" + json.writeValueAsString(pairs);
        JsonNode answer;
        try {
            answer = calls.ask("proofread", part, Prompts.PROOFREAD, user, "proofread", Prompts.blocksSchema(false),
                    maxTokens(text), reply -> usable(reply, blocks));
        } catch (BadOutput unusable) {
            // Better the draft than an edit that lost its place.
            log.warn("Chapter {} part {}: proofreading unusable ({}), the draft stays", calls.number, part, unusable.getMessage());
            journal.add(calls.job.getId(), calls.number, "proofread_skipped", Map.of("part", part, "reason", unusable.getMessage()));
            return draft;
        }
        return merged(blocks, lines(answer), draft);
    }

    /**
     * Null when the answer is the given blocks in order, each at most once and with text,
     * possibly with a few left out; otherwise what is wrong.
     */
    static String usable(JsonNode answer, List<Block> blocks) {
        JsonNode lines = answer.path("blocks");
        List<Boolean> speech = speech(blocks);
        String previousOff = null;
        int at = 0;
        for (JsonNode line : lines) {
            String id = line.path("id").asString("");
            while (at < blocks.size() && !blocks.get(at).id().equals(id)) {
                at++;
            }
            if (at == blocks.size()) {
                return "абзац «%s» зайвий, повторений або переставлений".formatted(id);
            }
            if (line.path("text").asString("").isBlank()) {
                return "абзац " + id + " порожній";
            }
            if (!sameStart(blocks.get(at).text(), line.path("start").asString(null))) {
                return "переклад абзацу %s не від його оригіналу: переклади зсунулися".formatted(id);
            }
            String left = leftover(id, line.path("text").asString(""));
            if (left != null) {
                return left;
            }
            boolean off = !fitsSpeech(speech.get(at), line.path("text").asString(""));
            if (off && previousOff != null) {
                return "репліки й оповідь не на своїх місцях (абзаци %s і %s): переклади зсунулися".formatted(previousOff, id);
            }
            previousOff = off ? id : null;
            at++;
        }
        if (lines.size() * 2 < blocks.size()) {
            return "абзаців %d замість %d".formatted(lines.size(), blocks.size());
        }
        return null;
    }

    /**
     * Whether «start», as the model copied it, is the beginning of this line's original. Spaces,
     * punctuation and brackets are ignored and two letters are enough: a model that lost its
     * place quotes another line. No «start» at all (an older answer) is not held against it.
     */
    static boolean sameStart(String original, String start) {
        if (start == null) {
            return true;
        }
        String source = letters(original);
        String quoted = letters(start);
        if (source.isEmpty()) {
            return true;
        }
        int length = Math.min(2, Math.min(source.length(), quoted.length()));
        return length > 0 && source.substring(0, length).equalsIgnoreCase(quoted.substring(0, length));
    }

    private static String letters(String text) {
        return text.replaceAll("[\\p{P}\\p{S}\\p{Z}\\s]", "");
    }

    private static List<Block> missing(List<Block> blocks, List<Line> lines) {
        java.util.Set<String> done = new java.util.HashSet<>();
        lines.forEach(line -> done.add(line.id()));
        return blocks.stream().filter(block -> !done.contains(block.id())).toList();
    }

    /** Lines in the order of the blocks, taken from the first list that has them. */
    private static List<Line> merged(List<Block> blocks, List<Line> first, List<Line> second) {
        Map<String, Line> a = new HashMap<>();
        first.forEach(line -> a.put(line.id(), line));
        Map<String, Line> b = new HashMap<>();
        second.forEach(line -> b.put(line.id(), line));
        List<Line> out = new ArrayList<>();
        for (Block block : blocks) {
            Line line = a.containsKey(block.id()) ? a.get(block.id()) : b.get(block.id());
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    /** Null when every block came back exactly once with text; otherwise what is wrong. */
    private static String complete(JsonNode answer, List<Block> blocks) {
        JsonNode lines = answer.path("blocks");
        if (lines.size() != blocks.size()) {
            return "абзаців %d замість %d".formatted(lines.size(), blocks.size());
        }
        for (int i = 0; i < blocks.size(); i++) {
            JsonNode line = lines.get(i);
            if (!blocks.get(i).id().equals(line.path("id").asString("")) || line.path("text").asString("").isBlank()) {
                return "абзац " + blocks.get(i).id() + " пропущено або переставлено";
            }
            if (!sameStart(blocks.get(i).text(), line.path("start").asString(null))) {
                return "переклад абзацу %s не від його оригіналу: переклади зсунулися".formatted(blocks.get(i).id());
            }
            String left = leftover(blocks.get(i).id(), line.path("text").asString(""));
            if (left != null) {
                return left;
            }
        }
        return null;
    }

    private static final java.util.regex.Pattern UNTRANSLATED =
            java.util.regex.Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]{3,}|\\[term]");

    /** What is wrong when a translation still has Japanese in it or a model's marker such as «[term]»; null when clean. */
    static String leftover(String id, String text) {
        return UNTRANSLATED.matcher(text).find()
                ? "у перекладі абзацу %s лишився японський текст або службова позначка".formatted(id)
                : null;
    }

    /**
     * Where each line stands in the dialogue: true when it opens a speech in 「」, false for
     * narration, null when it cannot be told — a speech going on from the line before, a quote in
     * 『』 or a thought in （）, a line opening with a dash, or the beginning of a part that may sit
     * inside a speech. A translation that slipped by a line puts speech under narration and back.
     */
    static List<Boolean> speech(List<Block> blocks) {
        List<Boolean> out = new ArrayList<>();
        Integer depth = null;
        for (Block block : blocks) {
            String text = block.text().strip();
            int opens = (int) text.chars().filter(c -> c == '「').count();
            int closes = (int) text.chars().filter(c -> c == '」').count();
            if (text.startsWith("「")) {
                out.add(true);
                depth = Math.max(0, opens - closes);
            } else if (depth == null) {
                out.add(null);
                if (closes > 0 || opens > 0) {
                    depth = Math.max(0, opens - closes);
                }
            } else if (depth > 0) {
                out.add(null);
                depth = Math.max(0, depth + opens - closes);
            } else {
                boolean unclear = text.isEmpty() || "『（(―—─ー“\"".indexOf(text.charAt(0)) >= 0;
                out.add(unclear ? null : false);
                depth = Math.max(0, opens - closes);
            }
        }
        return out;
    }

    /** Whether the translation opens as its original does: a speech with a dash or a quote, narration without a dash. */
    static boolean fitsSpeech(Boolean speech, String translation) {
        if (speech == null) {
            return true;
        }
        String text = translation.strip();
        boolean dash = text.startsWith("—") || text.startsWith("–") || text.startsWith("-");
        return speech ? dash || text.startsWith("«") || text.startsWith("„") || text.startsWith("\"") : !dash;
    }

    /**
     * Asks, checks, and asks again (a new paid request) if the answer is unusable, at most
     * three times. Before each request: is the job still wanted, and is it within its limit?
     */
    private final class Calls {
        private final JobRecord job;
        private final Settings settings;
        private final int number;
        /** What the prompts say about the chapter's first-person narrator; known once analysis is done. */
        private String narrator = "";

        Calls(JobRecord job, Settings settings, int number) {
            this.job = job;
            this.settings = settings;
            this.number = number;
        }

        JsonNode ask(String stage, int part, String system, String user, String schemaName, Map<String, Object> schema,
                int maxTokens, Function<JsonNode, String> problem) {
            Settings.Stage model = switch (stage) {
                case "analyze" -> settings.analyze();
                case "proofread" -> settings.proofread();
                default -> settings.translate();
            };
            String last = "";
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                guard();
                AiAnswer answer = ai.ask(new AiRequest(model.model(), system, user, schemaName, schema, maxTokens,
                        model.price(), new AiTag(job.getId(), number, stage, part), attempt + settings.salt()));
                if (answer.cut()) {
                    last = "відповідь обірвалася на межі довжини";
                    continue;
                }
                JsonNode parsed;
                try {
                    parsed = lenient(json, answer.content());
                } catch (RuntimeException notJson) {
                    last = "відповідь не у форматі JSON";
                    journal.add(job.getId(), number, "retry", Map.of("stage", stage, "part", part, "reason", last));
                    continue;
                }
                String wrong = problem.apply(parsed);
                if (wrong == null) {
                    return parsed;
                }
                last = wrong;
                log.info("Job {} chapter {} {} part {}: unusable answer ({}), asking again", job.getId(), number, stage, part, wrong);
                journal.add(job.getId(), number, "retry", Map.of("stage", stage, "part", part, "reason", wrong));
            }
            throw new BadOutput("Модель тричі повернула неповну відповідь (глава %d, %s): %s."
                    .formatted(number, stageName(stage), last));
        }

        private void guard() {
            if (shutdown.stopping()) {
                throw new Shutdown.Stopping();
            }
            String state = db.select(JOB.STATE).from(JOB).where(JOB.ID.eq(job.getId())).fetchSingle().value1();
            if (state.equals("cancelled")) {
                throw new Cancelled();
            }
            if ("account".equals(job.getFunding())) {
                // A person's run may spend what it holds, not a cent more (рішення 29).
                if (ai.spentMicroUsd(job.getId()) >= job.getQuoteShah() * settings.microUsdPerShah()) {
                    throw new OverBudget("Витрати дійшли до зарезервованих шагів (%d). Скасуйте запуск, щоб списати витрачене,"
                            .formatted(job.getQuoteShah()) + " і запустіть решту глав заново.");
                }
                return;
            }
            long cap = Math.max(200_000, Math.round(job.getQuoteShah() * settings.microUsdPerShah() * settings.capFactor()));
            if (ai.spentMicroUsd(job.getId()) > cap) {
                throw new OverBudget("Витрати перевищили кошторис у %s раза. Перевірте моделі й ціни в налаштуваннях."
                        .formatted(settings.capFactor()));
            }
        }
    }

    static String stageName(String stage) {
        return switch (stage) {
            case "analyze" -> "аналіз";
            case "translate" -> "переклад";
            case "proofread" -> "вичитка";
            default -> stage;
        };
    }

    // ---- helpers ----------------------------------------------------------------------------

    /** Whole blocks grouped up to {@code limit} characters; a longer block goes alone. */
    /** Lines in one translation request: longer runs of short lines is where models lose their place. */
    static final int LINES_PER_PART = 40;
    /** A part this short that still comes back out of step is not split further. */
    private static final int MIN_SPLIT = 8;

    static List<List<Block>> parts(List<Block> blocks, int limit) {
        return parts(blocks, limit, Integer.MAX_VALUE);
    }

    static List<List<Block>> parts(List<Block> blocks, int limit, int maxLines) {
        List<List<Block>> parts = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        int size = 0;
        for (Block block : blocks) {
            int length = block.text().length();
            if (!current.isEmpty() && (size + length > limit || current.size() >= maxLines)) {
                parts.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(block);
            size += length;
        }
        if (!current.isEmpty()) {
            parts.add(current);
        }
        return parts;
    }

    private static int maxTokens(String text) {
        // Ukrainian takes roughly one token per two or three characters; leave room.
        return Math.min(32_000, 1_500 + text.length() * 2);
    }

    private static String joined(List<Block> blocks) {
        StringBuilder out = new StringBuilder();
        blocks.forEach(block -> out.append(block.text()).append('\n'));
        return out.toString();
    }

    private static List<Map<String, String>> input(List<Block> blocks) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Block block : blocks) {
            Map<String, String> line = new LinkedHashMap<>();
            line.put("id", block.id());
            if (!block.type().equals("paragraph")) {
                line.put("type", block.type());
            }
            line.put("text", block.text());
            out.add(line);
        }
        return out;
    }

    private String glossaryLines(long editionId, String text) {
        List<Glossary.Entry> entries = glossary.mentionedIn(editionId, text);
        if (entries.isEmpty()) {
            return "(empty)";
        }
        StringBuilder out = new StringBuilder();
        entries.forEach(entry -> out.append(entry.line()).append('\n'));
        return out.toString();
    }

    /**
     * The answer as the schema asked for it, even from a provider that ignores the schema
     * (Venice gave DeepSeek's answers as a ```json block of [{id, start, translation}] with the
     * summary in prose after it). Only the wrapping is forgiven: ids and starts are still checked.
     */
    static JsonNode lenient(JsonMapper json, String content) {
        String text = content == null ? "" : content.strip();
        try {
            return json.readTree(text);
        } catch (RuntimeException wrapped) {
            // a fenced or prefixed answer: take the JSON between the first bracket and its last match
        }
        int object = text.indexOf('{');
        int array = text.indexOf('[');
        boolean isArray = array >= 0 && (object < 0 || array < object);
        int from = isArray ? array : object;
        int to = isArray ? text.lastIndexOf(']') : text.lastIndexOf('}');
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("no JSON in the answer");
        }
        JsonNode inner = json.readTree(text.substring(from, to + 1));
        if (!inner.isArray()) {
            return inner;
        }
        tools.jackson.databind.node.ObjectNode wrapped = json.createObjectNode();
        tools.jackson.databind.node.ArrayNode blocks = wrapped.putArray("blocks");
        for (JsonNode item : inner) {
            tools.jackson.databind.node.ObjectNode block = item.isObject() ? ((tools.jackson.databind.node.ObjectNode) item).deepCopy()
                    : json.createObjectNode();
            if (!block.has("text")) {
                JsonNode alias = block.has("translation") ? block.get("translation") : block.get("content");
                if (alias != null) {
                    block.set("text", alias);
                }
            }
            blocks.add(block);
        }
        String after = text.substring(to + 1).replace("```", "").strip()
                .replaceFirst("^\\**\\s*(Короткий зміст|Summary)\\s*:?\\s*\\**\\s*:?", "").strip();
        wrapped.put("summary", after);
        return wrapped;
    }

    /** What proofreading changed in a part: each line's draft and final text, the first 40 of them. */
    static List<Map<String, String>> changes(List<Line> draft, List<Line> revised) {
        Map<String, String> before = new HashMap<>();
        draft.forEach(line -> before.put(line.id(), line.text()));
        List<Map<String, String>> out = new ArrayList<>();
        for (Line line : revised) {
            String was = before.get(line.id());
            if (was != null && !was.equals(line.text()) && out.size() < 40) {
                out.add(Map.of("id", line.id(), "before", was, "after", line.text()));
            }
        }
        return out;
    }

    private static List<Line> lines(JsonNode answer) {
        List<Line> out = new ArrayList<>();
        for (JsonNode line : answer.path("blocks")) {
            out.add(new Line(line.path("id").asString(), line.path("text").asString().strip()));
        }
        return out;
    }

    private static List<Line> tail(List<Line> lines) {
        return lines == null ? List.of() : lines.subList(Math.max(0, lines.size() - 2), lines.size());
    }

    private static String summary(Checkpoint checkpoint, int parts) {
        StringBuilder out = new StringBuilder();
        for (int part = 0; part < parts; part++) {
            String piece = checkpoint.summaries.get(String.valueOf(part));
            if (piece != null && !piece.isBlank()) {
                out.append(piece.strip()).append(' ');
            }
        }
        String all = out.toString().strip();
        return all.length() > 1_500 ? all.substring(all.length() - 1_500) : all;
    }

    private String previousChapterSummary(long editionId, int number) {
        JSONB stored = db.select(JOB_STEP.CHECKPOINT).from(JOB_STEP).join(JOB).on(JOB.ID.eq(JOB_STEP.JOB_ID))
                .where(JOB.EDITION_ID.eq(editionId), JOB_STEP.CHAPTER_NUMBER.eq(number - 1), JOB_STEP.STAGE.eq("done"))
                .orderBy(JOB_STEP.ID.desc()).limit(1).fetchOne(JOB_STEP.CHECKPOINT);
        return stored == null ? null : json.readTree(stored.data()).path("summary").asString(null);
    }

    private java.util.Optional<Block> picture(JobRecord job, Block block) {
        if (block.sourceUrl() == null) {
            return java.util.Optional.empty();
        }
        try {
            long imageId = images.storeFromUrl(job.getRequestedBy(), ImageKind.ILLUSTRATION, block.sourceUrl()).id();
            return java.util.Optional.of(new Block(block.id(), "image", List.of(), imageId, null));
        } catch (UserFacingException unavailable) {
            log.info("Picture {} skipped: {}", block.sourceUrl(), unavailable.getMessage());
            return java.util.Optional.empty();
        }
    }

    private void save(JobStepRecord step, String stage, Checkpoint checkpoint) {
        db.update(JOB_STEP)
                .set(JOB_STEP.STAGE, stage)
                .set(JOB_STEP.CHECKPOINT, JSONB.valueOf(json.writeValueAsString(checkpoint)))
                .set(JOB_STEP.UPDATED_AT, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
                // Still alive: extend the lease so no one takes the step over.
                .set(JOB_STEP.NOT_BEFORE, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).plusMinutes(Worker.LEASE_MINUTES))
                .where(JOB_STEP.ID.eq(step.getId()))
                .execute();
        progress.changed(step.getJobId());
    }
}
