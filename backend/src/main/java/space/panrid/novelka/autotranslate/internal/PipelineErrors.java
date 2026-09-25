package space.panrid.novelka.autotranslate.internal;

/** Reasons a step stops that are not the model provider's. Messages are for the owner. */
final class PipelineErrors {

    private PipelineErrors() {
    }

    /** The model kept answering with missing or extra paragraphs. */
    static final class BadOutput extends RuntimeException {
        BadOutput(String message) {
            super(message);
        }
    }

    static final class OverBudget extends RuntimeException {
        OverBudget(String message) {
            super(message);
        }
    }

    static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("cancelled");
        }
    }
}
