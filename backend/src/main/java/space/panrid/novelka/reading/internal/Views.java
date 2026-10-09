package space.panrid.novelka.reading.internal;

import java.time.OffsetDateTime;
import java.util.List;

import space.panrid.novelka.platform.text.Span;

/** JSON shapes of the reading pages. Internal ids are included only where the app sends them back. */
final class Views {

    private Views() {
    }

    /** A novel in a list: shelf, catalog row, library row. {@code sourceChapters}: in the original, if known. */
    record Card(long editionId, String novelSlug, String teamHandle, String teamName, String title, String author,
            String coverUrl, String kind, String status, boolean adult, int chapterCount, List<String> tags,
            OffsetDateTime lastPublishedAt, Integer sourceChapters) {
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

    record TagGroup(String name, List<TagCount> tags) {
    }

    /** A page of the catalog with how many there are in all. */
    record Found<T>(List<T> items, int page, boolean hasMore, int total) {
    }

    /** What the search box offers while a person types. */
    record Hints(List<Card> novels, List<TagCount> tags) {
    }

    /**
     * @param rating          average stars, null until someone rates
     * @param downloadAllowed the team lets readers download it as EPUB
     */
    record EditionSummary(long editionId, String teamHandle, String teamName, String kind, String status,
            int chapterCount, String coverUrl, Double rating, int ratings, java.time.LocalDate pausedUntil,
            boolean downloadAllowed) {
    }

    /** {@code teamRole}: owner, translator or editor when the viewer works on this edition. */
    /** @param chapterLabel the number readers see for {@code chapterNumber} (null: the position itself) */
    /** {@code relayAsked}: a team of the viewer's already asked to continue this translation and waits. */
    /** @param subscribed the bell rings: new chapters of this translation come to the reader's inbox */
    /**
     * @param skipped     published chapters before the place that are not read
     * @param firstUnread the first of them, with the number readers see
     */
    record ViewerState(String list, Integer chapterNumber, Float position, String teamRole, Integer myRating, String chapterLabel,
            boolean relayAsked, boolean subscribed, int skipped, Integer firstUnread, String firstUnreadLabel) {
    }

    /**
     * @param language    the original's language (ja, en…), or null when the site does not know it
     * @param originalUrl the original's page, when the site knows it
     */
    record NovelPage(String slug, String title, String author, String origin, String language, List<ReaderBlock> description,
            List<String> tags, EditionSummary edition, List<EditionSummary> editions, boolean adult,
            OffsetDateTime lastPublishedAt, ViewerState viewer, Relay relay, String originalUrl,
            space.panrid.novelka.catalog.NovelFacts facts) {
    }

    /** «Естафета» on the novel page: free to continue and who continues already. */
    record Relay(boolean free, String reason, int lastNumber, List<Continuation> continuations) {
    }

    record Continuation(String teamHandle, String teamName, int firstNumber) {
    }

    /** @param label number readers see; null means {@code number}, empty means none */
    /** @param volume the volume the chapter is in, or null before the first one */
    /** {@code read}: whether the signed-in reader finished it; null for a guest. */
    record ChapterRow(int number, String title, OffsetDateTime publishedAt, String label, VolumeRef volume, Boolean read) {
    }

    /**
     * A volume as readers see it; kind is volume, prologue, side or extra.
     *
     * @param index «Том 2»: ordinary volumes counted from 1; null for the others
     */
    record VolumeRef(int firstNumber, String title, String kind, Integer index) {
    }

    /**
     * A volume one can download as a book.
     *
     * @param lastNumber the last chapter number it may hold; null for the last volume
     */
    record VolumeChoice(int firstNumber, Integer lastNumber, String title, int chapters) {
    }

    record ReaderBlock(String id, String type, List<Span> content, String imageUrl) {
    }

    /** {@code savedPosition}: where the signed-in reader stopped in this very chapter, 0..1. */
    record ReaderChapter(String novelSlug, String novelTitle, EditionSummary edition, int number, String title,
            List<ReaderBlock> blocks, Integer previous, Integer next, Float savedPosition, Continuation continuation,
            String teamRole, String label, VolumeRef volume) {
    }

    record Activity(List<Card> reading, int acceptedSuggestions) {
    }

    record LibraryPage(List<LibraryItem> items, java.util.Map<String, Integer> counts, int total, int page, boolean hasMore) {
    }

    record LibraryItem(Card card, String list, Integer chapterNumber, String chapterLabel) {
    }
}
