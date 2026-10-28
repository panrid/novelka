package space.panrid.novelka.media;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Saving pictures and finding their URLs. */
public interface Images {

    /**
     * Checks, crops, resizes and saves an uploaded picture.
     *
     * @throws space.panrid.novelka.platform.web.UserFacingException with a reason people understand
     */
    StoredImage store(long ownerAccountId, ImageKind kind, byte[] content);

    /**
     * Stores an upload like {@link #store} and remembers the file's name: «Мої картинки» show it,
     * and «27-5.png» tells where the picture goes (chapter 27, after the fifth paragraph).
     */
    StoredImage store(long ownerAccountId, ImageKind kind, byte[] content, String fileName);

    /** Downloads a picture by an https link (safely, see RemoteImages) and stores it like an upload. */
    StoredImage storeFromUrl(long ownerAccountId, ImageKind kind, String url);

    /**
     * Stores a picture a model drew for a chapter, with what it was drawn from; no upload
     * limit, since the drawing itself is paid and rare.
     */
    StoredImage storeDrawn(long ownerAccountId, byte[] content, String prompt, String fragment, long aiCallId);

    /** Reject using someone else's private upload or a file bound to another workspace. */
    void requireUsable(long imageId, long editionId, Long accountId);

    /** Permanently bind a working upload to this workspace; removal never makes it public. */
    void bindWorkspace(long imageId, long editionId, long accountId);

    /** A moderator hides a picture everywhere it is used. */
    void hide(long imageId, long moderatorId, String reason);

    void restore(long imageId);

    /** URLs of a picture, unless moderators hid it. */
    Optional<StoredImage> find(long imageId);

    /** A picture file to put into a book: the copy at least {@code width} wide (or the largest), unless hidden. */
    Optional<ImageFile> file(long imageId, int width);

    /** Original upload bytes for a lossless archive; legacy uploads fall back to the largest stored variant. */
    Optional<byte[]> original(long imageId);

    /** Several pictures at once (covers on a list page); hidden ones are left out. */
    Map<Long, StoredImage> findAll(Collection<Long> imageIds);
}
