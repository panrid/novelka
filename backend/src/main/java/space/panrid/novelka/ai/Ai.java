package space.panrid.novelka.ai;

import java.util.Optional;

/** Language models. Calls take minutes; never call them inside a database transaction. */
public interface Ai {

    /**
     * Sends the request, or returns the saved answer to exactly the same request.
     *
     * @throws AiException with {@link AiException.Kind} telling whether trying again is safe
     */
    AiAnswer ask(AiRequest request);

    /**
     * Draws a picture. Every call is a new picture: an identical request is not answered
     * from the journal, since «ще варіант» asks for exactly that.
     */
    AiPicture image(AiImageRequest request);

    /** Money spent by a job, in millionths of a dollar; an answer that may be lost counts at its estimate. */
    long spentMicroUsd(long jobId);

    /** Credits left at OpenRouter, if the management key is configured and OpenRouter answers. */
    Optional<AiCredits> credits();

    /** OpenRouter's catalogue of models with prices, kept for an hour; empty if it cannot be read. */
    java.util.List<AiModel> models();

    /** Whether a key is configured at all. */
    boolean configured();

    /** The owner allowed new attempts after answers were lost (they may already be paid for). */
    void forgetUncertain(long jobId);
}
