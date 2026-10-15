package space.panrid.novelka.studio.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.Viewer;

/**
 * «Мої картинки»: one's own illustrations. Anyone signed in sees and uploads their own;
 * placing them into a novel needs the right to add pictures there (owner, translator).
 */
@RestController
class PictureController {

    record PlacementRequest(Long editionId, List<Long> imageIds) {
    }

    private final AccessPolicy access;
    private final PictureLibrary library;

    PictureController(AccessPolicy access, PictureLibrary library) {
        this.access = access;
        this.library = library;
    }

    @GetMapping("/api/me/pictures")
    PictureLibrary.Page pictures(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "1") int page) {
        Viewer viewer = access.requireSignedIn();
        return library.page(viewer.accountId(), filter, page);
    }

    @DeleteMapping("/api/me/pictures/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long imageId) {
        Viewer viewer = access.requireSignedIn();
        library.remove(viewer.accountId(), imageId);
    }

    @PostMapping("/api/me/pictures/placement/preview")
    List<PictureLibrary.Place> preview(@RequestBody PlacementRequest body) {
        EditionAccess who = placer(body);
        return library.plan(who.viewer().accountId(), who.editionId(), body.imageIds());
    }

    @PostMapping("/api/me/pictures/placement")
    PictureLibrary.Placed place(@RequestBody PlacementRequest body) {
        EditionAccess who = placer(body);
        return library.place(who.viewer().accountId(), who.editionId(), body.imageIds());
    }

    private EditionAccess placer(PlacementRequest body) {
        if (body.editionId() == null) {
            throw space.panrid.novelka.platform.web.UserFacingException.badRequest("Виберіть новелу.");
        }
        return access.requireTranslator(body.editionId());
    }
}
