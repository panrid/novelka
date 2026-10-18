package space.panrid.novelka.studio.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** «Видалити переклад» with a code from the email or Telegram, and «Кошик» to bring it back. */
@RestController
@RequestMapping("/api/studio")
class TrashController {

    private final EditionTrash trash;

    TrashController(EditionTrash trash) {
        this.trash = trash;
    }

    @PostMapping("/editions/{editionId}/delete-code")
    EditionTrash.Sent code(@PathVariable long editionId) {
        return trash.requestCode(editionId);
    }

    record Confirm(String code) {
    }

    @PostMapping("/editions/{editionId}/delete")
    void delete(@PathVariable long editionId, @RequestBody Confirm body) {
        trash.confirm(editionId, body.code());
    }

    @GetMapping("/trash")
    List<EditionTrash.Trashed> list() {
        return trash.list();
    }

    @PostMapping("/editions/{editionId}/restore")
    void restore(@PathVariable long editionId) {
        trash.restore(editionId);
    }
}
