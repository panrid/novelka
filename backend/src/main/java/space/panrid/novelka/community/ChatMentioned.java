package space.panrid.novelka.community;

import java.util.List;

/** Someone mentioned people or teams in the site chat; {@code channel} is the channel's slug. */
public record ChatMentioned(long messageId, long authorId, List<Long> mentionedAccounts, List<Long> mentionedTeams,
        String excerpt, String channel) {
}
