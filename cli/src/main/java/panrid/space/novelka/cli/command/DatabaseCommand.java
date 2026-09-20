package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.core.persistence.DatabaseSession;

import java.util.concurrent.Callable;

public abstract class DatabaseCommand implements Callable<Integer> {
    @Override
    public final Integer call() throws Exception {
        try (DatabaseSession database = ApplicationContext.openDatabase()) {
            execute(database);
        }
        return 0;
    }

    protected abstract void execute(DatabaseSession database) throws Exception;

    protected final String novelId(DatabaseSession database, String reference) throws Exception {
        return database.novels().resolveNovel(reference);
    }
}
