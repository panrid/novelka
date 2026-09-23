package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.vote.VoteRequest;
import panrid.space.novelka.server.vote.VoteService;
import panrid.space.novelka.server.vote.VoteSummary;

import java.security.Principal;

@RestController
@RequestMapping("/api/votes")
public final class VotesController {
    private final AccessService access;
    private final VoteService votes;

    public VotesController(AccessService access, VoteService votes) {
        this.access = access;
        this.votes = votes;
    }

    @PostMapping("/{type}/{target}")
    public VoteSummary vote(Principal principal, @PathVariable String type, @PathVariable String target, @RequestBody VoteRequest request) throws Exception {
        return votes.vote(access.require(principal, Role.READER), type, target, request.value());
    }
}
