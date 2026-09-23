package panrid.space.novelka.server.task;

/**
 * Per-task execution choices that never change global settings. New overrides join this record;
 * a null or blank value means "use the site default".
 */
public record ExecutionOverrides(String model) {
    public boolean hasModel() { return model != null && !model.isBlank(); }
}
