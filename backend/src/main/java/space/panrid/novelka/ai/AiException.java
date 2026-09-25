package space.panrid.novelka.ai;

/** A call that gave no usable answer. The message is Ukrainian and may be shown to the owner. */
public class AiException extends RuntimeException {

    public enum Kind {
        /** Certainly not paid (not sent, rate limit, provider refused): safe to try again later. */
        UNPAID,
        /** Refused for a reason retrying will not fix: wrong key, no credits, bad request. */
        FAILED,
        /** Sent, but the answer was lost: it may be paid. Needs the owner's decision. */
        UNCERTAIN
    }

    private final Kind kind;

    public AiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
