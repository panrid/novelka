package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.JOB_EVENT;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.jooq.tables.records.JobRecord;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.OriginalFiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Studio's API for the original, autotranslation and the glossary (етап 22.3), under
 * {@code /api/v1/studio/novels/{novel}/translations/{team}}. The same rules as the Studio's pages,
 * through the same services: the original is the team's alone; runs are for translators of the team
 * who see AI (hidden from those without шаги while the site owner keeps it so), and translating
 * from the original's site — only for those the site owner let in; everyone but the site owner pays
 * with their шаги, held at the start and settled at the end. With a token, runs need the «translate»
 * right and the rest the «studio» one.
 */
@RestController
@RequestMapping("/api/v1/studio/novels/{novel}/translations/{team}")
@Tag(name = "Оригінал, автопереклад і словник", description = "Оригінал бачить лише команда; запуски автоперекладу — право токена `translate` і ваші шаги; решта — `studio`.")
class TranslateApiController {

    private static final int PAGE = 20;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final AccessPolicy access;
    private final Jobs jobs;
    private final Originals originals;
    private final Glossary glossary;
    private final Presets presets;
    private final MySets mySets;
    private final Ledger ledger;
    private final DSLContext db;
    private final JsonMapper json;

    TranslateApiController(AccessPolicy access, Jobs jobs, Originals originals, Glossary glossary, Presets presets, MySets mySets,
            Ledger ledger, DSLContext db, JsonMapper json) {
        this.access = access;
        this.jobs = jobs;
        this.originals = originals;
        this.glossary = glossary;
        this.presets = presets;
        this.mySets = mySets;
        this.ledger = ledger;
        this.db = db;
        this.json = json;
    }

    // ---- the original: the team's alone ------------------------------------------------------------

    /** A translator of the translation, as «Оригінал» in the Studio asks. */
    private EditionAccess originalKeeper(String novel, String team) {
        access.requireOriginals();
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        originals.requireTranslation(who.editionId());
        return who;
    }

    @GetMapping("/originals")
    @Operation(summary = "Глави оригіналу", description = "Глави оригіналу, які вставила команда.")
    TranslateApiV1.Page<TranslateApiV1.OriginalEntry> originals(@PathVariable String novel, @PathVariable String team,
            @RequestParam(required = false) String after, @RequestParam(defaultValue = "" + PAGE) int limit) {
        EditionAccess who = originalKeeper(novel, team);
        int size = size(limit);
        Long from = number(after);
        List<TranslateApiV1.OriginalEntry> items = originals.after(who.editionId(), from == null ? null : from.intValue(), size).stream()
                .map(row -> new TranslateApiV1.OriginalEntry(row.number(), row.title(), row.chars(), row.language(), row.translated(),
                        row.updatedAt())).toList();
        return new TranslateApiV1.Page<>(items, items.size() == size ? String.valueOf(items.getLast().number()) : null);
    }

    @GetMapping("/originals/{number}")
    @Operation(summary = "Глава оригіналу", description = "Текст глави оригіналу.")
    TranslateApiV1.Original original(@PathVariable String novel, @PathVariable String team, @PathVariable int number) {
        EditionAccess who = originalKeeper(novel, team);
        Originals.Original found = originals.find(who.editionId(), number);
        return new TranslateApiV1.Original(found.number(), found.title(), found.language(), found.blocks().stream()
                .filter(block -> !block.text().isBlank() || "separator".equals(block.type()))
                .map(block -> new TranslateApiV1.OriginalBlock(block.id(), block.type(), block.text())).toList());
    }

