package space.panrid.novelka.community;

/** What moderators do to comments and chat lines; the admin module decides who may. */
public interface CommunityModeration {

    void hideComment(long commentId, long moderatorId, String reason);

    void restoreComment(long commentId);

    void hideChat(long lineId, long moderatorId, String reason);

    void restoreChat(long lineId);
}
