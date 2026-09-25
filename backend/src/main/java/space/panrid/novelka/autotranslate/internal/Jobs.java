package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_STEP;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.SOURCE_CHAPTER;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiCredits;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.platform.SiteSettings;
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

    Jobs(DSLContext db, Ai ai, SiteSettings siteSettings, JsonMapper json) {
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
                settings.proofread(), settings.segmentChars(), settings.microUsdPerShah(), settings.capFactor());
        siteSettings.put(SETTINGS_KEY, json.writeValueAsString(fixed), ownerId);
    }

    // ---- quote --------------------------------------------------------------------------------

    record Novel(long novelId, int sourceChapters, int nextNumber, int publishedChapters) {
    }

    Novel novel(long editionId) {
        Record row = db.select(NOVEL.ID, NOVEL.SOURCE, NOVEL.SOURCE_CHAPTER_COUNT, EDITION.FIRST_NUMBER)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої новели немає."));
        if (!"syosetu".equals(row.get(NOVEL.SOURCE))) {
            throw UserFacingException.badRequest("Автопереклад доступний лише для новел із Syosetu.");
        }
        int last = db.select(DSL.coalesce(DSL.max(CHAPTER.NUMBER), 0)).from(CHAPTER)
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.PUBLISHED_REVISION_ID.isNotNull()).fetchOne(0, Integer.class);
        int published = db.fetchCount(CHAPTER, CHAPTER.EDITION_ID.eq(editionId).and(CHAPTER.PUBLISHED_REVISION_ID.isNotNull()));
        Integer count = row.get(NOVEL.SOURCE_CHAPTER_COUNT);
        return new Novel(row.get(NOVEL.ID), count == null ? 0 : count, Math.max(last + 1, row.get(EDITION.FIRST_NUMBER)), published);
    }

    record Quote(int from, int to, int chapters, int shah, BigDecimal usd, boolean estimated) {
    }

    Quote quote(long editionId, int to) {
        Novel novel = novel(editionId);
        int from = novel.nextNumber();
        if (to < from) {
            throw UserFacingException.badRequest(from == 1 ? "Вкажіть номер глави." : "Глави до %d уже перекладено.".formatted(from - 1));
        }
        if (to > novel.sourceChapters()) {
            throw UserFacingException.badRequest("В оригіналі поки %d глав.".formatted(novel.sourceChapters()));
        }
        Map<Integer, Integer> known = db.select(SOURCE_CHAPTER.NUMBER, SOURCE_CHAPTER.CHARS).from(SOURCE_CHAPTER)
                .where(SOURCE_CHAPTER.NOVEL_ID.eq(novel.novelId())).fetchMap(SOURCE_CHAPTER.NUMBER, SOURCE_CHAPTER.CHARS);
        int guess = known.isEmpty() ? UNKNOWN_CHAPTER_CHARS
                : (int) Math.round(known.values().stream().mapToInt(Integer::intValue).average().orElse(UNKNOWN_CHAPTER_CHARS));
        int shah = 0;
        boolean estimated = false;
        for (int number = from; number <= to; number++) {
            Integer chars = known.get(number);
            estimated |= chars == null;
            shah += Settings.shah(chars == null ? guess : chars);
        }
        return new Quote(from, to, to - from + 1, shah, settings().usd(shah), estimated);
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
    long start(long editionId, int to, long ownerId) {
        db.execute("SELECT 1 FROM edition WHERE id = ? FOR UPDATE", editionId);
        if (db.fetchExists(JOB, JOB.EDITION_ID.eq(editionId).and(JOB.STATE.in("queued", "running", "failed")))) {
            throw UserFacingException.conflict("Для цієї новели вже є незавершений переклад. Продовжте або скасуйте його.");
        }
        if (!ai.configured()) {
            throw UserFacingException.badRequest("Ключ OpenRouter не налаштовано на сервері.");
        }
        Quote quote = quote(editionId, to);
        long jobId = db.insertInto(JOB)
                .set(JOB.EDITION_ID, editionId)
                .set(JOB.REQUESTED_BY, ownerId)
                .set(JOB.FIRST_NUMBER, quote.from())
                .set(JOB.LAST_NUMBER, quote.to())
                .set(JOB.FUNDING, "site")
                .set(JOB.QUOTE_SHAH, quote.shah())
                .set(JOB.SETTINGS, JSONB.valueOf(json.writeValueAsString(settings())))
                .returning(JOB.ID).fetchSingle().getId();
        for (int number = quote.from(); number <= quote.to(); number++) {
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

    record JobView(long id, String state, int from, int to, int done, int quoteShah, BigDecimal spentUsd, int spentShah,
            StepView current, String error, OffsetDateTime createdAt, OffsetDateTime finishedAt) {
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
        return new JobView(job.getId(), job.getState(), job.getFirstNumber(), job.getLastNumber(), done, job.getQuoteShah(),
                Settings.usdOfMicro(spent), spentShah, current, job.getError(), job.getCreatedAt(), job.getFinishedAt());
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
}