    /** Pastes chapters of the original; a chapter under a number the translation has an original of is replaced. */
    @PostMapping("/originals")
    @Operation(summary = "Вставити оригінал", description = "Вставляє глави оригіналу; глава під наявним номером замінюється.")
    @ResponseStatus(HttpStatus.CREATED)
    TranslateApiV1.Pasted paste(@PathVariable String novel, @PathVariable String team, @RequestBody TranslateApiV1.Paste body) {
        EditionAccess who = originalKeeper(novel, team);
        OriginalFiles.Parsed parsed;
        if (body.chapters() != null && !body.chapters().isEmpty()) {
            parsed = OriginalController.separate(new OriginalController.Separate(body.chapters().stream()
                    .map(chapter -> new OriginalController.Given(chapter.title(), chapter.text())).toList(), body.first(), body.language()));
        } else {
            parsed = OriginalController.parse(null, body.text());
        }
        if (parsed.chapters().isEmpty()) {
            throw UserFacingException.badRequest("Вставте текст оригіналу.");
        }
        Originals.Saved saved = originals.save(who.editionId(), who.viewer().accountId(), parsed, body.first(), body.language());
        return new TranslateApiV1.Pasted(saved.saved(), saved.from(), saved.to());
    }

    // ---- runs: AI, at the person's шаги -------------------------------------------------------------

    /**
     * A translator of the translation who may run it, as on «Автопереклад»: from the original the
     * team pasted — whoever sees AI; from the original's site — those the site owner let in.
     */
    private EditionAccess translator(String novel, String team) {
        access.requireSignedIn();
        long editionId = access.editionAt(novel, team);
        if (jobs.novel(editionId).pasted()) {
            access.requireAi();
        } else {
            access.requireAutotranslate();
        }
        return access.requireTranslator(editionId);
    }

    private static boolean personal(Viewer viewer) {
        return viewer.role() != SiteRole.OWNER;
    }

    @GetMapping("/runs/options")
    @Operation(summary = "Можливості запуску", description = "Етапи, моделі й набори, доступні вам для автоперекладу.")
    TranslateApiV1.Options options(@PathVariable String novel, @PathVariable String team) {
        EditionAccess who = translator(novel, team);
        boolean personal = personal(who.viewer());
        Jobs.Novel found = jobs.novel(who.editionId());
        Ledger.Balance balance = personal ? ledger.balance(who.viewer().accountId()) : null;
        return new TranslateApiV1.Options(personal, balance == null ? null : new TranslateApiV1.Balance(balance.available(), balance.reserved()),
                found.pasted(), found.sourceChapters(), found.nextNumber(),
                (personal ? presets.forPeople() : presets.list()).stream().map(preset -> new TranslateApiV1.Preset(preset.id(), preset.name(),
                        preset.summary(), preset.quality(), preset.analyze(), preset.translate(), preset.proofread())).toList(),
                mySets.peopleModels());
    }

    /** Кошторис: what the run would do and cost, and whether the person has the шаги for it. */
    @PostMapping("/runs/estimate")
    @Operation(summary = "Кошторис", description = "Що зробить запуск і скільки шагів коштуватиме; чи вистачає їх.")
    TranslateApiV1.Estimate estimate(@PathVariable String novel, @PathVariable String team, @RequestBody TranslateApiV1.RunRequest body) {
        EditionAccess who = translator(novel, team);
        boolean personal = personal(who.viewer());
        Jobs.Quote quote = jobs.quote(who.editionId(), plan(body), personal);
        Integer available = personal ? ledger.balance(who.viewer().accountId()).available() : null;
        return new TranslateApiV1.Estimate(quote.kind(), quote.from(), quote.to(), quote.chapters(), quote.skipped(), quote.steps(),
                quote.shah(), quote.reserveShah(), available, available == null || available >= quote.reserveShah(), quote.estimated(),
                model(quote.analyzeModel()), model(quote.translateModel()), model(quote.proofreadModel()));
    }

    /** Starts the run: a person's шаги for it are held now (400 when short), what is not spent comes back. */
    @PostMapping("/runs")
    @Operation(summary = "Запустити автопереклад", description = "Шаги на запуск резервуються зараз (400, якщо бракує); невитрачене повертається.")
    @ResponseStatus(HttpStatus.CREATED)
    TranslateApiV1.Run start(@PathVariable String novel, @PathVariable String team, @RequestBody TranslateApiV1.RunRequest body) {
        EditionAccess who = translator(novel, team);
        long jobId = jobs.start(who.editionId(), plan(body), who.viewer().accountId(), personal(who.viewer()));
        return run(jobs.job(who.editionId(), jobId).orElseThrow());
    }

