package space.panrid.novelka.reading.internal;

import java.time.OffsetDateTime;
import java.util.List;

import space.panrid.novelka.platform.text.Span;

/** JSON shapes of the reading pages. Internal ids are included only where the app sends them back. */
final class Views {

    private Views() {
    }

    /** A novel in a list: shelf, catalog row, library row. */
    record Card(long editionId, String novelSlug, String teamHandle, String teamName, String title, String author,
            String coverUrl, String kind, String status, boolean adult, int chapterCount, List<String> tags,
            OffsetDateTime lastPublishedAt) {
    }

    /** {@code chapterLabel}: the number readers see (see ChapterLabels); null shows the position. */
    record ContinueItem(Card card, int chapterNumber, float position, String chapterLabel) {
    }

    /** @param firstLabel the number readers see for {@code firstNumber} (null: the position), likewise {@code lastLabel} */
    record NewChapters(Card card, int firstNumber, int lastNumber, OffsetDateTime publishedAt, String firstLabel, String lastLabel) {
    }

    record Home(List<ContinueItem> continueReading, List<Card> popular, List<NewChapters> newChapters) {
    }

    record Page<T>(List<T> items, int page, boolean hasMore) {
    }

    record TagCount(String name, String slug, int novels) {
    }

    /** @param rating average stars, null until someone rates */
    record EditionSummary(long editionId, String teamHandle, String teamName, String kind, String status,
            int chapterCount, String coverUrl, Double rating, int ratings) {
    }

    /** {@code teamRole}: owner, translator or editor when the viewer works on this edition. */
    /** @param chapterLabel the number readers see for {@code chapterNumber} (null: the position itself) */
    /** {@code relayAsked}: a team of the viewer's already asked to continue this translation and waits. */
    record ViewerState(String list, Integer chapterNumber, Float position, String teamRole, Integer myRating, String chapterLabel,
            boolean relayAsked) {
    }

    record NovelPage(String slug, String title, String author, String origin, List<ReaderBlock> description,
            List<String> tags, EditionSummary edition, List<EditionSummary> editions, boolean adult,
            OffsetDateTime lastPublishedAt, ViewerState viewer, Relay relay) {
    }

    /** «Естафета» on the novel page: free to continue and who continues already. */
    record Relay(boolean free, String reason, int lastNumber, List<Continuation> continuations) {
    }

    record Continuation(String teamHandle, String teamName, int firstNumber) {
    }

    /** @param label number readers see; null means {@code number}, empty means none */
    record ChapterRow(int number, String title, OffsetDateTime publishedAt, String label) {
    }

    record ReaderBlock(String id, String type, List<Span> content, String imageUrl) {
    }

    /** {@code savedPosition}: where the signed-in reader stopped in this very chapter, 0..1. */
    record ReaderChapter(String novelSlug, String novelTitle, EditionSummary edition, int number, String title,
            List<ReaderBlock> blocks, Integer previous, Integer next, Float savedPosition, Continuation continuation,
            String teamRole, String label) {
    }

    record Activity(List<Card> reading, int acceptedSuggestions) {
    }

    record LibraryPage(List<LibraryItem> items, java.util.Map<String, Integer> counts) {
    }

    record LibraryItem(Card card, String list, Integer chapterNumber, String chapterLabel) {
    }
}
