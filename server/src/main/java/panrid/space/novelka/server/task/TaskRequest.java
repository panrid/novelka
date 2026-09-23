package panrid.space.novelka.server.task;

public record TaskRequest(String requestKey, String operation, String novelId, String url,
        int first, int last, String jobId, boolean force, boolean retryUncertain, double budgetUsd, Integer dictionarySearchLimit,
        ExecutionOverrides overrides) {
    public TaskRequest {
        if (dictionarySearchLimit == null) dictionarySearchLimit = 6;
    }

    public TaskRequest(String requestKey, String operation, String novelId, String url,
            int first, int last, String jobId, boolean force, boolean retryUncertain, double budgetUsd, Integer dictionarySearchLimit) {
        this(requestKey, operation, novelId, url, first, last, jobId, force, retryUncertain, budgetUsd, dictionarySearchLimit, null);
    }

    public TaskRequest(String requestKey, String operation, String novelId, String url,
            int first, int last, String jobId, boolean force, boolean retryUncertain, double budgetUsd) {
        this(requestKey, operation, novelId, url, first, last, jobId, force, retryUncertain, budgetUsd, 6);
    }
}
