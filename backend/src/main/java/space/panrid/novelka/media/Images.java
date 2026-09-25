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

    /** Downloads a picture by an https link (safely, see RemoteImages) and stores it like an upload. */
    StoredImage storeFromUrl(long ownerAccountId, ImageKind kind, String url);

    /**
     * Stores a picture a model drew for a chapter, with what it was drawn from; no upload
     * limit, since the drawing itself is paid and rare.
     */
    StoredImage storeDrawn(long ownerAccountId, byte[] content, String prompt, String fragment, long aiCallId);

    /** URLs of a picture, unless moderators hid it. */
    Optional<StoredImage> find(long imageId);

    /** Several pictures at once (covers on a list page); hidden ones are left out. */
    Map<Long, StoredImage> findAll(Collection<Long> imageIds);
}
