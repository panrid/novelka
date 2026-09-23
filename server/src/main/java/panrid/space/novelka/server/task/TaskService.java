package panrid.space.novelka.server.task;

import org.springframework.stereotype.Service;
import panrid.space.novelka.core.integration.source.syosetu.Syosetu;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.TaskRepository;
import panrid.space.novelka.server.settings.SettingsService;

import java.util.Map;
import java.util.Set;

@Service
public final class TaskService {
    private final ReaderDatabase database;
    private final SettingsService settings;

    public TaskService(ReaderDatabase database, SettingsService settings) { this.database = database; this.settings = settings; }

    public String enqueue(Account actor, TaskRequest request) throws Exception {
        if (request.requestKey() == null || !request.requestKey().matches("[a-zA-Z0-9-]{8,80}")
                || request.operation() == null || !Set.of("import", "translate", "proofread", "resume").contains(request.operation()))
            throw new IllegalArgumentException("Невідома операція або ключ запиту.");
        var snapshot = settings.read();
        if (request.dictionarySearchLimit() < 0 || request.dictionarySearchLimit() > 30)
            throw new IllegalArgumentException("Ліміт звернень до словника: від 0 до 30.");
        if (!request.operation().equals("import") && (!Double.isFinite(request.budgetUsd())
                || request.budgetUsd() <= 0 || request.budgetUsd() > snapshot.maxBudgetUsd()))
            throw new IllegalArgumentException("Вкажіть додатний бюджет до $" + snapshot.maxBudgetUsd() + ".");
        String id;
        try (var jdbc = database.open()) {
            if (request.operation().equals("import")) id = Syosetu.code(request.url() == null ? "" : request.url());
            else if (request.operation().equals("resume")) id = new JobRepository(jdbc).job(request.jobId()).novelId();
            else id = new NovelRepository(jdbc).resolveNovel(request.novelId());
            if (!request.operation().equals("resume")) {
                if (!(request.operation().equals("import") && request.first() == 0 && request.last() == 0)
                        && (request.first() < 1 || request.last() < request.first() || request.last() - request.first() >= 100))
                    throw new IllegalArgumentException("Вкажіть діапазон від 1 до 100 глав.");
                if (request.operation().equals("proofread") && request.first() != request.last())
                    throw new IllegalArgumentException("Вичитка запускається для однієї глави.");
                if (!request.operation().equals("import")
                        && request.last() > new NovelRepository(jdbc).novel(id).chapterCount())
                    throw new IllegalArgumentException("Діапазон перевищує кількість глав.");
            }
            var normalized = new TaskRequest(request.requestKey(), request.operation(), id, request.url(), request.first(),
                    request.last(), request.jobId(), request.force(), request.retryUncertain(), request.budgetUsd(), request.dictionarySearchLimit());
            return jdbc.transaction(() -> {
                String task = new TaskRepository(jdbc).enqueue(actor.id(), normalized, snapshot);
                new AuditRepository(jdbc).add(actor.id(), "task.enqueue", task, Map.of("operation", request.operation(), "budget", request.budgetUsd()));
                return task;
            });
        }
    }
}
