package panrid.space.novelka.server.task;

public record TaskRequest(String requestKey, String operation, String novelId, String url,
        int first, int last, String jobId, boolean force, boolean retryUncertain, double budgetUsd) {
}
