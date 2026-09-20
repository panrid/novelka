package panrid.space.novelka.core.integration.source;

import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Novel;

public interface NovelSource {
    Novel inspect(String url) throws Exception;

    Chapter fetch(Novel novel, int number) throws Exception;
}
