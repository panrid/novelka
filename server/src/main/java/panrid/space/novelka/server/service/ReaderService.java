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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public final class ReaderService {
    private final ReaderDatabase database;

    public ReaderService(ReaderDatabase database) {
        this.database = database;
    }

    public List<NovelCard> catalog() throws Exception {
        try (var jdbc = database.open()) {
            var cards = new ArrayList<NovelCard>();
            for (var row : new ReaderRepository(jdbc).catalog()) {
                var novel = Json.decode(row.get("data").toString(), Novel.class);
                var aliases = StreamSupport.stream(Json.read(row.get("aliases").toString()).spliterator(), false)
                        .map(node -> node.asText()).toList();
                cards.add(new NovelCard(novel.id(), novel.displayTitle(), novel.author(), novel.chapterCount(),
                        ((Number) row.get("ready_chapters")).intValue(), aliases));
            }
            return cards;
        }
    }

    public NovelDetail novel(String reference) throws Exception {
        try (var jdbc = database.open()) {
            var novels = new NovelRepository(jdbc);
            String id = resolve(novels, reference);
            var novel = novels.novel(id);
            var chapters = new ReaderRepository(jdbc).chapters(id).stream()
                    .map(row -> new ChapterSummary(((Number) row.get("chapter")).intValue(),
                            (String) row.get("title"), ((Number) row.get("revision")).intValue())).toList();
            return new NovelDetail(id, novel.displayTitle(), novel.author(), novel.chapterCount(), chapters);
        }
    }

    public ReaderChapter chapter(String reference, int number) throws Exception {
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
            return new ReaderChapter(id, number, work.revision(), title, blocks);
        }
    }

    private String resolve(NovelRepository novels, String reference) throws Exception {
        try {
            return novels.resolveNovel(reference);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
