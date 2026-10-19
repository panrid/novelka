package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.Sources;
import space.panrid.novelka.team.Teams;

/**
 * The site owner runs autotranslation at the site's cost; everyone else who sees AI runs it from the
 * originals they pasted, and those the owner let in — from the original's site too, paying from
 * their шаги at the site's models (рішення 29, етап 19). Models, prices and the wallet stay the owner's.
 */
@RestController
@RequestMapping("/api/studio")
class AutotranslateController {

    private final AccessPolicy access;
    private final Preparation preparation;
    private final Jobs jobs;
    private final Glossary glossary;
    private final Ai ai;
    private final DSLContext db;
    private final Teams teams;
    private final Analyses analyses;
    private final Ledger ledger;
    private final Sources sources;
    private final space.panrid.novelka.suggestion.Suggestions suggestions;
    private final Agreement agreement;
    private final JobLog journal;
    private final Presets presets;
    private final ModelCosts costs;
    private final SourceWatch watch;
    private final StoryRoles storyRoles;
    private final Choices choices;
    private final SourceOf sourceOf;
    private final EditorAi editorAi;
    private final MySets mySets;
    private final LiveRun liveRun;
    private final space.panrid.novelka.platform.audit.AuditLog audit;

    AutotranslateController(AccessPolicy access, Preparation preparation, Jobs jobs, Glossary glossary, Ai ai, DSLContext db,
            Teams teams, Analyses analyses, Ledger ledger, Sources sources, space.panrid.novelka.suggestion.Suggestions suggestions,
            Agreement agreement, JobLog journal, Presets presets, ModelCosts costs, SourceWatch watch, StoryRoles storyRoles,
            Choices choices, space.panrid.novelka.platform.audit.AuditLog audit, SourceOf sourceOf, EditorAi editorAi, MySets mySets,
            LiveRun liveRun) {
        this.liveRun = liveRun;
        this.editorAi = editorAi;
        this.mySets = mySets;
        this.sourceOf = sourceOf;
        this.audit = audit;
        this.choices = choices;
        this.storyRoles = storyRoles;
        this.watch = watch;
        this.costs = costs;
        this.presets = presets;
        this.journal = journal;
        this.agreement = agreement;
        this.suggestions = suggestions;
        this.sources = sources;
        this.ledger = ledger;
        this.teams = teams;
        this.analyses = analyses;
        this.access = access;
        this.preparation = preparation;
        this.jobs = jobs;
        this.glossary = glossary;
        this.ai = ai;
        this.db = db;
    }

    private Viewer owner() {
        return access.requireSiteRole(SiteRole.OWNER);
    }

    /**
     * A translator of the edition who may run its translation. «Перекласти з ШІ» from the originals
     * the team pasted is for everyone who sees AI; translating from the original's site is the
     * owner's and of those the owner let in (етап 19): the site fetches nobody else's texts.
     */
    private Viewer ownerTranslating(long editionId) {
        access.requireSignedIn();
        Viewer viewer = jobs.novel(editionId).pasted() ? access.requireAi() : access.requireAutotranslate();
        access.requireTranslator(editionId);
        return viewer;
    }

    /** Watching the original is for novels taken from its site, not for pasted ones. */
    private void fromSite(long editionId) {
        if (jobs.novel(editionId).pasted()) {
            throw UserFacingException.badRequest("У цієї новели немає сайту оригіналу: її глави вставляє команда.");
        }
    }

    /** Everyone but the site owner pays with their own шаги. */
    private static boolean personal(Viewer viewer) {
        return viewer.role() != SiteRole.OWNER;
    }

    /** @param team handle of the team; empty means the personal one */
    record PrepareRequest(String url, String team) {
    }

    record Prepared(long editionId, String novelSlug) {
    }

    @PostMapping("/autotranslate/prepare")
    Prepared prepare(@RequestBody PrepareRequest body) {
        Viewer viewer = access.requireAutotranslate();
        if (personal(viewer) && ledger.balance(viewer.accountId()).available() < 1) {
            throw UserFacingException.badRequest("Автопереклад запускається за шаги, а у вас їх поки немає. Шаги нараховує власник сайту.");
        }
        SourceLink link = sources.link(body.url());
        long teamId = body.team() == null || body.team().isBlank()
                ? teams.personalTeam(viewer.accountId())
                : teams.findByHandle(body.team()).orElseThrow(() -> UserFacingException.notFound("Такої команди немає.")).id();
        access.requireTeamTranslator(teamId);
        EditionRef ref = preparation.prepare(link, teamId);
        return new Prepared(ref.editionId(), ref.novelSlug());
    }

