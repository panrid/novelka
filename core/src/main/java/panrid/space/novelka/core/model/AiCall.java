package panrid.space.novelka.core.model;

public record AiCall(
        String id,
        String jobId,
        String stage,
        int segment,
        String model,
        String promptVersion,
        long glossaryRevision,
        String contextJson,
        double estimateUsd,
        String state) {
}
