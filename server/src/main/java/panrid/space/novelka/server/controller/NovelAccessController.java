package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.novel.EditorRequest;
import panrid.space.novelka.server.novel.NovelAccessService;
import panrid.space.novelka.server.novel.NovelEditors;
import panrid.space.novelka.server.novel.OpenReviewRequest;

import java.security.Principal;

/** The translator (or an administrator) chooses who decides on the novel's corrections. */
@RestController
@RequestMapping("/api/manage/{novel}")
public final class NovelAccessController {
    private final AccessService access;
    private final NovelAccessService novels;

    public NovelAccessController(AccessService access, NovelAccessService novels) {
        this.access = access;
        this.novels = novels;
    }

    @GetMapping("/editors")
    public NovelEditors editors(Principal principal, @PathVariable String novel) throws Exception {
        return novels.editors(access.require(principal, Role.READER), novel);
    }

    @PostMapping("/editors")
    public NovelEditors add(Principal principal, @PathVariable String novel, @RequestBody EditorRequest request) throws Exception {
        return novels.addEditor(access.require(principal, Role.READER), novel, request.accountId());
    }

    @DeleteMapping("/editors/{account}")
    public NovelEditors remove(Principal principal, @PathVariable String novel, @PathVariable String account) throws Exception {
        return novels.removeEditor(access.require(principal, Role.READER), novel, account);
    }

    @PostMapping("/review-access")
    public NovelEditors openReview(Principal principal, @PathVariable String novel, @RequestBody OpenReviewRequest request) throws Exception {
        return novels.openReview(access.require(principal, Role.READER), novel, request.open());
    }
}