    /**
     * @param personal runs are paid from the viewer's шаги: {@code balance} is theirs and
     *                 {@code reserved} is what their runs hold; otherwise the balance is OpenRouter's
     * @param presets  ready sets of models: all of them for the site owner, the qualities people pick from for others (етап 19)
     * @param pasted   translated from the originals the team pasted: {@code sourceChapters} is the last one pasted
     * @param remembered what the person picked with «Запам'ятати для наступних»; null — nothing yet
     * @param mySets     the person's own sets (етап 19, п. 8), priced for an average chapter of this novel
     * @param peopleModels the models a person may put into a step: those the site's qualities use
     */
    record Overview(boolean configured, boolean showShah, int sourceChapters, int nextNumber, int publishedChapters,
            int lastAnalyzed, int nextToAnalyze, int averageChars, Jobs.Balance balance, BigDecimal usdPerShah, Settings settings,
            List<Jobs.JobView> jobs, boolean personal, int reserved, List<PresetView> presets, boolean pasted,
            Choices.Choice remembered, List<MySetView> mySets, List<String> peopleModels) {
    }

    /** A person's own set as the menu shows it: {@code chapterUsd} an average chapter of this novel at it. */
    record MySetView(long id, String name, Long basedOn, String analyze, String translate, String proofread, BigDecimal chapterUsd) {
    }

    private List<MySetView> mySets(long accountId, int averageChars) {
        return mySets.of(accountId).stream().map(set -> new MySetView(set.id(), set.name(), set.basedOn(), set.analyze(), set.translate(),
                set.proofread(), Settings.usdOfMicro(safePrice(set, averageChars)))).toList();
    }

    /** A model OpenRouter no longer lists leaves the set unpriced rather than the page broken. */
    private long safePrice(MySets.MySet set, int chars) {
        try {
            return jobs.chapterMicroUsd(set.models(), chars);
        } catch (UserFacingException gone) {
            return 0;
        }
    }

    /**
     * A preset as the menu shows it: {@code analysisUsd} is its analysis of an average chapter of
     * this novel, {@code chapterUsd} the whole chapter (analysis, translation and proofreading).
     * {@code quality}: what people get it as («best», «high», «draft»), null — the owner's alone.
     */
    record PresetView(long id, String name, String summary, BigDecimal rating, String analyze, String translate, String proofread,
            BigDecimal analysisUsd, BigDecimal chapterUsd, String quality) {
    }

    private List<PresetView> presets(List<Presets.Preset> shown, int averageChars) {
        double thousands = averageChars / 1000.0;
        return shown.stream().map(preset -> new PresetView(preset.id(), preset.name(), preset.summary(), preset.rating(),
                preset.analyze(), preset.translate(), preset.proofread(),
                Settings.usdOfMicro(Math.round(thousands * preset.analyzeMicroUsdPerThousand())),
                Settings.usdOfMicro(Math.round(thousands * (preset.analyzeMicroUsdPerThousand() + preset.translateMicroUsdPerThousand()
                        + preset.proofreadMicroUsdPerThousand()))), preset.quality())).toList();
    }

