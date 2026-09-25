package space.panrid.novelka.community;

import java.util.List;

/**
 * A new comment. {@code replyToAuthorId} is who wrote the comment answered, if any.
 *
 * @param chapterNumber null for a comment on the translation as a whole
 */
public record CommentPosted(long commentId, long editionId, Integer chapterNumber, long authorId, Long replyToAuthorId,
        List<Long> mentionedAccounts, List<Long> mentionedTeams, String excerpt) {
}
