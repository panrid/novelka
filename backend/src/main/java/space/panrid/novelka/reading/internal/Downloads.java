package space.panrid.novelka.reading.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.media.ImageFile;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.reading.internal.ReadingQueries.EditionRow;
import space.panrid.novelka.reading.internal.ReadingQueries.NovelRow;

/**
 * «Завантажити EPUB»: a translation, whole or one volume, as a book for a reader with an
 * account. The team may forbid it; its own people can always download.
 */
@Service
class Downloads {

    /** A book is built in memory, so a person gets a few a minute. */
    private static final int PER_MINUTE = 5;
    private static final int COVER_WIDTH = 960;
    private static final int PICTURE_WIDTH = 1280;
    private static final Map<String, String> FROM = Map.of("ja", "японської", "en", "англійської", "ko", "корейської",
            "zh", "китайської", "fr", "французької", "de", "німецької", "es", "іспанської", "pl", "польської");

    record File(String name, byte[] content) {
    }

    private final ReadingQueries queries;
    private final Images images;
    private final SiteProperties site;
    private final RateLimiter limiter;

    Downloads(ReadingQueries queries, Images images, SiteProperties site, Clock clock) {
        this.queries = queries;
        this.images = images;
        this.site = site;
        this.limiter = new RateLimiter(PER_MINUTE, Duration.ofMinutes(1), clock);
    }

    /** The volumes a reader may pick; empty when the translation has none. */
    List<Views.VolumeChoice> volumes(Viewer viewer, EditionRow edition) {
        requireAllowed(viewer, edition);
        return queries.volumeChoices(edition.id());
    }

    /** @param volume the first chapter number of the volume to download, or null for all chapters */
    File epub(Viewer viewer, NovelRow novel, EditionRow edition, Integer volume) {
        requireAllowed(viewer, edition);
        if (!limiter.tryAcquire(Long.toString(viewer.accountId()))) {
            throw UserFacingException.tooManyRequests();
        }
        Views.VolumeChoice part = null;
        if (volume != null) {
            part = queries.volumeChoices(edition.id()).stream().filter(choice -> choice.firstNumber() == volume).findFirst()
                    .orElseThrow(() -> UserFacingException.notFound("Такого тому немає."));
        }
        List<EpubBook.Chapter> chapters = part == null ? queries.bookChapters(edition.id(), Integer.MIN_VALUE, null)
                : queries.bookChapters(edition.id(), part.firstNumber(), part.lastNumber());
        if (chapters.isEmpty()) {
            throw UserFacingException.notFound("У перекладі ще немає глав.");
        }
        Map<Long, ImageFile> pictures = new LinkedHashMap<>();
        chapters.stream().flatMap(chapter -> chapter.blocks().stream()).map(Block::imageId).filter(Objects::nonNull).distinct()
                .forEach(id -> images.file(id, PICTURE_WIDTH).ifPresent(file -> pictures.put(id, file)));
        ImageFile cover = edition.coverImageId() == null ? null : images.file(edition.coverImageId(), COVER_WIDTH).orElse(null);
        String title = edition.title() != null ? edition.title() : novel.title();
        EpubBook.Book book = new EpubBook.Book(
                "urn:novelka:" + edition.id() + (part == null ? "" : ":" + part.firstNumber()),
                title, part == null ? null : part.title(), novel.author(), about(novel, edition, chapters), cover, chapters, pictures);
        String name = novel.slug() + (part == null ? "" : "-" + part.firstNumber()) + ".epub";
        return new File(name, EpubBook.write(book));
    }

    /** Lines of the title page: who wrote it, who translated it, which chapters and where to read on. */
    private List<String> about(NovelRow novel, EditionRow edition, List<EpubBook.Chapter> chapters) {
        List<String> lines = new ArrayList<>();
        if (novel.author() != null && !novel.author().isBlank()) {
            lines.add(novel.author());
        }
        boolean original = novel.source().equals("original");
        String from = novel.language() == null ? null : FROM.get(novel.language());
        boolean machine = edition.kind().equals("machine") || edition.kind().equals("mixed");
        lines.add(original ? "Твір: " + edition.teamName()
                : (from == null ? "Переклад" : "Переклад з " + from) + (machine ? " (ШІ з правками)" : "") + ": " + edition.teamName());
        EpubBook.Chapter first = chapters.getFirst();
        EpubBook.Chapter last = chapters.getLast();
        lines.add(first == last ? first.heading() : "Глави " + shown(first) + "–" + shown(last));
        lines.add(site.publicUrl() + "/n/" + novel.slug() + "?t=" + edition.teamHandle());
        return lines;
    }

    private static String shown(EpubBook.Chapter chapter) {
        return chapter.label() == null || chapter.label().isEmpty() ? String.valueOf(chapter.number()) : chapter.label();
    }

    private void requireAllowed(Viewer viewer, EditionRow edition) {
        if (!edition.downloadAllowed() && queries.viewer(viewer.accountId(), edition.id()).teamRole() == null) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Команда не дозволяє завантажувати цей переклад.");
        }
    }
}
