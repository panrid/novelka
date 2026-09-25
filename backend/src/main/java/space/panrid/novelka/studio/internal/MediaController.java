package space.panrid.novelka.studio.internal;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Pictures for the editor and covers. Uploading only stores the picture; putting it into
 * a chapter or on an edition is checked where that happens (team rights).
 */
@RestController
class MediaController {

    record LinkRequest(String url, String kind) {
    }

    record Uploaded(long id, String url) {
    }

    private final Images images;
    private final CurrentUser currentUser;

    MediaController(Images images, CurrentUser currentUser) {
        this.images = images;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/media/images")
    @ResponseStatus(HttpStatus.CREATED)
    Uploaded upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "illustration") String kind) {
        Viewer viewer = currentUser.requireSignedIn();
        try {
            return uploaded(images.store(viewer.accountId(), kind(kind), file.getBytes()), kind(kind));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @PostMapping("/api/media/images/from-url")
    @ResponseStatus(HttpStatus.CREATED)
    Uploaded fromUrl(@RequestBody LinkRequest body) {
        Viewer viewer = currentUser.requireSignedIn();
        ImageKind kind = kind(body.kind() == null ? "illustration" : body.kind());
        return uploaded(images.storeFromUrl(viewer.accountId(), kind, body.url()), kind);
    }

    private static Uploaded uploaded(StoredImage image, ImageKind kind) {
        return new Uploaded(image.id(), image.url(switch (kind) {
            case COVER -> 480;
            case GROUP_AVATAR, AVATAR -> 256;
            default -> 1280;
        }));
    }

    private static ImageKind kind(String code) {
        return switch (code) {
            case "illustration" -> ImageKind.ILLUSTRATION;
            case "cover" -> ImageKind.COVER;
            case "message" -> ImageKind.MESSAGE;
            case "group_avatar" -> ImageKind.GROUP_AVATAR;
            default -> throw UserFacingException.badRequest("Невідомий вид картинки.");
        };
    }
}
