package panrid.space.novelka.server.correction;

/** Replace every occurrence of {@code find} in one chapter ({@code scope=chapter}) or in all published chapters ({@code novel}). */
public record ReplaceRequest(String novelId, int chapter, String baseJobId, String find, String replacement, String scope, String reason) {
}
