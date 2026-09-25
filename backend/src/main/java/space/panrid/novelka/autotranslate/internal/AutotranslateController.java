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
            int lastAnalyzed, int nextToAnalyze,
            Jobs.Balance balance, BigDecimal usdPerShah, Jobs.Quote quote, List<Jobs.JobView> jobs) {
    }

    @GetMapping("/editions/{editionId}/autotranslate")
    Overview overview(@PathVariable long editionId, @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "translate") String kind) {
        Viewer viewer = ownerTranslating(editionId);
        Jobs.Novel novel = jobs.novel(editionId);
        boolean showShah = db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
        Settings settings = jobs.settings();
        return new Overview(ai.configured(), showShah, novel.sourceChapters(), novel.nextNumber(), novel.publishedChapters(),
                novel.lastAnalyzed(), novel.nextToAnalyze(),
                jobs.balance().orElse(null), Settings.usdOfMicro(settings.microUsdPerShah()),
                to == null ? null : jobs.quote(editionId, to, kind), jobs.jobs(editionId));
    }

    /** @param kind «analyze» (glossary and chapter titles only) or «translate» */
    record StartRequest(int to, String kind) {
    }

    @PostMapping("/editions/{editionId}/autotranslate/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    Jobs.JobView start(@PathVariable long editionId, @RequestBody StartRequest body) {
        Viewer viewer = ownerTranslating(editionId);
        long jobId = jobs.start(editionId, body.to(), "analyze".equals(body.kind()) ? "analyze" : "translate", viewer.accountId());
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

    // ---- chapter titles and numbers from analysis, checked before translating ---------------

    record AnalysisItem(int number, String title, String label, boolean edited) {
    }

    @GetMapping("/editions/{editionId}/analysis")
    List<AnalysisItem> analysis(@PathVariable long editionId) {
        access.requireTextEditor(editionId);
        return analyses.from(editionId, jobs.novel(editionId).nextNumber()).stream()
                .map(done -> new AnalysisItem(done.number(), done.title(), done.label(), done.edited())).toList();
    }

    record AnalysisChange(String title, String label) {
    }

    @PutMapping("/editions/{editionId}/analysis/{number}")
    void editAnalysis(@PathVariable long editionId, @PathVariable int number, @RequestBody AnalysisChange body) {
        access.requireTranslator(editionId);
        analyses.edit(editionId, number, body.title(), body.label());
    }

    @PostMapping("/editions/{editionId}/glossary/checked")
    void allChecked(@PathVariable long editionId) {
        access.requireTranslator(editionId);
        glossary.markAllChecked(editionId);
    }

    // ---- glossary: the team edits it, the Japanese side stays on the server (рішення 8) --------

    record GlossaryItem(long id, String ukrainian, String kind, String gender, String note, Integer chapter, boolean manual) {
    }

    @GetMapping("/editions/{editionId}/glossary")
    List<GlossaryItem> glossary(@PathVariable long editionId) {
        access.requireTextEditor(editionId);
        return glossary.all(editionId).stream()
                .map(entry -> new GlossaryItem(entry.id(), entry.ukrainian(), entry.kind(), entry.gender(), entry.note(),
                        entry.sourceChapter(), entry.manual()))
                .toList();
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
