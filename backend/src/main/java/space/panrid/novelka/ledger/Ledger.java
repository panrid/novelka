package space.panrid.novelka.ledger;

/**
 * A person's шаги. Runs paid by a person hold шаги first ({@link #hold}) and, once over, are
 * charged what the models really cost ({@link #settle}); the rest of the hold comes back.
 */
public interface Ledger {

    /** Free to spend, and held for runs still going. */
    record Balance(int available, int reserved) {
    }

    Balance balance(long accountId);

    /** What one шаг pays for at the models, in millionths of a dollar (the owner sets it). */
    long microUsdPerShah();

    /** Dollars of model cost as шаги, rounded up: 3 cents is 1 шаг at 7 cents, 8 cents is 2. */
    int shahOf(long microUsd);

    /**
     * Holds шаги for a run.
     *
     * @param what shown in the person's history, e.g. «Автопереклад «…», глави 3–5»
     * @return the hold, to be settled when the run ends
     * @throws space.panrid.novelka.platform.web.UserFacingException when the balance is short
     */
    long hold(long accountId, int shah, String what);

    /**
     * Charges what the run really cost, never more than was held, and returns the rest.
     * A settled hold stays settled: calling again changes nothing and returns the first charge.
     *
     * @return шаги charged
     */
    int settle(long holdId, long spentMicroUsd);
}
