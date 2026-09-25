package space.panrid.novelka.reading.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Relay;
import space.panrid.novelka.catalog.RelayState;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.reading.internal.ReadingQueries.EditionRow;
import space.panrid.novelka.reading.internal.ReadingQueries.NovelRow;
import space.panrid.novelka.reading.internal.Views.EditionSummary;

@RestController
@RequestMapping("/api")
class ReadingController {

    record ProgressRequest(int chapterNumber, float position) {
    }

    record ListRequest(String list) {
    }

    private static final int PAGE_SIZE = 20;
    private static final int CHAPTERS_PAGE_SIZE = 100;

    private final ReadingQueries queries;
    private final LibraryService library;
    private final CurrentUser currentUser;
    private final Relay relay;

    ReadingController(ReadingQueries queries, LibraryService library, CurrentUser currentUser, Relay relay) {
        this.queries = queries;
        this.library = library;
        this.currentUser = currentUser;
        this.relay = relay;
    }

    @GetMapping("/home")
    Views.Home home() {
        Optional<Viewer> viewer = currentUser.viewer();
        boolean adult = adult(viewer);
        return new Views.Home(
                viewer.map(v -> queries.continueReading(v.accountId(), adult, 6)).orElse(List.of()),
                queries.popular(adult, 12),
                queries.newChapters(adult, 15));
    }