    /** The translation's runs, the newest first. */
    @GetMapping("/runs")
    @Operation(summary = "Запуски", description = "Запуски автоперекладу цього перекладу — найновіші першими.")
    TranslateApiV1.Page<TranslateApiV1.Run> runs(@PathVariable String novel, @PathVariable String team,
            @RequestParam(required = false) String after, @RequestParam(defaultValue = "" + PAGE) int limit) {
        EditionAccess who = translator(novel, team);
        int size = size(limit);
        Long from = number(after);
        List<TranslateApiV1.Run> items = db.selectFrom(JOB)
                .where(JOB.EDITION_ID.eq(who.editionId()), from == null ? DSL.noCondition() : JOB.ID.lt(from))
                .orderBy(JOB.ID.desc()).limit(size).fetch().stream().map(this::run).toList();
        return new TranslateApiV1.Page<>(items, items.size() == size ? String.valueOf(items.getLast().id()) : null);
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Стан запуску", description = "Стан запуску автоперекладу.")
    TranslateApiV1.Run run(@PathVariable String novel, @PathVariable String team, @PathVariable long runId) {
        EditionAccess who = translator(novel, team);
        return run(job(who.editionId(), runId));
    }

    /** The run's journal in the order it happened, 50 events to a page (at most 200). */
    @GetMapping("/runs/{runId}/log")
    @Operation(summary = "Журнал запуску", description = "Події запуску в тому порядку, як вони стались.")
    TranslateApiV1.Page<TranslateApiV1.Event> log(@PathVariable String novel, @PathVariable String team, @PathVariable long runId,
            @RequestParam(required = false) String after, @RequestParam(defaultValue = "50") int limit) {
        EditionAccess who = translator(novel, team);
        job(who.editionId(), runId);
        int size = Math.max(1, Math.min(limit, 200));
        Long from = number(after);
        List<TranslateApiV1.Event> items = db.selectFrom(JOB_EVENT)
                .where(JOB_EVENT.JOB_ID.eq(runId), from == null ? DSL.noCondition() : JOB_EVENT.ID.gt(from))
                .orderBy(JOB_EVENT.ID).limit(size)
                .fetch(r -> new TranslateApiV1.Event(r.getId(), r.getChapterNumber(), r.getKind(), json.readValue(r.getPayload().data(), MAP),
                        r.getCreatedAt()));
        return new TranslateApiV1.Page<>(items, items.size() == size ? String.valueOf(items.getLast().id()) : null);
    }

    /**
     * Stops the run; a person's шаги it did not spend come back. As on the site: the team cancels its
     * runs, whoever started one may cancel it even after leaving the team, the site owner any.
     */
    @PostMapping("/runs/{runId}/cancel")
    @Operation(summary = "Скасувати запуск", description = "Зупиняє запуск; невитрачені шаги повертаються.")
    TranslateApiV1.Run cancel(@PathVariable String novel, @PathVariable String team, @PathVariable long runId) {
        Viewer viewer = access.requireSignedIn();
        long editionId = access.editionAt(novel, team);
        JobRecord job = job(editionId, runId);
        if (job.getRequestedBy() != viewer.accountId() && viewer.role() != SiteRole.OWNER) {
            access.requireTranslator(editionId);
        }
        jobs.cancel(editionId, runId);
        return run(job(editionId, runId));
    }

    /** «Продовжити» after a failure (or a lost answer the site would not ask again by itself). */
    @PostMapping("/runs/{runId}/resume")
    @Operation(summary = "Продовжити запуск", description = "«Продовжити» після збою чи загубленої відповіді.")
    TranslateApiV1.Run resume(@PathVariable String novel, @PathVariable String team, @PathVariable long runId) {
        EditionAccess who = translator(novel, team);
        jobs.resume(who.editionId(), runId);
        return run(job(who.editionId(), runId));
    }

    private JobRecord job(long editionId, long runId) {
        return jobs.job(editionId, runId).orElseThrow(() -> UserFacingException.notFound("Такого запуску немає."));
    }

    private TranslateApiV1.Run run(JobRecord job) {
        Jobs.JobView view = jobs.view(job);
        Jobs.StepView step = view.current();
        return new TranslateApiV1.Run(view.id(), view.kind(), view.state(), view.from(), view.to(), view.done(), view.quoteShah(),
                view.spentShah(), view.chargedShah(), view.personal(),
                step == null ? null : new TranslateApiV1.Step(step.number(), step.stage(), step.state(), step.error(), step.progress()),
                view.error(), view.createdAt(), view.finishedAt());
    }

    private static Jobs.Plan plan(TranslateApiV1.RunRequest body) {
        TranslateApiV1.StepModels models = body.models();
        return new Jobs.Plan(null, body.from(), body.to(), body.redo(),
                models == null ? null : new Jobs.Models(models.analyze(), models.translate(), models.proofread(), null),
                body.preset(), body.steps(), body.publish());
    }

    private static String model(Settings.Stage stage) {
        return stage == null || !stage.enabled() ? null : stage.model();
    }

    // ---- the glossary: the team reviews it -----------------------------------------------------------

    /** The novel's glossary in the order entries were added; {@code status}, {@code kind} and {@code q} narrow it. */
    @GetMapping("/glossary")
    @Operation(summary = "Словник", description = "Словник новели в порядку додавання; `status`, `kind` і `q` звужують його.")
    TranslateApiV1.Page<TranslateApiV1.Entry> glossary(@PathVariable String novel, @PathVariable String team,
            @RequestParam(required = false) String status, @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q, @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "" + PAGE) int limit) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        if (status != null && !Glossary.STATUSES.contains(status)) {
            throw UserFacingException.badRequest("Стан запису: new, approved або rejected.");
        }
        if (kind != null && !Glossary.KINDS.contains(kind)) {
            throw UserFacingException.badRequest("Вид запису: character, place, organization, term або other.");
        }
        int size = Math.max(1, Math.min(limit, 200));
        List<TranslateApiV1.Entry> items = glossary.after(who.editionId(), status, kind, q, number(after), size).stream()
                .map(TranslateApiController::toEntry).toList();
        return new TranslateApiV1.Page<>(items, items.size() == size ? String.valueOf(items.getLast().id()) : null);
    }

    @GetMapping("/glossary/{entryId}")
    @Operation(summary = "Запис словника", description = "Один запис словника.")
    TranslateApiV1.Entry entry(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return entry(who.editionId(), entryId);
    }

    /** The team's own entry: approved at once, as entries written by hand are. */
    @PostMapping("/glossary")
    @Operation(summary = "Додати запис", description = "Запис команди: одразу затверджений, як написаний вручну.")
    @ResponseStatus(HttpStatus.CREATED)
    TranslateApiV1.Entry addEntry(@PathVariable String novel, @PathVariable String team, @RequestBody TranslateApiV1.NewEntry body) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        if (body.kind() == null || !Glossary.KINDS.contains(body.kind())) {
            throw UserFacingException.badRequest("Вид запису: character, place, organization, term або other.");
        }
        long id = glossary.addByHand(who.editionId(), body.chapter() == null ? 0 : body.chapter(), body.ukrainian(), body.original(),
                body.kind(), body.gender() == null ? "unknown" : body.gender());
        return entry(who.editionId(), id);
    }

    /** Corrects the entry; fields left out stay. A corrected entry becomes approved. */
    @PatchMapping("/glossary/{entryId}")
    @Operation(summary = "Змінити запис", description = "Виправляє запис; пропущені поля лишаються. Виправлений запис стає затвердженим.")
    TranslateApiV1.Entry changeEntry(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId,
            @RequestBody TranslateApiV1.EntryChange body) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        Glossary.Entry now = glossary.find(who.editionId(), entryId)
                .orElseThrow(() -> UserFacingException.notFound("Такого запису в словнику немає."));
        glossary.update(who.editionId(), entryId, body.ukrainian() != null ? body.ukrainian() : now.ukrainian(),
                body.kind() != null ? body.kind() : now.kind(), body.gender() != null ? body.gender() : now.gender(),
                body.note() != null ? body.note() : now.note(), null);
        return entry(who.editionId(), entryId);
    }

    /** «Затвердити», «Відхилити» or «Повернути в нові» for the entries given. */
    @PostMapping("/glossary/status")
    @Operation(summary = "Змінити стан записів", description = "«Затвердити», «Відхилити» чи «Повернути в нові» для вказаних записів.")
    TranslateApiV1.Changed status(@PathVariable String novel, @PathVariable String team, @RequestBody TranslateApiV1.StatusChange body) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        return new TranslateApiV1.Changed(glossary.setStatus(who.editionId(), body.ids(), body.status()));
    }

    /** «Погодити нові»: every entry nobody checked yet becomes approved. */
    @PostMapping("/glossary/approve-new")
    @Operation(summary = "Погодити нові", description = "Усі ще не перевірені записи стають затвердженими.")
    TranslateApiV1.Changed approveNew(@PathVariable String novel, @PathVariable String team) {
        EditionAccess who = access.requireTranslator(access.editionAt(novel, team));
        return new TranslateApiV1.Changed(glossary.approveNew(who.editionId()));
    }

    @GetMapping("/glossary/{entryId}/facts")
    @Operation(summary = "Факти запису", description = "Що новела розповідає про цей запис і з якої глави.")
    List<TranslateApiV1.Fact> facts(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return glossary.facts(who.editionId(), entryId).stream().map(TranslateApiController::toFact).toList();
    }

    @PostMapping("/glossary/{entryId}/facts")
    @Operation(summary = "Додати факт", description = "Новий факт про запис.")
    @ResponseStatus(HttpStatus.CREATED)
    TranslateApiV1.Fact addFact(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId,
            @RequestBody TranslateApiV1.FactRequest body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return toFact(glossary.saveFact(who.editionId(), entryId, null, chapter(body), body.text(), who.viewer().accountId()));
    }

    @PutMapping("/glossary/{entryId}/facts/{factId}")
    @Operation(summary = "Змінити факт", description = "Змінює факт.")
    TranslateApiV1.Fact changeFact(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId,
            @PathVariable long factId, @RequestBody TranslateApiV1.FactRequest body) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        return toFact(glossary.saveFact(who.editionId(), entryId, factId, chapter(body), body.text(), who.viewer().accountId()));
    }

    @DeleteMapping("/glossary/{entryId}/facts/{factId}")
    @Operation(summary = "Видалити факт", description = "Видаляє факт.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFact(@PathVariable String novel, @PathVariable String team, @PathVariable long entryId, @PathVariable long factId) {
        EditionAccess who = access.requireTextEditor(access.editionAt(novel, team));
        glossary.deleteFact(who.editionId(), entryId, factId);
    }

    private TranslateApiV1.Entry entry(long editionId, long entryId) {
        return glossary.find(editionId, entryId).map(TranslateApiController::toEntry)
                .orElseThrow(() -> UserFacingException.notFound("Такого запису в словнику немає."));
    }

    private static TranslateApiV1.Entry toEntry(Glossary.Entry entry) {
        return new TranslateApiV1.Entry(entry.id(), entry.ukrainian(), entry.original(), entry.reading(), entry.kind(), entry.gender(),
                entry.note(), entry.sourceChapter(), entry.status(), entry.manual());
    }

    private static TranslateApiV1.Fact toFact(Glossary.Fact fact) {
        return new TranslateApiV1.Fact(fact.id(), fact.chapter(), fact.text(), fact.manual());
    }

    private static int chapter(TranslateApiV1.FactRequest body) {
        if (body.chapter() == null) {
            throw UserFacingException.badRequest("Вкажіть главу, з якої це відомо (0 — з самого початку).");
        }
        return body.chapter();
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private static int size(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    /** A cursor of this API: the last id (or number) of the page before. */
    private static Long number(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(after.strip());
        } catch (NumberFormatException error) {
            throw UserFacingException.badRequest("Список змінився — почніть із першої сторінки.");
        }
    }
}
