package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.CHAPTER_ANALYSIS;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiCredits;
import space.panrid.novelka.ai.AiModel;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.SiteSettings;
import space.panrid.novelka.platform.audit.AuditLog;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

/** Quotes, starting, cancelling and resuming jobs, and what the owner sees about them. */
@Service
class Jobs {

    static final String SETTINGS_KEY = "autotranslate.settings";
    /** Guess for a chapter nobody has downloaded yet, until the novel has known chapters. */
    private static final int UNKNOWN_CHAPTER_CHARS = 6_000;

    private final DSLContext db;
    private final Ai ai;
    private final SiteSettings siteSettings;
    private final JsonMapper json;
    private final Ledger ledger;
    private final AuditLog audit;
    /** A person's run holds this much more than the quote: chapters vary and answers get asked again. */
    static final double RESERVE_MARGIN = 1.5;

    Jobs(DSLContext db, Ai ai, SiteSettings siteSettings, JsonMapper json, Ledger ledger, AuditLog audit) {
        this.audit = audit;
        this.ledger = ledger;
        this.db = db;
        this.ai = ai;
        this.siteSettings = siteSettings;
        this.json = json;
    }

    Settings settings() {
        return siteSettings.json(SETTINGS_KEY).map(value -> json.readValue(value, Settings.class)).orElseGet(Settings::defaults);
    }

    void saveSettings(Settings settings, long ownerId) {
        for (Settings.Stage stage : List.of(settings.analyze(), settings.translate(), settings.proofread())) {
            if (stage == null || stage.model() == null || !stage.model().matches("[a-z0-9._-]+/[a-zA-Z0-9._:-]+")
                    || stage.inputPerMillion() < 0 || stage.outputPerMillion() < 0
                    || stage.inputPerMillion() > 1_000 || stage.outputPerMillion() > 1_000) {
                throw UserFacingException.badRequest("Перевірте назви моделей (вигляд «постачальник/модель») і ціни.");
            }
        }
        if (settings.segmentChars() < 1_000 || settings.segmentChars() > 20_000) {
            throw UserFacingException.badRequest("Частина глави — від 1 000 до 20 000 знаків.");
        }
        if (settings.microUsdPerShah() < 1_000 || settings.microUsdPerShah() > 1_000_000) {
            throw UserFacingException.badRequest("Собівартість шагу — від $0,001 до $1.");
        }
        if (settings.capFactor() < 1.2 || settings.capFactor() > 20) {
            throw UserFacingException.badRequest("Межа витрат — від 1,2 до 20 кошторисів.");
        }
        Settings.Stage analyze = settings.analyze();
        Settings.Stage translate = settings.translate();
        Settings fixed = new Settings(new Settings.Stage(analyze.model(), analyze.inputPerMillion(), analyze.outputPerMillion(), true),
                new Settings.Stage(translate.model(), translate.inputPerMillion(), translate.outputPerMillion(), true),
                settings.proofread(), settings.segmentChars(), settings.microUsdPerShah(), settings.capFactor(), null);
        audit.record(ownerId, "autotranslate_settings", "site", null, Map.of("before", settings(), "after", fixed));
        siteSettings.put(SETTINGS_KEY, json.writeValueAsString(fixed), ownerId);
    }

    // ---- quote --------------------------------------------------------------------------------

    /**
     * @param nextNumber   first chapter not yet translated
     * @param lastAnalyzed last chapter whose analysis is ready (0 if none)
     */
    record Novel(long novelId, int sourceChapters, int nextNumber, int publishedChapters, int lastAnalyzed) {

        int nextToAnalyze() {
            return Math.max(nextNumber, lastAnalyzed + 1);
        }
    }

