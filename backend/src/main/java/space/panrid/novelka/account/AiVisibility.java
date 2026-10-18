package space.panrid.novelka.account;

/**
 * Whether a person sees the AI tools at all (етап 19, п. 7). The rule is the access module's; the
 * account only tells the web app, so it hides the buttons the server would refuse anyway.
 */
public interface AiVisibility {

    boolean seesAi(long accountId, SiteRole role);
}