    @GetMapping("/catalog")
    Views.Page<Views.Card> catalog(@RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> tag, @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "all") String machine, @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "1") int page) {
        if (q != null && q.length() > 100) {
            throw UserFacingException.badRequest("Запит задовгий.");
        }
        return queries.search(q, tag == null ? List.of() : tag.stream().limit(5).toList(), kind, machine, sort,
                adult(currentUser.viewer()), Math.max(1, Math.min(page, 500)), PAGE_SIZE);
    }

    @GetMapping("/tags")
    List<Views.TagCount> tags() {
        return queries.tags(adult(currentUser.viewer()), 60);
    }

    @GetMapping("/novels/{slug}")
    Views.NovelPage novel(@PathVariable String slug, @RequestParam(required = false) String t) {
        Optional<Viewer> viewer = currentUser.viewer();
        NovelRow novel = queries.novel(slug).orElseThrow(ReadingController::noNovel);
        List<EditionRow> editions = visibleEditions(novel, viewer);
        EditionRow edition = pick(editions, t);
        List<EditionSummary> summaries = queries.summaries(editions);
        EditionSummary chosen = summaries.get(editions.indexOf(edition));
        return new Views.NovelPage(novel.slug(), edition.title() != null ? edition.title() : novel.title(),
                novel.author(), origin(novel.source()),
                queries.readerBlocks(edition.description() != null ? edition.description() : novel.description()),
                queries.allTags(novel.id()), chosen, summaries, edition.adult(), edition.lastPublishedAt(),
                viewer.map(v -> queries.viewer(v.accountId(), edition.id())).orElse(null), relay(edition.id()));
    }

    @GetMapping("/novels/{slug}/chapters")
    Views.Page<Views.ChapterRow> chapters(@PathVariable String slug, @RequestParam(required = false) String t,
            @RequestParam(defaultValue = "asc") String order, @RequestParam(defaultValue = "1") int page) {
        EditionRow edition = pick(visibleEditions(queries.novel(slug).orElseThrow(ReadingController::noNovel),
                currentUser.viewer()), t);
        return queries.chapters(edition.id(), order.equals("desc"), Math.max(1, page), CHAPTERS_PAGE_SIZE);
    }

    @GetMapping("/novels/{slug}/chapters/{number}")
    Views.ReaderChapter chapter(@PathVariable String slug, @PathVariable int number,
            @RequestParam(required = false) String t) {
        Optional<Viewer> viewer = currentUser.viewer();
        NovelRow novel = queries.novel(slug).orElseThrow(ReadingController::noNovel);
        List<EditionRow> editions = visibleEditions(novel, viewer);
        EditionRow edition = pick(editions, t);
        ReadingQueries.ChapterText text = queries.chapter(edition.id(), number)
                .orElseThrow(() -> UserFacingException.notFound("Такої глави немає."));
        Integer next = queries.neighbour(edition.id(), number, true);
        Optional<Views.ViewerState> state = viewer.map(v -> queries.viewer(v.accountId(), edition.id()));
        Float saved = state.filter(s -> s.chapterNumber() != null && s.chapterNumber() == number)
                .map(Views.ViewerState::position)
                .orElse(null);
        String teamRole = state.map(Views.ViewerState::teamRole).orElse(null);
        return new Views.ReaderChapter(novel.slug(), edition.title() != null ? edition.title() : novel.title(),
                queries.summaries(List.of(edition)).getFirst(), text.number(), text.title(),
                queries.readerBlocks(text.blocks()),
                queries.neighbour(edition.id(), number, false), next, saved,
                next == null ? relay(edition.id()).continuations().stream().findFirst().orElse(null) : null, teamRole,
                text.label());
    }

    @PutMapping("/progress/{editionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void progress(@PathVariable long editionId, @RequestBody ProgressRequest body) {
        library.saveProgress(currentUser.requireSignedIn(), editionId, body.chapterNumber(), body.position());
    }

    @GetMapping("/library")
    Views.LibraryPage library(@RequestParam(defaultValue = "reading") String list) {
        Viewer viewer = currentUser.requireSignedIn();
        if (!LibraryService.LISTS.contains(list)) {
            throw UserFacingException.badRequest("Такого списку немає.");
        }
        return queries.library(viewer.accountId(), list, viewer.adultConfirmed());
    }

    @PutMapping("/library/{editionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setList(@PathVariable long editionId, @RequestBody ListRequest body) {
        library.setList(currentUser.requireSignedIn(), editionId, body.list());
    }

    private Views.Relay relay(long editionId) {
        RelayState state = relay.state(editionId);
        return new Views.Relay(state.free(), state.reason(), state.lastNumber(), state.continuations().stream()
                .map(c -> new Views.Continuation(c.teamHandle(), c.teamName(), c.firstNumber())).toList());
    }

    /**
     * Editions the viewer may open. An 18+ novel for someone who has not confirmed the age
     * answers 403 with reason «adult», so the page can explain instead of pretending it is gone.
     */
    private List<EditionRow> visibleEditions(NovelRow novel, Optional<Viewer> viewer) {
        List<EditionRow> alive = queries.editions(novel.id()).stream()
                .filter(edition -> !edition.hidden() && edition.chapterCount() > 0).toList();
        if (alive.isEmpty()) {
            throw noNovel();
        }
        List<EditionRow> allowed = alive.stream().filter(edition -> !edition.adult() || adult(viewer)).toList();
        if (allowed.isEmpty()) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, viewer.isPresent()
                    ? "Ця новела для дорослих. Підтвердьте в налаштуваннях приватності, що вам є 18."
                    : "Ця новела для дорослих. Увійдіть і підтвердьте, що вам є 18.", "adult");
        }
        return allowed;
    }

    /** The edition named in ?t=, otherwise the most popular one. */
    private static EditionRow pick(List<EditionRow> editions, String handle) {
        if (handle == null || handle.isBlank()) {
            return editions.getFirst();
        }
        return editions.stream().filter(edition -> edition.teamHandle().equalsIgnoreCase(handle)).findFirst()
                .orElseThrow(() -> UserFacingException.notFound("Цього перекладу немає."));
    }

    private static String origin(String source) {
        return source.equals("original") ? "original" : "translation";
    }

    private static boolean adult(Optional<Viewer> viewer) {
        return viewer.map(Viewer::adultConfirmed).orElse(false);
    }

    private static UserFacingException noNovel() {
        return UserFacingException.notFound("Такої новели немає.");
    }
}
