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

    /** URLs of a picture, unless moderators hid it. */
    Optional<StoredImage> find(long imageId);

    /** Several pictures at once (covers on a list page); hidden ones are left out. */
    Map<Long, StoredImage> findAll(Collection<Long> imageIds);
}
