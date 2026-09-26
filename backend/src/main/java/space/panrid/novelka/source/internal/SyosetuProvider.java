package space.panrid.novelka.source.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceEntry;
import space.panrid.novelka.source.SourceHttp;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.SourceNovel;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Syosetu (小説家になろう): the novel's data from the site's API, chapters from their pages.
 * Episodes are numbered on the site as they are listed, so the position is the address; a
 * short story has no chapter pages and its text is on the novel's own page.
 */
@Component
@Order(1)
class SyosetuProvider implements SourceProvider {

    static final String ID = "syosetu";

    private final SourceHttp http;
    private final JsonMapper json;

    SyosetuProvider(SourceHttp http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Syosetu";
    }

    @Override
    public Optional<SourceLink> parse(String raw) {
        return SyosetuLink.parse(raw).map(SyosetuLink::link);
    }

    @Override
    public SourceNovel novel(SourceLink link) {
        SyosetuLink syosetu = SyosetuLink.fromKey(link.key());
        String api = "https://api.syosetu.com/%s/api/?out=json&of=t-w-s-ga-nt-e&ncode=%s"
                .formatted(syosetu.adult() ? "novel18api" : "novelapi", syosetu.code());
        JsonNode answer = json.readTree(http.get(api));
        if (!answer.isArray() || answer.size() < 2) {
            throw UserFacingException.notFound("На Syosetu немає новели з таким посиланням.");
        }
        JsonNode novel = answer.get(1);
        boolean serial = novel.path("noveltype").asInt(1) == 1;
        List<SourceEntry> chapters = new ArrayList<>();
        if (serial) {
            int count = Math.max(1, novel.path("general_all_no").asInt(1));
            for (int number = 1; number <= count; number++) {
                chapters.add(new SourceEntry(number, String.valueOf(number), null, null, null, true));
            }
        } else {
            chapters.add(new SourceEntry(1, "", null, null, null, true));
        }
        return new SourceNovel(link, "ja", novel.path("title").asString(""), novel.path("writer").asString(""),
                novel.path("story").asString(""), novel.path("end").asInt(1) == 0, chapters);
    }

    @Override
    public Page chapter(SourceLink link, SourceEntry entry) {
        String url = SyosetuLink.fromKey(link.key()).url();
        return SyosetuPages.chapter(http.get(entry.ref().isEmpty() ? url : url + entry.ref() + "/"));
    }
}
