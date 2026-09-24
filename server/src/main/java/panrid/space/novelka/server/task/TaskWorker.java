package panrid.space.novelka.server.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import panrid.space.novelka.core.integration.source.syosetu.Syosetu;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.service.translation.Pipeline;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.NovelAccessRepository;
import panrid.space.novelka.server.repository.AccountRepository;
import panrid.space.novelka.server.repository.TaskRepository;
import panrid.space.novelka.server.settings.SiteSettings;

@Component
@ConditionalOnProperty(name = "novelka.worker.enabled", havingValue = "true", matchIfMissing = true)
public final class TaskWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TaskWorker.class);
    private final ReaderDatabase database;
    private final TaskAiFactory factory;

    public TaskWorker(ReaderDatabase database, TaskAiFactory factory) {
        this.database = database; this.factory = factory;
    }

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        // This connection owns the global worker lock until the entire task finishes.
        try (var control = database.open()) {
            var queue = new TaskRepository(control);
            if (!queue.workerLock()) return;
            queue.recoverInterrupted();
            var row = queue.next();
            if (row == null) return;
            String id = (String) row.get("id");
            String actor = (String) row.get("actor_id");
            queue.state(id, "running", "");
            try {
                check(queue, id, actor);
                run(queue, id, actor, Json.decode(row.get("request").toString(), TaskRequest.class),
                        Json.decode(row.get("settings").toString(), SiteSettings.class));
                queue.state(id, queue.cancelled(id) ? "cancelled" : "complete", "");
            } catch (Exception error) {
                boolean cancel = queue.cancelled(id);
                String message = TaskFailureMessage.describe(error);
                queue.state(id, cancel ? "cancelled" : "failed", message == null ? "Помилка операції." : message.substring(0, Math.min(500, message.length())));
                LOG.warn("Task {} failed ({})", id, error.getClass().getSimpleName());
            }
        } catch (Exception error) {
            LOG.warn("Worker unavailable ({})", error.getClass().getSimpleName());
        }
    }

    private void run(TaskRepository queue, String task, String actor, TaskRequest request, SiteSettings settings) throws Exception {
        try (var db = database.openDatabase(); var lock = db.lock(request.novelId())) {
            check(queue, task, actor);
            if (request.operation().equals("import")) {
                var source = new Syosetu();
                var imported = source.inspect(request.url());
                if (request.last() > imported.chapterCount()) throw new IllegalArgumentException("Діапазон перевищує кількість глав.");
                // Refresh source metadata without erasing manual Ukrainian metadata.
                Novel existing = null;
                try { existing = db.novels().novel(imported.id()); } catch (IllegalArgumentException ignored) { }
                db.novels().save(new Novel(imported.id(), imported.title(), existing == null ? null : existing.titleUk(),
                        imported.author(), existing == null ? null : existing.authorUk(),
                        existing == null ? null : existing.descriptionUk(), imported.url(), imported.chapterCount(), imported.shortStory()));
                // Whoever imports a new novel from the web becomes its translator.
                if (existing == null) try (var jdbc = database.open()) { new NovelAccessRepository(jdbc).owner(imported.id(), actor); }
                for (int chapter = request.first(); chapter > 0 && chapter <= request.last(); chapter++) {
                    check(queue, task, actor);
                    db.chapters().save(imported.id(), source.fetch(imported, chapter));
                }
                return;
            }
            var ai = factory.create(db.calls(), settings, request.dictionarySearchLimit());
            var pipeline = new Pipeline(db, (job, segment, stage, glossary, payload, budget) -> {
                check(queue, task, actor);
                return ai.generate(job, segment, stage, glossary, payload, budget);
            }, settings.segmentChars(), settings.targetUsdPer5000());
            if (request.operation().equals("resume")) {
                Work work = db.jobs().job(request.jobId());
                if (!db.jobs().latest(work.novelId(), work.chapter()).id().equals(work.id()))
                    throw new IllegalArgumentException("Продовжувати можна лише останню ревізію глави.");
                var source = db.chapters().chapter(work.novelId(), work.chapter());
                if (!panrid.space.novelka.core.support.Hashes.hash(source.blocks()).equals(work.sourceHash()))
                    throw new IllegalArgumentException("Оригінал змінився. Створіть нову ревізію перекладу.");
                double before = db.calls().spent(work.id());
                queue.job(task, work.id(), before);
                if (request.retryUncertain()) db.calls().authorizeRetry(work.id());
                try { pipeline.run(work, before + request.budgetUsd()); }
                finally { queue.finishJob(task, work.id()); }
                return;
            }
            double consumed = 0;
            var novel = db.novels().novel(request.novelId());
            for (int chapter = request.first(); chapter <= request.last(); chapter++) {
                check(queue, task, actor);
                if (consumed >= request.budgetUsd()) throw new IllegalStateException("Бюджет вичерпано. Прогрес збережено.");
                if (!db.chapters().exists(novel.id(), chapter))
                    db.chapters().save(novel.id(), new Syosetu().fetch(novel, chapter));
                Work work;
                if (request.operation().equals("proofread")) {
                    var previous = db.jobs().latest(novel.id(), chapter);
                    if (previous == null) throw new IllegalArgumentException("Спочатку перекладіть главу.");
                    if (!panrid.space.novelka.core.support.Hashes.hash(db.chapters().chapter(novel.id(), chapter).blocks()).equals(previous.sourceHash()))
                        throw new IllegalArgumentException("Оригінал змінився. Створіть нову ревізію перекладу.");
                    work = pipeline.proofread(previous);
                } else work = pipeline.create(novel.id(), chapter, request.force());
                double before = db.calls().spent(work.id());
                queue.job(task, work.id(), before);
                try { pipeline.run(work, before + request.budgetUsd() - consumed); }
                finally { queue.finishJob(task, work.id()); }
                consumed += db.calls().spent(work.id()) - before;
            }
        }
    }

    private void check(TaskRepository queue, String task, String actor) throws Exception {
        if (Thread.currentThread().isInterrupted() || queue.cancelled(task))
            throw new IllegalStateException("Операцію скасовано після останньої контрольної точки.");
        try (var jdbc = database.open()) {
            var account = new AccountRepository(jdbc).byId(actor);
            var novel = jdbc.rows("SELECT owner_id FROM novels WHERE id=(SELECT novel_id FROM web_tasks WHERE id=?)", task);
            // A new import has no novel yet; otherwise the author must still translate the novel or be an administrator.
            if (account == null || !novel.isEmpty() && !account.role().includes(Role.ADMIN) && !actor.equals(novel.getFirst().get("owner_id")))
                throw new IllegalStateException("Автор завдання більше не має права запускати переклад.");
        }
    }
}
