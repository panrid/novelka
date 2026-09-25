package space.panrid.novelka.ai;

/** The wire to OpenRouter; tests put a fake model in its place. */
public interface AiTransport {

    record Reply(int status, String body) {
    }

    /** The request never left: nothing can be charged. */
    class NotSent extends Exception {
        public NotSent(String message) {
            super(message);
        }
    }

    /** The request may have reached the provider, but no complete answer came back. */
    class Lost extends Exception {
        public Lost(String message) {
            super(message);
        }
    }

    boolean configured();

    Reply chat(String body) throws NotSent, Lost;

    /** GET /models: the public catalogue with prices. */
    Reply models() throws NotSent, Lost;

    /** GET /credits with the management key; empty body if there is no such key. */
    Reply credits() throws NotSent, Lost;
}
