package space.panrid.novelka.catalog;

import java.util.List;

/**
 * Whether others may continue an edition (рішення 4), and who already does.
 *
 * @param reason abandoned, inactive or unanswered when free; null otherwise
 */
public record RelayState(boolean free, String reason, int lastNumber, List<Continuation> continuations) {

    public record Continuation(long editionId, String teamHandle, String teamName, int firstNumber) {
    }
}
