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
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SyosetuLink;
import space.panrid.novelka.team.Teams;

/** At launch everything here is for the site owner only (рішення 23). */
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

    AutotranslateController(AccessPolicy access, Preparation preparation, Jobs jobs, Glossary glossary, Ai ai, DSLContext db,
            Teams teams, Analyses analyses) {
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

    /** Owner of the site and translator in the edition's team. */
    private Viewer ownerTranslating(long editionId) {
        Viewer viewer = owner();
        access.requireTranslator(editionId);
        return viewer;
    }

    /** @param team handle of the team; empty means the personal one */
    record PrepareRequest(String url, String team) {
    }

    record Prepared(long editionId, String novelSlug) {
    }

    @PostMapping("/autotranslate/prepare")
    Prepared prepare(@RequestBody PrepareRequest body) {
        Viewer viewer = owner();
        SyosetuLink link = SyosetuLink.parse(body.url());
        long teamId = body.team() == null || body.team().isBlank()
                ? teams.personalTeam(viewer.accountId())
                : teams.findByHandle(body.team()).orElseThrow(() -> UserFacingException.notFound("Такої команди немає.")).id();
        access.requireTeamTranslator(teamId);
        EditionRef ref = preparation.prepare(link, teamId);
        return new Prepared(ref.editionId(), ref.novelSlug());
    }

    record Overview(boolean configured, boolean showShah, int sourceChapters, int nextNumber, int publishedChapters,
            int lastAnalyzed, int nextToAnalyze, int averageChars, Jobs.Balance balance, BigDecimal usdPerShah, Settings settings,
            List<Jobs.JobView> jobs) {
    }

    @GetMapping("/editions/{editionId}/autotranslate")
    Overview overview(@PathVariable long editionId) {
        Viewer viewer = ownerTranslating(editionId);
        Jobs.Novel novel = jobs.novel(editionId);
        Settings settings = jobs.settings();
        return new Overview(ai.configured(), showShah(viewer), novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                novel.lastAnalyzed(), novel.nextToAnalyze(), jobs.averageChars(editionId),
                jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()), settings, jobs.jobs(editionId));
    }

    @PostMapping("/editions/{editionId}/autotranslate/quote")
    Jobs.Quote quote(@PathVariable long editionId, @RequestBody Jobs.Plan plan) {
        ownerTranslating(editionId);
        return jobs.quote(editionId, plan);
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    Jobs.JobView start(@PathVariable long editionId, @RequestBody Jobs.Plan plan) {
        Viewer viewer = ownerTranslating(editionId);
        long jobId = jobs.start(editionId, plan, viewer.accountId());
        return jobs.view(jobs.job(editionId, jobId).orElseThrow());
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/cancel")
    void cancel(@PathVariable long editionId, @PathVariable long jobId) {
        ownerTranslating(editionId);
        jobs.cancel(editionId, jobId);
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs/{jobId}/resume")
    void resume(@PathVariable long editionId, @PathVariable long jobId) {
        ownerTranslating(editionId);
        jobs.resume(editionId, jobId);
    }

    /** Every run of the owner's, running ones first. */
    @GetMapping("/autotranslate/processes")
    List<Jobs.Process> processes(@RequestParam(defaultValue = "1") int page) {
        return jobs.processes(owner().accountId(), page);
    }

    /**
     * @param chapterUsd a chapter of {@code chars} through analysis, translation and
     *                   proofreading, all by this model
     */
    record ModelChoice(String id, String name, double inputPerMillion, double outputPerMillion, BigDecimal chapterUsd) {
    }

    /** OpenRouter models whose id or name contains what was typed, with the price of a chapter. */
    @GetMapping("/autotranslate/models")
    List<ModelChoice> models(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "6000") int chars,
            @RequestParam(defaultValue = "text") String output) {
        owner();
        String query = q.strip().toLowerCase(java.util.Locale.ROOT);
        return ai.models().stream()
                .filter(model -> model.outputs().contains(output))
                .filter(model -> query.isEmpty() || model.id().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || model.name().toLowerCase(java.util.Locale.ROOT).contains(query))
                .limit(20)
                .map(model -> {
                    Settings.Stage stage = new Settings.Stage(model.id(), model.inputPerMillion(), model.outputPerMillion(), true);
                    long micro = Settings.defaults().withModels(stage, stage, stage).expectedMicroUsd(Math.max(500, chars), true, true);
                    return new ModelChoice(model.id(), model.name(), model.inputPerMillion(), model.outputPerMillion(), Settings.usdOfMicro(micro));
                })
                .toList();
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

    // ---- glossary: the team reviews it; the Japanese side stays on the server (рішення 8) ------

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
