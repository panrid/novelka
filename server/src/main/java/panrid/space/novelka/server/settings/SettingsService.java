package panrid.space.novelka.server.settings;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.server.account.Account;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AuditRepository;
import panrid.space.novelka.server.repository.SettingsRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public final class SettingsService {
    private final ReaderDatabase database;

    public SettingsService(ReaderDatabase database) { this.database = database; }

    public SiteSettings read() throws Exception {
        try (var jdbc = database.open()) {
            var saved = new SettingsRepository(jdbc).read();
            return saved == null ? new SiteSettings(0, true, 1500, 0.10, 5,
                    List.of(new StageSettings("analyze", "openai/gpt-4o-mini", .15, .60),
                            new StageSettings("translate", "openai/gpt-4o-mini", .15, .60),
                            new StageSettings("proofread", "openai/gpt-4o-mini", .15, .60)), false) : saved;
        }
    }

    public SiteSettings save(Account actor, SiteSettings settings) throws Exception {
        validate(settings);
        try (var jdbc = database.open()) {
            return jdbc.transaction(() -> {
                jdbc.exec("SELECT pg_advisory_xact_lock(728619)");
                var repository = new SettingsRepository(jdbc);
                var previous = repository.read();
                long revision = previous == null ? 0 : previous.revision();
                if (settings.revision() != revision) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Налаштування вже змінено. Оновіть сторінку.");
                var next = new SiteSettings(revision + 1, settings.registrationOpen(), settings.segmentChars(),
                        settings.targetUsdPer5000(), settings.maxBudgetUsd(), settings.stages(), settings.adminSelfApproval());
                repository.save(next);
                new AuditRepository(jdbc).add(actor.id(), "settings.update", "site", next);
                return next;
            });
        }
    }

    private void validate(SiteSettings settings) {
        if (settings.segmentChars() < 500 || settings.segmentChars() > 20000
                || !Double.isFinite(settings.targetUsdPer5000()) || settings.targetUsdPer5000() < 0
                || !Double.isFinite(settings.maxBudgetUsd()) || settings.maxBudgetUsd() <= 0 || settings.maxBudgetUsd() > 1000
                || settings.stages() == null || settings.stages().size() != 3)
            throw new IllegalArgumentException("Некоректні межі налаштувань.");
        var stages = new java.util.HashSet<String>();
        for (var stage : settings.stages()) {
            if (stage == null || stage.stage() == null || !Set.of("analyze", "translate", "proofread").contains(stage.stage())
                    || !stages.add(stage.stage()) || stage.model() == null
                    || !stage.model().matches("[A-Za-z0-9._:/-]{3,150}")
                    || !Double.isFinite(stage.inputUsdM()) || stage.inputUsdM() < 0
                    || !Double.isFinite(stage.outputUsdM()) || stage.outputUsdM() < 0)
                throw new IllegalArgumentException("Перевірте модель і тарифи кожного етапу.");
        }
    }
}
