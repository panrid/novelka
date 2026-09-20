package panrid.space.novelka.core.persistence;

import panrid.space.novelka.core.repository.AiCallRepository;
import panrid.space.novelka.core.repository.ChapterRepository;
import panrid.space.novelka.core.repository.GlossaryRepository;
import panrid.space.novelka.core.repository.JobRepository;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.core.service.glossary.GlossaryService;

import java.util.concurrent.Callable;

/** Owns one connection; all repositories share its locks and transaction boundary. */
public final class DatabaseSession implements AutoCloseable {
    private final JdbcSession jdbc;
    private final NovelRepository novels;
    private final ChapterRepository chapters;
    private final JobRepository jobs;
    private final GlossaryRepository glossaries;
    private final AiCallRepository calls;
    private final GlossaryService glossaryService;

    public DatabaseSession(String url, String user, String password) throws Exception {
        jdbc = new JdbcSession(url, user, password);
        try {
            new MigrationRunner().migrate(jdbc);
        } catch (Exception error) {
            try {
                jdbc.close();
            } catch (Exception closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
        novels = new NovelRepository(jdbc);
        chapters = new ChapterRepository(jdbc);
        jobs = new JobRepository(jdbc);
        glossaries = new GlossaryRepository(jdbc);
        calls = new AiCallRepository(jdbc);
        glossaryService = new GlossaryService(jdbc, glossaries, jobs, calls);
    }

    public NovelRepository novels() {
        return novels;
    }

    public ChapterRepository chapters() {
        return chapters;
    }

    public JobRepository jobs() {
        return jobs;
    }

    public GlossaryRepository glossaries() {
        return glossaries;
    }

    public AiCallRepository calls() {
        return calls;
    }

    public GlossaryService glossaryService() {
        return glossaryService;
    }

    public <T> T transaction(Callable<T> action) throws Exception {
        return jdbc.transaction(action);
    }

    public AutoCloseable lock(String novel) throws Exception {
        return jdbc.lock(novel);
    }

    @Override
    public void close() throws Exception {
        jdbc.close();
    }
}
