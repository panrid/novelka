package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.comment.CommentRequest;
import panrid.space.novelka.server.comment.CommentService;
import panrid.space.novelka.server.moderation.HideRequest;

import java.security.Principal;
import java.util.Map;

@RestController
public final class CommentsController {
    private final AccessService access;
    private final CommentService comments;

    public CommentsController(AccessService access, CommentService comments) {
        this.access = access;
        this.comments = comments;
    }

    /** Public: anyone may read the discussion; the viewer only changes vote and permission flags. */
    @GetMapping("/api/novels/{novel}/comments")
    public Map<String, Object> list(Principal principal, @PathVariable String novel, @RequestParam(defaultValue = "0") int chapter,
            @RequestParam(defaultValue = "0") long before) throws Exception {
        return comments.page(novel, chapter, before, access.current(principal));
    }

    @PostMapping("/api/novels/{novel}/comments")
    public Map<String, Long> create(Principal principal, @PathVariable String novel, @RequestBody CommentRequest request) throws Exception {
        return Map.of("id", comments.create(access.require(principal, Role.READER), novel, request));
    }

    @PostMapping("/api/comments/{id}")
    public Map<String, String> edit(Principal principal, @PathVariable long id, @RequestBody CommentRequest request) throws Exception {
        comments.edit(access.require(principal, Role.READER), id, request);
        return Map.of("message", "Коментар змінено.");
    }

    @PostMapping("/api/comments/{id}/hide")
    public Map<String, String> hide(Principal principal, @PathVariable long id, @RequestBody HideRequest request) throws Exception {
        comments.hide(access.require(principal, Role.MODERATOR), id, true, request.normalized());
        return Map.of("message", "Коментар приховано.");
    }

    @PostMapping("/api/comments/{id}/unhide")
    public Map<String, String> unhide(Principal principal, @PathVariable long id) throws Exception {
        comments.hide(access.require(principal, Role.MODERATOR), id, false, "");
        return Map.of("message", "Коментар знову видно.");
    }

    @DeleteMapping("/api/comments/{id}")
    public Map<String, String> delete(Principal principal, @PathVariable long id) throws Exception {
        comments.delete(access.require(principal, Role.READER), id);
        return Map.of("message", "Коментар видалено.");
    }
}
