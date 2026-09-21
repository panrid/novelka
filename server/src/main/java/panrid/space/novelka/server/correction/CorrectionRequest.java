package panrid.space.novelka.server.correction;

public record CorrectionRequest(String novelId, int chapter, String baseJobId, int blockIndex,
        String original, String replacement, String reason) {
}
