package space.panrid.novelka.messaging;

import java.util.List;

/**
 * A message in a conversation. {@code firstUnread} are the members who had read everything
 * before it and did not mute the conversation: a messenger pings them once per unread streak.
 *
 * @param title the group's name; empty for a direct conversation, the team's handle for a team chat
 */
public record MessagePosted(long conversationId, String kind, String title, String authorNick, String excerpt,
        List<Long> firstUnread) {
}
