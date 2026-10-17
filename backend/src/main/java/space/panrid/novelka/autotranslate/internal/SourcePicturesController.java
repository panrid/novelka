package space.panrid.novelka.autotranslate.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.Viewer;

/** «Картинки з оригіналу»: what published chapters lack, then chapter by chapter, so the page shows the progress. */
@RestController
@RequestMapping("/api/studio/editions/{editionId}/source-pictures")
class SourcePicturesController {

    private final AccessPolicy access;
    private final SourcePictures pictures;

    SourcePicturesController(AccessPolicy access, SourcePictures pictures) {
        this.access = access;
        this.pictures = pictures;
    }

    /** Taken from the original's site: the owner's and of those the owner let in, as autotranslate is. */
    private Viewer owner(long editionId) {
        Viewer viewer = access.requireAutotranslate();
        access.requireTranslator(editionId);
        return viewer;
    }

    @GetMapping
    List<SourcePictures.Missing> missing(@PathVariable long editionId) {
        owner(editionId);
        return pictures.missing(editionId);
    }

    @PostMapping("/{number}")
    SourcePictures.Filled fill(@PathVariable long editionId, @PathVariable int number) {
        return pictures.fill(editionId, number, owner(editionId).accountId());
    }
}