    @GetMapping("/editions/{editionId}/autotranslate")
    Overview overview(@PathVariable long editionId) {
        Viewer viewer = ownerTranslating(editionId);
        Jobs.Novel novel = jobs.novel(editionId);
        Settings settings = jobs.settings();
        if (personal(viewer)) {
            Ledger.Balance mine = ledger.balance(viewer.accountId());
            long price = ledger.microUsdPerShah();
            int averageChars = jobs.averageChars(editionId);
            return new Overview(ai.configured(), true, novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                    novel.lastAnalyzed(), novel.nextToAnalyze(), averageChars,
                    new Jobs.Balance(mine.available(), Settings.usdOfMicro(mine.available() * price)), Settings.usdOfMicro(price),
                    settings.paidBy(price), jobs.jobs(editionId), true, mine.reserved(), presets(presets.forPeople(), averageChars),
                    novel.pasted(), choices.of(viewer.accountId()).orElse(null), mySets(viewer.accountId(), averageChars), mySets.peopleModels());
        }
        int averageChars = jobs.averageChars(editionId);
        return new Overview(ai.configured(), showShah(viewer), novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                novel.lastAnalyzed(), novel.nextToAnalyze(), averageChars,
                jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()), settings, jobs.jobs(editionId), false, 0,
                presets(presets.list(), averageChars), novel.pasted(), choices.of(viewer.accountId()).orElse(null),
                mySets(viewer.accountId(), averageChars), mySets.peopleModels());
    }

    /** The Studio's bell for the original: whether this person hears of its new chapters, and when it was last looked at. */
    record SourceWatchView(boolean watching, java.time.OffsetDateTime checkedAt, int sourceChapters, int found) {
    }

    record WatchChange(boolean watching) {
    }

    @GetMapping("/editions/{editionId}/source-watch")
    SourceWatchView sourceWatch(@PathVariable long editionId) {
        Viewer viewer = ownerTranslating(editionId);
        return new SourceWatchView(watch.watching(viewer.accountId(), editionId), watch.checkedAt(editionId),
                jobs.novel(editionId).sourceChapters(), 0);
    }

    @PutMapping("/editions/{editionId}/source-watch")
    SourceWatchView changeSourceWatch(@PathVariable long editionId, @RequestBody WatchChange body) {
        Viewer viewer = ownerTranslating(editionId);
        fromSite(editionId);
        watch.watch(viewer.accountId(), editionId, body.watching());
        return sourceWatch(editionId);
    }

    /** «Перевірити зараз»: the original's table of contents read at once; {@code found} — chapters it grew by. */
    @PostMapping("/editions/{editionId}/source-check")
    SourceWatchView checkSource(@PathVariable long editionId) {
        Viewer viewer = ownerTranslating(editionId);
        fromSite(editionId);
        SourceWatch.Checked checked;
        try {
            checked = watch.check(jobs.novel(editionId).novelId());
        } catch (UserFacingException known) {
            throw known;
        } catch (RuntimeException failed) {
            throw UserFacingException.badGateway("Сайт оригіналу не відповів. Спробуйте пізніше.");
        }
        return new SourceWatchView(watch.watching(viewer.accountId(), editionId), watch.checkedAt(editionId), checked.now(),
                checked.found());
    }

    @PostMapping("/editions/{editionId}/autotranslate/quote")
    Jobs.Quote quote(@PathVariable long editionId, @RequestBody Jobs.Plan plan) {
        Viewer viewer = ownerTranslating(editionId);
        return jobs.quote(editionId, plan, personal(viewer));
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    Jobs.JobView start(@PathVariable long editionId, @RequestBody Jobs.Plan plan) {
        Viewer viewer = ownerTranslating(editionId);
        long jobId = jobs.start(editionId, plan, viewer.accountId(), personal(viewer));
        return jobs.view(jobs.job(editionId, jobId).orElseThrow());
    }

    /** {@code chapters}: how many chapters the journal has, {@link JobLog#CHAPTERS} to a page. */
    record Journal(Jobs.JobView job, int chapters, int page, List<JobLog.Event> events) {
    }

    /** The run's journal (етап 17), a page of chapters: the last ones first, or from the first with {@code order=asc}. */
    @GetMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/log")
    Journal journal(@PathVariable long editionId, @PathVariable long jobId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "desc") String order) {
        ownerTranslating(editionId);
        var job = jobs.job(editionId, jobId).orElseThrow(() -> UserFacingException.notFound("Такого запуску немає."));
        var shown = journal.chapters(jobId, page, "asc".equals(order));
        return new Journal(jobs.view(job), shown.chapters(), Math.max(1, page), shown.events());
    }

    /**
     * «Хід перекладу» наживо (етап 20, рішення 17): one chapter of the run as it is now — its original
     * paragraph by paragraph with the translation saved so far, the glossary words it names and what
     * proofreading changed. Without {@code chapter}, the chapter the run works on now.
     */
    @GetMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/live")
    LiveRun.View live(@PathVariable long editionId, @PathVariable long jobId, @RequestParam(required = false) Integer chapter) {
        ownerTranslating(editionId);
        var job = jobs.job(editionId, jobId).orElseThrow(() -> UserFacingException.notFound("Такого запуску немає."));
        return liveRun.view(job, chapter);
    }

    /**
     * The team cancels its runs; whoever started a run may cancel it even after leaving the team,
     * so шаги it holds are never stuck; the site owner may cancel any run.
     */
    @PostMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/cancel")
    void cancel(@PathVariable long editionId, @PathVariable long jobId) {
        Viewer viewer = access.requireSignedIn();
        boolean started = jobs.job(editionId, jobId).map(job -> job.getRequestedBy() == viewer.accountId()).orElse(false);
        if (!started && viewer.role() != SiteRole.OWNER) {
            access.requireTranslator(editionId);
        }
        jobs.cancel(editionId, jobId);
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/resume")
    void resume(@PathVariable long editionId, @PathVariable long jobId) {
        ownerTranslating(editionId);
        jobs.resume(editionId, jobId);
    }

    /**
     * «Запам'ятати для наступних» (етап 19): the person's set of models, steps and «Одразу
     * опублікувати», offered first in their next runs. People keep only a set the site gives them.
     */
    @PutMapping("/autotranslate/choice")
    void remember(@RequestBody Choices.Choice body) {
        Viewer viewer = access.requireAi();
        if (body.preset() != null) {
            Presets.Preset preset = presets.find(body.preset()).orElseThrow(() -> UserFacingException.notFound("Такого набору моделей немає."));
            if (personal(viewer) && preset.quality() == null) {
                throw UserFacingException.badRequest("Цей набір моделей сайт людям не пропонує.");
            }
        }
        if (body.mySet() != null && mySets.find(viewer.accountId(), body.mySet()).isEmpty()) {
            throw UserFacingException.notFound("Такого набору немає.");
        }
        choices.remember(viewer.accountId(), body);
    }

    /** @param basedOn the site's set it was changed from, or null */
    record NewSet(String name, Long basedOn, Jobs.Models models) {
    }

    /** «Мої набори» (етап 19, п. 8): the person's own sets of models. */
    @GetMapping("/autotranslate/my-sets")
    List<MySetView> mySets() {
        return mySets(access.requireAi().accountId(), 6_000);
    }

    /** «Зберегти як мій набір»: a quality with some steps' models changed, under a name. */
    @PostMapping("/autotranslate/my-sets")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> saveSet(@RequestBody NewSet body) {
        Viewer viewer = access.requireAi();
        return Map.of("id", mySets.create(viewer.accountId(), body.name(), body.basedOn(), body.models()));
    }

    record Rename(String name) {
    }

    @PutMapping("/autotranslate/my-sets/{id}")
    void renameSet(@PathVariable long id, @RequestBody Rename body) {
        mySets.rename(access.requireSignedIn().accountId(), id, body.name());
    }

    @DeleteMapping("/autotranslate/my-sets/{id}")
    void deleteSet(@PathVariable long id) {
        mySets.delete(access.requireSignedIn().accountId(), id);
    }

    /** A ready set of models and the quality people get it as (етап 19); null — the owner's alone. */
    record PresetQuality(long id, String name, String summary, String translate, String quality) {
    }

    /** «Якість для людей» in «Налаштування сайту»: every set and which quality it is. */
    @GetMapping("/autotranslate/qualities")
    List<PresetQuality> qualities() {
        owner();
        return presets.list().stream()
                .map(preset -> new PresetQuality(preset.id(), preset.name(), preset.summary(), preset.translate(), preset.quality())).toList();
    }

    /** The site owner gives each quality («best», «high», «draft») its set; null leaves it out. */
    @PutMapping("/autotranslate/qualities")
    List<PresetQuality> changeQualities(@RequestBody Map<String, Long> body) {
        Viewer viewer = owner();
        presets.setQualities(body);
        audit.record(viewer.accountId(), "autotranslate_qualities", "site", null, Map.of("qualities", body));
        return qualities();
    }

    /** Every run the viewer started, unfinished ones first; by state and kind, by words of the title. */
    @GetMapping("/autotranslate/processes")
    Jobs.Processes processes(@RequestParam(defaultValue = "") String state, @RequestParam(defaultValue = "") String kind,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page) {
        return jobs.processes(access.requireSignedIn().accountId(), state, kind, q, page);
    }

    /**
     * {@code chapterUsd}: a chapter (or its stage) for text models, one picture for models that draw.
     * {@code rating}: recommended, usual or weak (see {@link ModelRatings}).
     */
    /** {@code measuredChapters}: the price is what the model really cost on that many chapters here; null — an estimate from token prices. */
    record ModelChoice(String id, String name, double inputPerMillion, double outputPerMillion, BigDecimal chapterUsd, String rating,
            Integer measuredChapters) {
    }

    private static final List<String> STAGES = List.of("analyze", "translate", "proofread");

    /**
     * OpenRouter models whose id or name has all the words typed, recommended ones first, with
     * what a chapter costs: for one stage when {@code stage} is given, else for all three.
     * {@code show}: «recommended» only, «usual» (without weak ones) or «weak» (everything that can
     * translate). Models that cannot translate are never listed.
     */
    @GetMapping("/autotranslate/models")
    List<ModelChoice> models(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "6000") int chars,
            @RequestParam(defaultValue = "text") String output, @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "usual") String show) {
        owner();
        List<String> words = java.util.Arrays.stream(q.strip().toLowerCase(java.util.Locale.ROOT).split("[\\s/:-]+"))
                .filter(word -> !word.isEmpty()).toList();
        int size = Math.max(500, chars);
        int at = stage == null ? -1 : STAGES.indexOf(stage);
        boolean text = !output.equals("image");
        return ai.models().stream()
                .filter(model -> model.outputs().contains(output) && !model.id().startsWith("openrouter/") && !model.id().endsWith(":batch"))
                // Models that draw write text too, but they are the illustrator's choice, not the translator's.
                .filter(model -> !text || !model.outputs().contains("image"))
                // Analysis and translation need answers in a fixed JSON shape; without it a model cannot work here.
                .filter(model -> !text || model.accepts("structured_outputs"))
                .filter(model -> {
                    String haystack = (model.id() + " " + model.name()).toLowerCase(java.util.Locale.ROOT);
                    return words.stream().allMatch(haystack::contains);
                })
                .filter(model -> !text || shown(ModelRatings.of(model.id()), show))
                .sorted(java.util.Comparator.comparing((space.panrid.novelka.ai.AiModel model) ->
                        text && ModelRatings.of(model.id()) == ModelRatings.Rating.RECOMMENDED ? 0 : 1))
                .map(model -> {
                    Settings all = Settings.defaults().withModels(stageOf(model), stageOf(model), stageOf(model));
                    long micro = !text ? pictureMicroUsd(model) : at >= 0
                            ? costs.chapterMicroUsd(at, size, stageOf(model))
                            : costs.chapterMicroUsd(all, size, true, true, true);
                    Integer measured = !text || at < 0 ? null
                            : costs.of(model.id(), ModelCosts.STAGES.get(at)).map(ModelCosts.Measured::chapters).orElse(null);
                    String rating = text ? ModelRatings.of(model.id()).name().toLowerCase(java.util.Locale.ROOT) : "usual";
                    return new ModelChoice(model.id(), model.name(), model.inputPerMillion(), model.outputPerMillion(),
                            Settings.usdOfMicro(micro), rating, measured);
                })
                .toList();
    }

    /**
     * A picture: about 1 290 image tokens (what Gemini draws; our first real picture cost $0.0387
     * at $30 per million) plus a short description to read.
     */
    private static long pictureMicroUsd(space.panrid.novelka.ai.AiModel model) {
        return Math.round(1290 * model.imageOutputPerMillion() + 300 * model.inputPerMillion());
    }

    private static boolean shown(ModelRatings.Rating rating, String show) {
        return switch (rating) {
            case RECOMMENDED -> true;
            case USUAL -> !show.equals("recommended");
            case WEAK -> show.equals("weak");
            case AWFUL -> false;
        };
    }

    private static Settings.Stage stageOf(space.panrid.novelka.ai.AiModel model) {
        return new Settings.Stage(model.id(), model.inputPerMillion(), model.outputPerMillion(), true);
    }

    // ---- chapter titles and numbers from analysis, checked before translating ---------------

    record AnalysisItem(int number, String title, String label, boolean edited, boolean translated) {
    }

    record AnalysisPage(List<AnalysisItem> items, int total, int page, boolean hasMore) {
    }

    @GetMapping("/editions/{editionId}/analysis")
    AnalysisPage analysis(@PathVariable long editionId, @RequestParam(defaultValue = "1") int page) {
        access.requireTextEditor(editionId);
        int next = jobs.novel(editionId).nextNumber();
        Analyses.Page found = analyses.page(editionId, page);
        return new AnalysisPage(found.items().stream()
                .map(done -> new AnalysisItem(done.number(), done.title(), done.label(), done.edited(), done.number() < next)).toList(),
                found.total(), found.page(), found.hasMore());
    }

    record AnalysisChange(String title, String label) {
    }

    @PutMapping("/editions/{editionId}/analysis/{number}")
    void editAnalysis(@PathVariable long editionId, @PathVariable int number, @RequestBody AnalysisChange body) {
        access.requireTranslator(editionId);
        analyses.edit(editionId, number, body.title(), body.label());
    }

    // ---- glossary: the team reviews it; the original only behind «Оригінал» (рішення 30) --------

    /** {@code facts}: how many facts of its «хто це» card the novel has told so far. */
    /**
     * @param original    the form in the original's current language, null until analysis names it there
     * @param portraitUrl the portrait for «Хто це», picked from «Мої картинки»
     */
    record GlossaryItem(long id, String ukrainian, String kind, String gender, String note, Integer chapter, boolean manual,
            String status, int facts, boolean looseForms, Glossary.Name main, List<Glossary.Name> others, Boolean inStory,
            String original, String portraitUrl) {
    }

    record GlossaryPage(List<GlossaryItem> items, int total, int page, boolean hasMore, List<Integer> chapters,
            java.util.Map<Integer, String> labels, java.util.Map<String, Integer> counts) {
    }

    @GetMapping("/editions/{editionId}/glossary")
    GlossaryPage glossary(@PathVariable long editionId, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer chapter, @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "alpha") String sort, @RequestParam(defaultValue = "1") int page) {
        access.requireTextEditor(editionId);
        Glossary.Page found = glossary.page(editionId, status != null && Glossary.STATUSES.contains(status) ? status : null, chapter, q,
                kind, sort, page);
        List<Long> ids = found.items().stream().map(Glossary.Entry::id).toList();
        java.util.Map<Long, Integer> facts = glossary.factCounts(ids);
        java.util.Map<Long, Glossary.Group> groups = glossary.groupsOf(ids);
        java.util.Map<Long, String> portraits = glossary.portraits(ids);
        return new GlossaryPage(found.items().stream()
                .map(entry -> new GlossaryItem(entry.id(), entry.ukrainian(), entry.kind(), entry.gender(), entry.note(),
                        entry.sourceChapter(), entry.manual(), entry.status(), facts.getOrDefault(entry.id(), 0), entry.looseForms(),
                        groups.get(entry.id()).main(), groups.get(entry.id()).others(), entry.inStory(), entry.original(),
                        portraits.get(entry.id())))
                .toList(), found.total(), found.page(), found.hasMore(), found.chapters(), found.labels(), found.counts());
    }

    record StatusChange(List<Long> ids, String status) {
    }

    /** «Затвердити», «Відхилити» or «Повернути в нові» for the selected entries. */
    @PostMapping("/editions/{editionId}/glossary/status")
    java.util.Map<String, Integer> setStatus(@PathVariable long editionId, @RequestBody StatusChange body) {
        access.requireTranslator(editionId);
        return java.util.Map.of("changed", glossary.setStatus(editionId, body.ids(), body.status()));
    }

    /** «Погодити нові»: every entry nobody checked yet becomes approved at once. */
    @PostMapping("/editions/{editionId}/glossary/approve-new")
    java.util.Map<String, Integer> approveNew(@PathVariable long editionId) {
        access.requireTranslator(editionId);
        return java.util.Map.of("changed", glossary.approveNew(editionId));
    }

    record PortraitRequest(Long imageId) {
    }

    /** The portrait of an entry for «Хто це», from the person's «Мої картинки»; {@code imageId} null takes it away. */
    @PutMapping("/editions/{editionId}/glossary/{entryId}/portrait")
    void portrait(@PathVariable long editionId, @PathVariable long entryId, @RequestBody PortraitRequest body) {
        EditionAccess who = access.requireTranslator(editionId);
        glossary.setPortrait(editionId, entryId, who.viewer().accountId(), body.imageId());
    }

    /** @param looseForms null keeps it as it was */
    record GlossaryChange(String ukrainian, String kind, String gender, String note, Boolean looseForms) {
    }

    @PutMapping("/editions/{editionId}/glossary/{entryId}")
    void updateEntry(@PathVariable long editionId, @PathVariable long entryId, @RequestBody GlossaryChange body) {
        access.requireTranslator(editionId);
        glossary.update(editionId, entryId, body.ukrainian(), body.kind(), body.gender(), body.note(), body.looseForms());
    }

    /** The original behind an entry, for the team only (рішення 30). */
    @GetMapping("/editions/{editionId}/glossary/{entryId}/original")
    Glossary.Original original(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTextEditor(editionId);
        return glossary.original(editionId, entryId);
    }

    /** Every chapter where the entry's Ukrainian form is used, in any case (етап 17). */
    @GetMapping("/editions/{editionId}/glossary/{entryId}/occurrences")
    Glossary.Occurrences occurrences(@PathVariable long editionId, @PathVariable long entryId,
            @RequestParam(required = false) String form) {
        access.requireTextEditor(editionId);
        Glossary.Occurrences current = glossary.occurrences(editionId, entryId);
        // Another form (the one before an edit) is looked up the same way, to say what changing it touches.
        return form == null || form.isBlank() ? current : glossary.occurrencesOf(editionId, form.strip(), glossary.looseForms(entryId));
    }

    /**
     * @param from  the Ukrainian form the text has now
     * @param apply true: change the chapters at once; otherwise the changes go to the team as suggestions
     */
    record Rewrite(String from, boolean apply, Boolean ai) {
    }

    record Rewritten(int paragraphs, int chapters) {
    }

    /** «Оновити в перекладених главах»: the entry's old form turned into its new one everywhere. */
    @PostMapping("/editions/{editionId}/glossary/{entryId}/rewrite")
    Rewritten rewrite(@PathVariable long editionId, @PathVariable long entryId, @RequestBody Rewrite body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        if (Boolean.TRUE.equals(body.ai())) {
            access.requireAi();
        }
        String to = glossary.occurrences(editionId, entryId).form();
        Map<String, String> originals = new java.util.HashMap<>();
        List<space.panrid.novelka.suggestion.Suggestions.BlockChange> changes = glossary.rewrite(editionId, body.from(), to, glossary.looseForms(entryId), originals);
        if (Boolean.TRUE.equals(body.ai()) && !changes.isEmpty()) {
            // The site owner's runs are the site's; anyone else pays from their шаги (рішення 29).
            Long payer = personal(viewer) ? viewer.accountId() : null;
            if (payer != null && ledger.balance(payer).available() < 1) {
                throw UserFacingException.badRequest("Вичитка ШІ — за шаги, а у вас їх поки немає.");
            }
            changes = agreement.fix(changes, originals, body.from(), to, payer);
        }
        int paragraphs = suggestions.propose(viewer, editionId, changes, "Словник: «%s» → «%s»".formatted(body.from(), to), body.apply());
        return new Rewritten(paragraphs, (int) changes.stream().mapToInt(space.panrid.novelka.suggestion.Suggestions.BlockChange::chapterNumber).distinct().count());
    }

    record Regender(boolean apply) {
    }

    /**
     * The entry's gender changed: a model makes the words about the character agree in every
     * chapter that names them; the rest is for people, from the list of places.
     */
    @PostMapping("/editions/{editionId}/glossary/{entryId}/regender")
    Rewritten regender(@PathVariable long editionId, @PathVariable long entryId, @RequestBody Regender body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        access.requireAi();
        Glossary.Entry entry = glossary.all(editionId).stream().filter(e -> e.id() == entryId).findFirst()
                .orElseThrow(() -> UserFacingException.notFound("Такого запису немає."));
        Long payer = personal(viewer) ? viewer.accountId() : null;
        if (payer != null && ledger.balance(payer).available() < 1) {
            throw UserFacingException.badRequest("Вичитка ШІ — за шаги, а у вас їх поки немає.");
        }
        List<space.panrid.novelka.suggestion.Suggestions.BlockChange> paragraphs = glossary.naming(editionId, entry.ukrainian(), entry.looseForms());
        List<space.panrid.novelka.suggestion.Suggestions.BlockChange> changes = paragraphs.isEmpty() ? List.of()
                : agreement.regender(paragraphs, entry.ukrainian(), entry.gender(), payer);
        int count = suggestions.propose(viewer, editionId, changes, "Словник: рід «%s»".formatted(entry.ukrainian()), body.apply());
        return new Rewritten(count, (int) changes.stream().mapToInt(space.panrid.novelka.suggestion.Suggestions.BlockChange::chapterNumber).distinct().count());
    }

    /** @param translation the paragraph as it is in the editor now, saved or not */
    record SourceQuestion(String blockId, String translation, String selection) {
    }

    /**
     * @param paragraph the original paragraph, or null when this chapter has no original
     * @param original  the piece of it the selection translates, or null when none was found
     */
    record Source(String paragraph, String original, String language) {
    }

    /**
     * «Словник» in the editor (етап 19, п. 4): what a word selected in the translation was translated
     * from — a piece of the original itself. Asks AI, so it is for those who see it.
     */
    @PostMapping("/editions/{editionId}/chapters/{number}/glossary/source")
    Source source(@PathVariable long editionId, @PathVariable int number, @RequestBody SourceQuestion body) {
        Viewer viewer = access.requireTranslator(editionId).viewer();
        access.requireAi();
        String language = glossary.scope(editionId, number).language();
        String paragraph = glossary.originalBlock(editionId, number, body.blockId() == null ? "" : body.blockId()).orElse(null);
        if (paragraph == null) {
            return new Source(null, null, language);
        }
        String found = sourceOf.find(paragraph, body.translation() == null ? "" : body.translation(), body.selection(), language,
                personal(viewer) ? viewer.accountId() : null);
        return new Source(paragraph, found, language);
    }

    /** @param text the paragraph as it is in the editor now, saved or not */
    record VariantsQuestion(String blockId, String text) {
    }

    /** «Інший варіант» (етап 19, п. 5): other versions of the paragraph with the cursor, to pick from in the editor. */
    @PostMapping("/editions/{editionId}/chapters/{number}/variants")
    Map<String, List<String>> variants(@PathVariable long editionId, @PathVariable int number, @RequestBody VariantsQuestion body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        access.requireAi();
        return Map.of("variants", editorAi.variants(editionId, number, body.blockId(), body.text(), personal(viewer) ? viewer.accountId() : null));
    }

    /** @param text the proofread paragraph, or null when AI would change nothing */
    record Proofread(String text) {
    }

    /** «Вичитати абзац» (етап 19): AI's proofreading of the paragraph with the cursor, shown before it is taken. */
    @PostMapping("/editions/{editionId}/chapters/{number}/proofread-paragraph")
    Proofread proofreadParagraph(@PathVariable long editionId, @PathVariable int number, @RequestBody VariantsQuestion body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        access.requireAi();
        return new Proofread(editorAi.proofread(editionId, number, body.blockId(), body.text(), personal(viewer) ? viewer.accountId() : null));
    }

    /**
     * «Перевірити главу» (етап 19, п. 5): AI reads the published chapter against its original; each paragraph it
     * would change waits as a suggestion, accepted or rejected one by one.
     */
    @PostMapping("/editions/{editionId}/chapters/{number}/check")
    Map<String, Integer> check(@PathVariable long editionId, @PathVariable int number) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        access.requireAi();
        return Map.of("proposed", editorAi.check(viewer, editionId, number, personal(viewer) ? viewer.accountId() : null));
    }

    /** {@code chapter}: where the word was met; {@code gender} only matters for characters. */
    record NewEntry(int chapter, String ukrainian, String original, String kind, String gender) {
    }

    /** «Додати до словника» (етап 19, п. 4): the team's own entry, approved at once. */
    @PostMapping("/editions/{editionId}/glossary")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> addEntry(@PathVariable long editionId, @RequestBody NewEntry body) {
        access.requireTranslator(editionId);
        return Map.of("id", glossary.addByHand(editionId, body.chapter(), body.ukrainian(), body.original(), body.kind(), body.gender()));
    }

    @DeleteMapping("/editions/{editionId}/glossary/{entryId}")
    void deleteEntry(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTranslator(editionId);
        glossary.delete(editionId, entryId);
    }

    /** {@code linked} false unties the words from the entry in that paragraph. */
    record LinkRequest(String blockId, long entryId, String form, boolean linked) {
    }

    /** The team ties a word of a translated paragraph to a glossary entry, or unties it there. */
    @PostMapping("/editions/{editionId}/chapters/{number}/glossary-links")
    void linkWord(@PathVariable long editionId, @PathVariable int number, @RequestBody LinkRequest body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        glossary.link(editionId, number, body.blockId(), body.entryId(), body.form(), body.linked(), viewer.accountId());
    }

    // ---- groups of names and who acts in the story (the hints and «Персонажі») ------------------

    record SameAs(long mainId) {
    }

    @PostMapping("/editions/{editionId}/glossary/{entryId}/same-as")
    void sameAs(@PathVariable long editionId, @PathVariable long entryId, @RequestBody SameAs body) {
        access.requireTranslator(editionId);
        glossary.mergeInto(editionId, entryId, body.mainId());
    }

    @DeleteMapping("/editions/{editionId}/glossary/{entryId}/same-as")
    void separate(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTranslator(editionId);
        glossary.separate(editionId, entryId);
    }

    @PostMapping("/editions/{editionId}/glossary/{entryId}/main")
    void makeMain(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTranslator(editionId);
        glossary.makeMain(editionId, entryId);
    }

    record InStory(boolean inStory) {
    }

    @PutMapping("/editions/{editionId}/glossary/{entryId}/in-story")
    void inStory(@PathVariable long editionId, @PathVariable long entryId, @RequestBody InStory body) {
        access.requireTranslator(editionId);
        glossary.setInStory(editionId, entryId, body.inStory());
    }

    /** How many characters are not sorted yet and what one ask of the model would cost (the site pays). */
    @GetMapping("/editions/{editionId}/glossary/roles")
    StoryRoles.Quote rolesQuote(@PathVariable long editionId) {
        owner();
        access.requireTranslator(editionId);
        return storyRoles.quote(editionId);
    }

    @PostMapping("/editions/{editionId}/glossary/roles")
    StoryRoles.Done sortRoles(@PathVariable long editionId) {
        owner();
        access.requireTranslator(editionId);
        return storyRoles.run(editionId);
    }

    // ---- facts of an entry: its «хто це» card ---------------------------------------------------

    record FactRequest(int chapter, String text) {
    }

    @GetMapping("/editions/{editionId}/glossary/{entryId}/facts")
    List<Glossary.Fact> facts(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTextEditor(editionId);
        return glossary.facts(editionId, entryId);
    }

    @PostMapping("/editions/{editionId}/glossary/{entryId}/facts")
    @ResponseStatus(HttpStatus.CREATED)
    Glossary.Fact addFact(@PathVariable long editionId, @PathVariable long entryId, @RequestBody FactRequest body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        return glossary.saveFact(editionId, entryId, null, body.chapter(), body.text(), viewer.accountId());
    }

    @PutMapping("/editions/{editionId}/glossary/{entryId}/facts/{factId}")
    Glossary.Fact changeFact(@PathVariable long editionId, @PathVariable long entryId, @PathVariable long factId,
            @RequestBody FactRequest body) {
        Viewer viewer = access.requireTextEditor(editionId).viewer();
        return glossary.saveFact(editionId, entryId, factId, body.chapter(), body.text(), viewer.accountId());
    }

    @DeleteMapping("/editions/{editionId}/glossary/{entryId}/facts/{factId}")
    void deleteFact(@PathVariable long editionId, @PathVariable long entryId, @PathVariable long factId) {
        access.requireTextEditor(editionId);
        glossary.deleteFact(editionId, entryId, factId);
    }

    private boolean showShah(Viewer viewer) {
        return db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
    }

    // ---- the owner's money and models ------------------------------------------------------

    /** @param parallel chapters of different novels that translate at once */
    record Wallet(boolean configured, boolean showShah, Jobs.Balance balance, BigDecimal usdPerShah, Settings settings,
            List<Jobs.CostRow> report, int parallel) {
    }

    record ParallelRequest(Integer chapters) {
    }

    @GetMapping("/autotranslate/wallet")
    Wallet wallet(@RequestParam(defaultValue = "30") int days) {
        Viewer viewer = owner();
        boolean showShah = db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
        Settings settings = jobs.settings();
        return new Wallet(ai.configured(), showShah, jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()),
                settings, jobs.report(Math.max(1, Math.min(days, 365))), jobs.parallel());
    }

    @PutMapping("/autotranslate/parallel")
    void saveParallel(@RequestBody ParallelRequest body) {
        Viewer viewer = owner();
        jobs.saveParallel(body.chapters() == null ? 0 : body.chapters(), viewer.accountId());
    }

    @PutMapping("/autotranslate/settings")
    void saveSettings(@RequestBody Settings settings) {
        Viewer viewer = owner();
        jobs.saveSettings(settings, viewer.accountId());
    }
}
