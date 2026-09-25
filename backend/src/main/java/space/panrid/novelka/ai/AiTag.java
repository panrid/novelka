package space.panrid.novelka.ai;

/** What a call was for, for the journal and the cost report. */
public record AiTag(Long jobId, Integer chapterNumber, String stage, Integer segment) {
}
