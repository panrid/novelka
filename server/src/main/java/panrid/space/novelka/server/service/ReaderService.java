package panrid.space.novelka.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import panrid.space.novelka.core.model.Novel;
import panrid.space.novelka.core.repository.NovelRepository;
import panrid.space.novelka.core.repository.ReaderRepository;
import panrid.space.novelka.core.support.Json;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.dto.ChapterSummary;
import panrid.space.novelka.server.dto.NovelCard;
import panrid.space.novelka.server.dto.NovelDetail;
import panrid.space.novelka.server.dto.ReaderChapter;
import panrid.space.novelka.server.repository.CatalogRepository;
import panrid.space.novelka.server.repository.TagRepository;
import panrid.space.novelka.server.tag.TagNames;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public final class ReaderService {
    private final ReaderDatabase database;

    public ReaderService(ReaderDatabase database) {
        this.database = database;
    }

    public ListPage<NovelCard> catalog(ListQuery query, boolean readyOnly, List<String> tags) throws Exception {
        var slugs = tags == null ? List.<String>of() : tags.stream().filter(tag -> !tag.isBlank()).map(TagNames::slug).distinct().toList();
        if (slugs.size() > 12) throw new IllegalArgumentException("Можна вибрати до 12 тегів.");
        try (var jdbc = database.open()) {
            var page = new CatalogRepository(jdbc).list(query, readyOnly, slugs);
            var tagsByNovel = new TagRepository(jdbc).forNovels(page.items().stream().map(row -> (String) row.get("id")).toList());
            var cards = new ArrayList<NovelCard>();
            for (var row : page.items()) {
                var novel = Json.decode(row.get("data").toString(), Novel.class);
                var aliases = StreamSupport.stream(Json.read(row.get("aliases").toString()).spliterator(), false)
                        .map(node -> node.asText()).toList();
                cards.add(new NovelCard(novel.id(), novel.displayTitle(), novel.displayAuthor(), description(novel), novel.chapterCount(),
                        ((Number) row.get("ready_chapters")).intValue(), aliases, tagsByNovel.getOrDefault(novel.id(), List.of())));
            }
            return ListPage.of(cards, query, page.total());
        }
    }

    public NovelDetail novel(String reference, Integer resume) throws Exception {
        try (var jdbc = database.open()) {
            var novels = new NovelRepository(jdbc);
            String id = resolve(novels, reference);
            var novel = novels.novel(id);
            var reader = new ReaderRepository(jdbc);
            var stats = reader.chapterStats(id);
            return new NovelDetail(id, novel.displayTitle(), novel.displayAuthor(), description(novel), novel.chapterCount(),
                    ((Number) stats.get("ready")).longValue(), stats.get("first_chapter") == null ? null : ((Number) stats.get("first_chapter")).intValue(),
                    resume != null && resume > 0 && reader.hasChapter(id, resume) ? resume : null, new TagRepository(jdbc).forNovel(id));
        }
    }

    public ListPage<ChapterSummary> contents(String reference, ListQuery query) throws Exception {
        if (!query.sort().isEmpty() && !query.sort().equals("number")) throw new IllegalArgumentException("Глави можна сортувати за номером.");
        try (var jdbc = database.open()) {
            String id = resolve(new NovelRepository(jdbc), reference);
            var reader = new ReaderRepository(jdbc);
            long total = reader.chapterCount(id, query.pattern());
            var chapters = reader.chapterPage(id, query.pattern(), query.direction(), query.size(), query.offset()).stream()
                    .map(row -> new ChapterSummary(((Number) row.get("chapter")).intValue(),
                            (String) row.get("title"), ((Number) row.get("revision")).intValue())).toList();
            return ListPage.of(chapters, query, total);
        }
    }

    public ReaderChapter chapter(String reference, int number, String authorId) throws Exception {
        if (number < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try (var jdbc = database.open()) {
            String id = resolve(new NovelRepository(jdbc), reference);
            var work = new ReaderRepository(jdbc).chapter(id, number);
            if (work == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var blocks = work.segments().stream()
                    .flatMap(segment -> segment.revised().stream())
                    .toList();
            String title = blocks.stream().filter(block -> block.kind().equals("heading"))
                    .map(block -> block.text()).findFirst().orElse("Глава " + number);
            var personal = new java.util.LinkedHashMap<Integer, String>();
            if (authorId != null) {
                for (var row : new panrid.space.novelka.server.repository.CorrectionRepository(jdbc).pending(authorId, work))
                    personal.put(((Number) row.get("block_index")).intValue(), (String) row.get("replacement"));
            }
            var neighbours = new ReaderRepository(jdbc).neighbours(id, number);
            return new ReaderChapter(id, number, work.revision(), title, blocks, work.id(), personal,
                    neighbours.get("previous") == null ? null : ((Number) neighbours.get("previous")).intValue(),
                    neighbours.get("next") == null ? null : ((Number) neighbours.get("next")).intValue());
        }
    }

    private String resolve(NovelRepository novels, String reference) throws Exception {
        try {
            return novels.resolveNovel(reference);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String description(Novel novel) {
        return novel.descriptionUk() == null ? "" : novel.descriptionUk();
    }
}
