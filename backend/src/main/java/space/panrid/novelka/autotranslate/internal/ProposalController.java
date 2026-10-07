package space.panrid.novelka.autotranslate.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.Teams;

/** «Що перекласти»: everyone sees the list; voting and proposing need an account. */
@RestController
@RequestMapping("/api/proposals")
class ProposalController {

    private final Proposals proposals;
    private final CurrentUser currentUser;
    private final AccessPolicy access;
    private final Teams teams;

    ProposalController(Proposals proposals, CurrentUser currentUser, AccessPolicy access, Teams teams) {
        this.proposals = proposals;
        this.currentUser = currentUser;
        this.access = access;
        this.teams = teams;
    }

    @GetMapping
    Proposals.Page list(@RequestParam(defaultValue = "votes") String sort, @RequestParam(defaultValue = "open") String state,
            @RequestParam(defaultValue = "1") int page) {
        return proposals.page(currentUser.viewer().map(Viewer::accountId).orElse(null), sort, state, page);
    }

    /** A link, or a name, or both; a description and a comment if the person likes. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Proposals.Proposed propose(@RequestBody Proposals.Proposal body) {
        return proposals.propose(currentUser.requireSignedIn(), body);
    }

    /** @param comment null keeps the comment as it was */
    record Change(String title, String author, String description, String comment) {
    }

    @PutMapping("/{id}")
    void update(@PathVariable long id, @RequestBody Change body) {
        proposals.update(currentUser.requireSignedIn(), id, body.title(), body.author(), body.description(), body.comment());
    }

    @DeleteMapping("/{id}")
    void remove(@PathVariable long id) {
        proposals.remove(currentUser.requireSignedIn(), id);
    }

    @PostMapping("/{id}/vote")
    void vote(@PathVariable long id) {
        proposals.vote(currentUser.requireSignedIn(), id);
    }

    @DeleteMapping("/{id}/vote")
    void unvote(@PathVariable long id) {
        proposals.unvote(currentUser.requireSignedIn(), id);
    }

    /** @param team handle of the team; empty means the personal one */
    record TakeRequest(String team) {
    }

    record Taken(long editionId, String novelSlug) {
    }

    @PostMapping("/{id}/take")
    Taken take(@PathVariable long id, @RequestBody TakeRequest body) {
        Viewer viewer = currentUser.requireSignedIn();
        long teamId = body.team() == null || body.team().isBlank()
                ? teams.personalTeam(viewer.accountId())
                : teams.findByHandle(body.team()).orElseThrow(() -> UserFacingException.notFound("Такої команди немає.")).id();
        access.requireTeamTranslator(teamId);
        EditionRef ref = proposals.take(viewer, id, teamId);
        return new Taken(ref.editionId(), ref.novelSlug());
    }
}
