package space.panrid.novelka.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;

import space.panrid.novelka.studio.NovelChanged;

/** What the Studio announced of works created and changed: what a future «Журнал дій» would hear. */
public class NovelEvents {

    private final List<NovelChanged> heard = new CopyOnWriteArrayList<>();

    @EventListener
    void hear(NovelChanged event) {
        heard.add(event);
    }

    public List<NovelChanged> of(long editionId) {
        return heard.stream().filter(event -> event.editionId() == editionId).toList();
    }
}
