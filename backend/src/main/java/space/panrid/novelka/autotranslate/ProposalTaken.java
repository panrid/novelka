package space.panrid.novelka.autotranslate;

import java.util.List;

/**
 * A team took a proposed novel to translate (рішення 32).
 *
 * @param voters who voted for it, except the person who took it
 */
public record ProposalTaken(long proposalId, String title, String novelSlug, String teamHandle, long takenBy,
        List<Long> voters) {
}
