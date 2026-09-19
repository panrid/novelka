package panrid.space.novelka.cli.command;

import java.util.concurrent.Callable;
import panrid.space.novelka.cli.config.ApplicationContext;
import panrid.space.novelka.core.Store;

public abstract class DatabaseCommand implements Callable<Integer> {
  @Override
  public final Integer call() throws Exception {
    try (Store store = ApplicationContext.openStore()) {
      execute(store);
    }
    return 0;
  }

  protected abstract void execute(Store store) throws Exception;

  protected final String novelId(Store store, String reference) throws Exception {
    return store.resolveNovel(reference);
  }
}
