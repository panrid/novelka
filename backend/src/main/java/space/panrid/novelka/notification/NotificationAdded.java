package space.panrid.novelka.notification;

import java.util.Map;

/**
 * A row came into someone's inbox (or an unread row grew). {@code payload} is what the row
 * says, for this event alone: new chapters carry the range just published, suggestions the
 * count just sent. Messengers repeat it outside the site.
 */
public record NotificationAdded(long recipientId, String kind, Map<String, Object> payload) {
}
