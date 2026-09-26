package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.math.BigDecimal;
import java.util.List;

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
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SyosetuLink;
import space.panrid.novelka.team.Teams;

/**
 * The site owner runs autotranslation at the site's cost; anyone else with шаги runs it in
 * translations they translate, paying from their balance at the site's models (рішення 29).
 * Models, prices and the wallet stay the owner's.
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

    AutotranslateController(AccessPolicy access, Preparation preparation, Jobs jobs, Glossary glossary, Ai ai, DSLContext db,
            Teams teams, Analyses analyses, Ledger ledger) {
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

    /** A translator of the edition; who pays is decided by {@link #personal}. */
    private Viewer ownerTranslating(long editionId) {
        Viewer viewer = access.requireSignedIn();
        access.requireTranslator(editionId);
        return viewer;
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
        Viewer viewer = access.requireSignedIn();
        if (personal(viewer) && ledger.balance(viewer.accountId()).available() < 1) {
            throw UserFacingException.badRequest("Автопереклад запускається за шаги, а у вас їх поки немає. Шаги нараховує власник сайту.");
        }
        SyosetuLink link = SyosetuLink.parse(body.url());
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
     */
    record Overview(boolean configured, boolean showShah, int sourceChapters, int nextNumber, int publishedChapters,
            int lastAnalyzed, int nextToAnalyze, int averageChars, Jobs.Balance balance, BigDecimal usdPerShah, Settings settings,
            List<Jobs.JobView> jobs, boolean personal, int reserved) {
    }

    @GetMapping("/editions/{editionId}/autotranslate")
    Overview overview(@PathVariable long editionId) {
        Viewer viewer = ownerTranslating(editionId);
        Jobs.Novel novel = jobs.novel(editionId);
        Settings settings = jobs.settings();
        if (personal(viewer)) {
            Ledger.Balance mine = ledger.balance(viewer.accountId());
            long price = ledger.microUsdPerShah();
            return new Overview(ai.configured(), true, novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                    novel.lastAnalyzed(), novel.nextToAnalyze(), jobs.averageChars(editionId),
                    new Jobs.Balance(mine.available(), Settings.usdOfMicro(mine.available() * price)), Settings.usdOfMicro(price),
                    settings.paidBy(price), jobs.jobs(editionId), true, mine.reserved());
        }
        return new Overview(ai.configured(), showShah(viewer), novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                novel.lastAnalyzed(), novel.nextToAnalyze(), jobs.averageChars(editionId),
                jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()), settings, jobs.jobs(editionId), false, 0);
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

    /** Every run the viewer started, running ones first. */
    @GetMapping("/autotranslate/processes")
    List<Jobs.Process> processes(@RequestParam(defaultValue = "1") int page) {
        return jobs.processes(access.requireSignedIn().accountId(), page);
    }

    /**
     * {@code chapterUsd}: a chapter (or its stage) for text models, one picture for models that draw.
     * {@code rating}: recommended, usual or weak (see {@link ModelRatings}).
     */
    record ModelChoice(String id, String name, double inputPerMillion, double outputPerMillion, BigDecimal chapterUsd, String rating) {
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
                    long micro = !text ? pictureMicroUsd(model) : at >= 0
                            ? Settings.stageMicroUsd(at, size, model.inputPerMillion(), model.outputPerMillion())
                            : Settings.defaults().withModels(stageOf(model), stageOf(model), stageOf(model)).expectedMicroUsd(size, true, true);
                    String rating = text ? ModelRatings.of(model.id()).name().toLowerCase(java.util.Locale.ROOT) : "usual";
                    return new ModelChoice(model.id(), model.name(), model.inputPerMillion(), model.outputPerMillion(),
                            Settings.usdOfMicro(micro), rating);
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

    record GlossaryItem(long id, String ukrainian, String kind, String gender, String note, Integer chapter, boolean manual,
            String status) {
    }

    record GlossaryPage(List<GlossaryItem> items, int total, int page, boolean hasMore, List<Integer> chapters,
            java.util.Map<Integer, String> labels, java.util.Map<String, Integer> counts) {
    }

    @GetMapping("/editions/{editionId}/glossary")
    GlossaryPage glossary(@PathVariable long editionId, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer chapter, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "alpha") String sort, @RequestParam(defaultValue = "1") int page) {
        access.requireTextEditor(editionId);
        Glossary.Page found = glossary.page(editionId, status != null && Glossary.STATUSES.contains(status) ? status : null, chapter, q, sort, page);
        return new GlossaryPage(found.items().stream()
                .map(entry -> new GlossaryItem(entry.id(), entry.ukrainian(), entry.kind(), entry.gender(), entry.note(),
                        entry.sourceChapter(), entry.manual(), entry.status()))
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

    record GlossaryChange(String ukrainian, String kind, String gender, String note) {
    }

    @PutMapping("/editions/{editionId}/glossary/{entryId}")
    void updateEntry(@PathVariable long editionId, @PathVariable long entryId, @RequestBody GlossaryChange body) {
        access.requireTranslator(editionId);
        glossary.update(editionId, entryId, body.ukrainian(), body.kind(), body.gender(), body.note());
    }

    /** The original behind an entry, for the team only (рішення 30). */
    @GetMapping("/editions/{editionId}/glossary/{entryId}/original")
    Glossary.Original original(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTextEditor(editionId);
        return glossary.original(editionId, entryId);
    }

    @DeleteMapping("/editions/{editionId}/glossary/{entryId}")
    void deleteEntry(@PathVariable long editionId, @PathVariable long entryId) {
        access.requireTranslator(editionId);
        glossary.delete(editionId, entryId);
    }

    private boolean showShah(Viewer viewer) {
        return db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
    }

    // ---- the owner's money and models ------------------------------------------------------

    record Wallet(boolean configured, boolean showShah, Jobs.Balance balance, BigDecimal usdPerShah, Settings settings,
            List<Jobs.CostRow> report) {
    }

    @GetMapping("/autotranslate/wallet")
    Wallet wallet(@RequestParam(defaultValue = "30") int days) {
        Viewer viewer = owner();
        boolean showShah = db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
        Settings settings = jobs.settings();
        return new Wallet(ai.configured(), showShah, jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()),
                settings, jobs.report(Math.max(1, Math.min(days, 365))));
    }

    @PutMapping("/autotranslate/settings")
    void saveSettings(@RequestBody Settings settings) {
        Viewer viewer = owner();
        jobs.saveSettings(settings, viewer.accountId());
    }
}
