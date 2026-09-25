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

    record ContinueItem(Card card, int chapterNumber, float position) {
    }

    record NewChapters(Card card, int firstNumber, int lastNumber, OffsetDateTime publishedAt) {
    }

    record Home(List<ContinueItem> continueReading, List<Card> popular, List<NewChapters> newChapters) {
    }

    record Page<T>(List<T> items, int page, boolean hasMore) {
    }

    record TagCount(String name, String slug, int novels) {
    }

    record EditionSummary(long editionId, String teamHandle, String teamName, String kind, String status,
            int chapterCount, String coverUrl) {
    }

    /** {@code teamRole}: owner, translator or editor when the viewer works on this edition. */
    record ViewerState(String list, Integer chapterNumber, Float position, String teamRole) {
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

    record ChapterRow(int number, String title, OffsetDateTime publishedAt) {
    }

    record ReaderBlock(String id, String type, List<Span> content, String imageUrl) {
    }

    /** {@code savedPosition}: where the signed-in reader stopped in this very chapter, 0..1. */
    record ReaderChapter(String novelSlug, String novelTitle, EditionSummary edition, int number, String title,
            List<ReaderBlock> blocks, Integer previous, Integer next, Float savedPosition, Continuation continuation) {
    }

    record LibraryPage(List<LibraryItem> items, java.util.Map<String, Integer> counts) {
    }

    record LibraryItem(Card card, String list, Integer chapterNumber) {
    }
}
