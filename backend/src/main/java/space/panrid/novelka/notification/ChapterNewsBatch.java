package space.panrid.novelka.notification;

import java.util.List;
import java.util.Map;

/**
 * One batch of readers just told about new chapters on the site (or who chose not to see them
 * there): messengers repeat it. Published inside the batch's transaction, so a listener that
 * queues its own messages commits or rolls back with the batch. One {@code newsId} reaches a
 * reader at most once; {@code payload} is the inbox row's, chapters {@code first}..{@code last}.
 */
public record ChapterNewsBatch(long newsId, long editionId, int first, int last, String kind, Map<String, Object> payload,
        List<Long> recipients) {
}
