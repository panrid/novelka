package space.panrid.novelka.account;

/**
 * A way to reach a person besides email (Telegram), for news about their own account: a
 * changed nick, address or password, and the link for a new password. Implemented by the
 * messenger's module; does nothing for people who did not tie one.
 */
public interface AccountMessenger {

    /** Plain text, and a link under it if {@code link} is not null. Never throws. */
    void tell(long accountId, String text, String link);
}
