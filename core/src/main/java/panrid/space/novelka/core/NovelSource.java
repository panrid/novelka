package panrid.space.novelka.core;

import static panrid.space.novelka.core.Domain.*;

public interface NovelSource {
  Novel inspect(String url) throws Exception;

  Chapter fetch(Novel novel, int number) throws Exception;
}