    Novel novel(long editionId) {
        Record row = db.select(NOVEL.ID, NOVEL.SOURCE, NOVEL.SOURCE_CHAPTER_COUNT, EDITION.FIRST_NUMBER)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої новели немає."));
        if ("manual".equals(row.get(NOVEL.SOURCE)) || "original".equals(row.get(NOVEL.SOURCE))) {
            throw UserFacingException.badRequest("Автопереклад доступний лише для новел, узятих із сайту-джерела.");
        }
        int last = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), 0)).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull()).fetchOne(0, Integer.class);
        int published = db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        Integer count = row.get(NOVEL.SOURCE_CHAPTER_COUNT);
        return new Novel(row.get(NOVEL.ID), count == null ? 0 : count, Math.max(last + 1, row.get(EDITION.FIRST_NUMBER)), published,
                db.select(DSL.coalesce(DSL.max(CHAPTER_ANALYSIS.NUMBER), 0)).from(CHAPTER_ANALYSIS)
                        .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId)).fetchOne(0, Integer.class));
    }

    /**
     * What to run. {@code from} absent: the first chapter not done yet. {@code redo}: chapters
     * already done are done again (new answers, not the saved ones); otherwise they are skipped.
     * {@code models} replace the site's models for this run only.
     */
    record Plan(String kind, Integer from, Integer to, Boolean redo, Models models) {

        boolean analyze() {
            return "analyze".equals(kind);
        }

        /** Missing fields are allowed (Jackson refuses absent primitives), so read them through these. */
        boolean again() {
            return Boolean.TRUE.equals(redo);
        }

        int last() {
            if (to == null) {
                throw UserFacingException.badRequest("Вкажіть, до якої глави.");
            }
            return to;
        }
    }

    /** Model ids for this run; null keeps the site's choice. */
    record Models(String analyze, String translate, String proofread, Boolean proofreadEnabled) {
    }

    /**
     * @param chapters    chapters that will be done
     * @param skipped     chapters in the range already done and left alone
     * @param shah        the price; for a person's run, the expected cost in whole шаги
     * @param expectedUsd what it should really cost at the chosen models
     * @param unanalyzed  chapters of a translation without analysis: their glossary cannot be checked first
     * @param reserveShah what a person's run holds until it ends (the site owner's runs hold nothing)
     */
    record Quote(String kind, int from, int to, int chapters, int skipped, int shah, BigDecimal usd, BigDecimal expectedUsd,
            boolean estimated, int unanalyzed, Settings.Stage analyzeModel, Settings.Stage translateModel,
            Settings.Stage proofreadModel, int reserveShah) {
    }

    /** Analysis is about a quarter of a chapter's work: its price in шаги. */
    static int analysisShah(int translationShah) {
        return Math.max(1, (translationShah + 3) / 4);
    }

    private record Prepared(Quote quote, List<Integer> numbers, Settings settings) {
    }

    /** @param personal paid from the person's шаги (рішення 29), not by the site owner */
    Quote quote(long editionId, Plan plan, boolean personal) {
        return prepare(editionId, plan, personal).quote();
    }

    private Prepared prepare(long editionId, Plan plan, boolean personal) {
        if (personal && plan.models() != null && (plan.models().analyze() != null || plan.models().translate() != null
                || plan.models().proofread() != null || plan.models().proofreadEnabled() != null)) {
            throw UserFacingException.badRequest("Моделі для автоперекладу обирає сайт.");
        }
        Novel novel = novel(editionId);
        boolean analyze = plan.analyze();
        int from = plan.from() != null ? plan.from() : analyze ? novel.nextToAnalyze() : novel.nextNumber();
        int to = plan.last();
        if (from < 1 || to < from) {
            throw UserFacingException.badRequest("Перевірте діапазон: «з» не може бути більшим за «по».");
        }
        if (to > novel.sourceChapters()) {
            throw UserFacingException.badRequest("В оригіналі поки %d глав.".formatted(novel.sourceChapters()));
        }
        Set<Integer> done = new HashSet<>(analyze
                ? db.select(CHAPTER_ANALYSIS.NUMBER).from(CHAPTER_ANALYSIS)
                        .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.between(from, to)).fetch(CHAPTER_ANALYSIS.NUMBER)
                : db.select(CHAPTER.NUMBER).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull(), CHAPTER.NUMBER.between(from, to))
                        .fetch(CHAPTER.NUMBER));
        List<Integer> numbers = new ArrayList<>();
        for (int number = from; number <= to; number++) {
            if (plan.again() || !done.contains(number)) {
                numbers.add(number);
            }
        }
        if (numbers.isEmpty()) {
            throw UserFacingException.badRequest((analyze ? "Ці глави вже проаналізовано." : "Ці глави вже перекладено.")
                    + " Щоб зробити їх заново, увімкніть «Зробити заново» в розширених налаштуваннях.");
        }
        Settings site = settings();
        Settings settings = withModels(site, plan.models());
        Map<Integer, Integer> known = db.select(SOURCE_CHAPTER.NUMBER, SOURCE_CHAPTER.CHARS).from(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novel.novelId())).fetchMap(SOURCE_CHAPTER.NUMBER, SOURCE_CHAPTER.CHARS);
        int guess = known.isEmpty() ? UNKNOWN_CHAPTER_CHARS
                : (int) Math.round(known.values().stream().mapToInt(Integer::intValue).average().orElse(UNKNOWN_CHAPTER_CHARS));
        Set<Integer> analyzed = new HashSet<>(db.select(CHAPTER_ANALYSIS.NUMBER).from(CHAPTER_ANALYSIS)
                .where(CHAPTER_ANALYSIS.EDITION_ID.eq(editionId), CHAPTER_ANALYSIS.NUMBER.between(from, to)).fetch(CHAPTER_ANALYSIS.NUMBER));
        int shah = 0;
        long expected = 0;
        boolean estimated = false;
        int unanalyzed = 0;
        for (int number : numbers) {
            Integer chars = known.get(number);
            estimated |= chars == null;
            int size = chars == null ? guess : chars;
            boolean needsAnalysis = analyze || !analyzed.contains(number);
            unanalyzed += !analyze && !analyzed.contains(number) ? 1 : 0;
            long chosen = settings.expectedMicroUsd(size, needsAnalysis, !analyze);
            expected += chosen;
            shah += shahFor(size, chosen, site.expectedMicroUsd(size, needsAnalysis, !analyze));
        }
        if (analyze) {
            shah = analysisShah(shah);
        }
        int reserve = 0;
        if (personal) {
            // What the models should cost, in whole шаги at the people's price, and a margin on top.
            settings = settings.paidBy(ledger.microUsdPerShah());
            shah = Math.max(1, ledger.shahOf(expected));
            reserve = Math.max(shah, ledger.shahOf(Math.round(expected * RESERVE_MARGIN)));
        }
        Quote quote = new Quote(analyze ? "analyze" : "translate", from, to, numbers.size(), to - from + 1 - numbers.size(), shah,
                settings.usd(shah), Settings.usdOfMicro(expected), estimated, unanalyzed,
                settings.analyze(), settings.translate(), settings.proofread(), reserve);
        return new Prepared(quote, numbers, settings);
    }

    /**
     * A шаг pays for 10 000 characters at the site's models. Any model may be chosen: one that
     * costs more takes proportionally more шаги, a cheaper one never takes fewer.
     */
    static int shahFor(int chars, long chosenMicroUsd, long siteMicroUsd) {
        int base = Settings.shah(chars);
        if (siteMicroUsd <= 0 || chosenMicroUsd <= siteMicroUsd) {
            return base;
        }
        return (int) Math.ceil(base * (double) chosenMicroUsd / siteMicroUsd);
    }

    /** The site's models with this run's choices, each priced from OpenRouter's catalogue. */
    private Settings withModels(Settings base, Models models) {
        if (models == null) {
            return base;
        }
        Settings.Stage proofread = stage(base.proofread(), models.proofread());
        if (models.proofreadEnabled() != null) {
            proofread = new Settings.Stage(proofread.model(), proofread.inputPerMillion(), proofread.outputPerMillion(), models.proofreadEnabled());
        }
        return base.withModels(stage(base.analyze(), models.analyze()), stage(base.translate(), models.translate()), proofread);
    }

    private Settings.Stage stage(Settings.Stage base, String model) {
        if (model == null || model.isBlank() || model.strip().equals(base.model())) {
            return base;
        }
        String id = model.strip();
        List<AiModel> catalogue = ai.models();
        if (catalogue.isEmpty()) {
            throw UserFacingException.badGateway("Не вдалося отримати список моделей OpenRouter. Спробуйте пізніше.");
        }
        AiModel found = catalogue.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> UserFacingException.badRequest("Моделі «%s» на OpenRouter немає.".formatted(id)));
        if (!found.outputs().contains("text")) {
            throw UserFacingException.badRequest("Модель «%s» не пише текст.".formatted(id));
        }
        if (ModelRatings.of(found.id()) == ModelRatings.Rating.AWFUL) {
            throw UserFacingException.badRequest("Модель «%s» не підходить для перекладу. Оберіть іншу.".formatted(id));
        }
        if (!found.accepts("structured_outputs")) {
            throw UserFacingException.badRequest("Модель «%s» не вміє відповідати в потрібному форматі. Оберіть іншу.".formatted(id));
        }
        return new Settings.Stage(found.id(), found.inputPerMillion(), found.outputPerMillion(), base.enabled());
    }

    // ---- balance ------------------------------------------------------------------------------

    record Balance(Integer shah, BigDecimal usd) {
    }

    Optional<Balance> balance() {
        Optional<AiCredits> credits = ai.credits();
        long perShah = settings().microUsdPerShah();
        return credits.map(left -> new Balance(
                left.left().movePointRight(6).divide(BigDecimal.valueOf(perShah), 0, java.math.RoundingMode.FLOOR).intValue(),
                left.left().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Transactional
    long start(long editionId, Plan plan, long ownerId, boolean personal) {
        db.execute("SELECT 1 FROM edition WHERE id = ? FOR UPDATE", editionId);
        if (db.fetchExists(JOB, JOB.EDITION_ID.eq(editionId).and(JOB.STATE.in("queued", "running", "failed")))) {
            throw UserFacingException.conflict("Для цієї новели вже є незавершений переклад. Продовжте або скасуйте його.");
        }
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        Prepared prepared = prepare(editionId, plan, personal);
        Quote quote = prepared.quote();
        Settings settings = plan.again() ? prepared.settings().withRedo(System.nanoTime()) : prepared.settings();
        Long hold = null;
        if (personal) {
            String title = db.select(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE)).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                    .where(EDITION.ID.eq(editionId)).fetchSingle().value1();
            String chapters = quote.from() == quote.to() ? "глава " + quote.from() : "глави %d–%d".formatted(quote.from(), quote.to());
            hold = ledger.hold(ownerId, quote.reserveShah(), "%s «%s», %s".formatted(plan.analyze() ? "Аналіз" : "Автопереклад", title, chapters));
        }
        long jobId = db.insertInto(JOB)
                .set(JOB.EDITION_ID, editionId)
                .set(JOB.KIND, quote.kind())
                .set(JOB.REQUESTED_BY, ownerId)
                .set(JOB.FIRST_NUMBER, quote.from())
                .set(JOB.LAST_NUMBER, quote.to())
                .set(JOB.FUNDING, personal ? "account" : "site")
                .set(JOB.QUOTE_SHAH, personal ? quote.reserveShah() : quote.shah())
                .set(JOB.HOLD_TX_ID, hold)
                .set(JOB.SETTINGS, JSONB.valueOf(json.writeValueAsString(settings)))
                .returning(JOB.ID).fetchSingle().getId();
        for (int number : prepared.numbers()) {
            db.insertInto(JOB_STEP).set(JOB_STEP.JOB_ID, jobId).set(JOB_STEP.CHAPTER_NUMBER, number).execute();
        }
        return jobId;
    }

    @Transactional
    void cancel(long editionId, long jobId) {
        int changed = db.update(JOB).set(JOB.STATE, "cancelled").set(JOB.FINISHED_AT, DSL.currentOffsetDateTime())
                .where(JOB.ID.eq(jobId), JOB.EDITION_ID.eq(editionId), JOB.STATE.in("queued", "running", "failed")).execute();
        if (changed == 0) {
            throw UserFacingException.conflict("Цей переклад уже завершено.");
        }
        db.update(JOB_STEP).set(JOB_STEP.STATE, "cancelled")
                .where(JOB_STEP.JOB_ID.eq(jobId), JOB_STEP.STATE.in("pending", "failed")).execute();
        settle(jobId);
    }

    /**
     * A person's run is over (done or cancelled): what it really cost is charged in whole шаги,
     * the rest of the hold returns. Safe to call twice.
     */
    void settle(long jobId) {
        JobRecord job = db.selectFrom(JOB).where(JOB.ID.eq(jobId)).fetchSingle();
        if (!"account".equals(job.getFunding()) || job.getHoldTxId() == null) {
            return;
        }
        int charged = ledger.settle(job.getHoldTxId(), ai.spentMicroUsd(jobId));
        db.update(JOB).set(JOB.CHARGED_SHAH, charged).where(JOB.ID.eq(jobId)).execute();
    }

    /** After a failure: the failed chapter goes back to the queue; lost answers may be asked again. */
    @Transactional
    void resume(long editionId, long jobId) {
        int changed = db.update(JOB).set(JOB.STATE, "running").set(JOB.ERROR, (String) null).set(JOB.FINISHED_AT, (OffsetDateTime) null)
                .where(JOB.ID.eq(jobId), JOB.EDITION_ID.eq(editionId), JOB.STATE.eq("failed")).execute();
        if (changed == 0) {
            throw UserFacingException.conflict("Продовжити можна лише переклад, що зупинився з помилкою.");
        }
        db.update(JOB_STEP).set(JOB_STEP.STATE, "pending").set(JOB_STEP.ATTEMPTS, 0).set(JOB_STEP.NOT_BEFORE, DSL.currentOffsetDateTime())
                .where(JOB_STEP.JOB_ID.eq(jobId), JOB_STEP.STATE.eq("failed")).execute();
        ai.forgetUncertain(jobId);
    }

    // ---- what the owner sees -------------------------------------------------------------------

    record StepView(int number, String stage, String state, String error) {
    }

    /**
     * @param personal   paid from the person's шаги: {@code quoteShah} is then what the run holds
     * @param chargedShah what a finished person's run was charged
     */
    record JobView(long id, String kind, String state, int from, int to, int done, int quoteShah, BigDecimal spentUsd, int spentShah,
            StepView current, String error, OffsetDateTime createdAt, OffsetDateTime finishedAt, boolean personal, int chargedShah) {
    }

    List<JobView> jobs(long editionId) {
        List<JobView> out = new ArrayList<>();
        for (JobRecord job : db.selectFrom(JOB).where(JOB.EDITION_ID.eq(editionId)).orderBy(JOB.ID.desc()).limit(10).fetch()) {
            out.add(view(job));
        }
        return out;
    }

    JobView view(JobRecord job) {
        int done = db.fetchCount(JOB_STEP, JOB_STEP.JOB_ID.eq(job.getId()).and(JOB_STEP.STATE.eq("done")));
        StepView current = db.selectFrom(JOB_STEP)
                .where(JOB_STEP.JOB_ID.eq(job.getId()), JOB_STEP.STATE.ne("done"))
                .orderBy(JOB_STEP.CHAPTER_NUMBER).limit(1)
                .fetchOptional(step -> new StepView(step.getChapterNumber(), step.getStage(), step.getState(), step.getError()))
                .orElse(null);
        long spent = ai.spentMicroUsd(job.getId());
        Settings settings = json.readValue(job.getSettings().data(), Settings.class);
        int spentShah = (int) Math.ceil((double) spent / settings.microUsdPerShah());
        return new JobView(job.getId(), job.getKind(), job.getState(), job.getFirstNumber(), job.getLastNumber(), done, job.getQuoteShah(),
                Settings.usdOfMicro(spent), spentShah, current, job.getError(), job.getCreatedAt(), job.getFinishedAt(),
                "account".equals(job.getFunding()), job.getChargedShah());
    }

    Optional<JobRecord> job(long editionId, long jobId) {
        return db.selectFrom(JOB).where(JOB.ID.eq(jobId), JOB.EDITION_ID.eq(editionId)).fetchOptional();
    }

    // ---- cost report --------------------------------------------------------------------------

    record CostRow(String model, int chapters, BigDecimal minChapter, BigDecimal avgChapter, BigDecimal maxChapter,
            BigDecimal minPerShah, BigDecimal avgPerShah, BigDecimal maxPerShah, BigDecimal total) {
    }

    /**
     * What a finished chapter really cost, and what 10 000 characters of the original cost
     * (рішення 22: the price of a шаг is set from this), by translation model.
     */
    List<CostRow> report(int days) {
        var cost = DSL.sum(DSL.coalesce(AI_CALL.COST_ACTUAL_MUSD, AI_CALL.COST_ESTIMATED_MUSD)).cast(Long.class).as("cost");
        var model = DSL.field("{0} -> 'translate' ->> 'model'", String.class, JOB.SETTINGS).as("model");
        var perChapter = db.select(model, JOB_STEP.CHAPTER_NUMBER, JOB.ID, SOURCE_CHAPTER.CHARS, cost)
                .from(JOB_STEP)
                .join(JOB).on(JOB.ID.eq(JOB_STEP.JOB_ID))
                .join(EDITION).on(EDITION.ID.eq(JOB.EDITION_ID))
                .join(SOURCE_CHAPTER).on(SOURCE_CHAPTER.NOVEL_ID.eq(EDITION.NOVEL_ID), SOURCE_CHAPTER.NUMBER.eq(JOB_STEP.CHAPTER_NUMBER))
                .join(AI_CALL).on(AI_CALL.JOB_ID.eq(JOB.ID), AI_CALL.CHAPTER_NUMBER.eq(JOB_STEP.CHAPTER_NUMBER))
                .where(JOB_STEP.STAGE.eq("done"), JOB_STEP.UPDATED_AT.gt(OffsetDateTime.now().minusDays(days)))
                .groupBy(model, JOB_STEP.CHAPTER_NUMBER, JOB.ID, SOURCE_CHAPTER.CHARS)
                .fetch();
        Map<String, List<long[]>> byModel = new java.util.TreeMap<>();
        for (Record row : perChapter) {
            byModel.computeIfAbsent(row.get(model), key -> new ArrayList<>())
                    .add(new long[] {row.get(cost), row.get(SOURCE_CHAPTER.CHARS)});
        }
        List<CostRow> rows = new ArrayList<>();
        byModel.forEach((name, chapters) -> {
            double[] chapter = chapters.stream().mapToDouble(pair -> pair[0]).toArray();
            double[] perShah = chapters.stream().mapToDouble(pair -> pair[0] * 10_000.0 / Math.max(1, pair[1])).toArray();
            rows.add(new CostRow(name, chapters.size(), usd(min(chapter)), usd(avg(chapter)), usd(max(chapter)),
                    usd(min(perShah)), usd(avg(perShah)), usd(max(perShah)), usd(java.util.Arrays.stream(chapter).sum())));
        });
        return rows;
    }

    private static double min(double[] values) {
        return java.util.Arrays.stream(values).min().orElse(0);
    }

    private static double max(double[] values) {
        return java.util.Arrays.stream(values).max().orElse(0);
    }

    private static double avg(double[] values) {
        return java.util.Arrays.stream(values).average().orElse(0);
    }

    private static BigDecimal usd(double micro) {
        return Settings.usdOfMicro(Math.round(micro));
    }

    /** Average length of the novel's known chapters, for prices per chapter. */
    int averageChars(long editionId) {
        Double average = db.select(DSL.avg(SOURCE_CHAPTER.CHARS).cast(Double.class)).from(SOURCE_CHAPTER)
                .join(EDITION).on(EDITION.NOVEL_ID.eq(SOURCE_CHAPTER.NOVEL_ID)).where(EDITION.ID.eq(editionId)).fetchOne(0, Double.class);
        return average == null ? UNKNOWN_CHAPTER_CHARS : (int) Math.round(average);
    }

    record Process(long editionId, String title, String slug, JobView job) {
    }

    /** Every job the owner started, newest first: the «Процеси» page. */
    List<Process> processes(long ownerId, int page) {
        int at = Math.max(1, page);
        List<Process> out = new ArrayList<>();
        for (Record row : db.select(JOB.asterisk(), DSL.coalesce(EDITION.TITLE, NOVEL.TITLE).as("title"), NOVEL.SLUG)
                .from(JOB).join(EDITION).on(EDITION.ID.eq(JOB.EDITION_ID)).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(JOB.REQUESTED_BY.eq(ownerId))
                .orderBy(DSL.when(JOB.STATE.in("queued", "running", "failed"), 0).otherwise(1), JOB.ID.desc())
                .limit(30).offset((at - 1) * 30).fetch()) {
            JobRecord job = row.into(JOB);
            out.add(new Process(job.getEditionId(), row.get("title", String.class), row.get(NOVEL.SLUG), view(job)));
        }
        return out;
    }
}
