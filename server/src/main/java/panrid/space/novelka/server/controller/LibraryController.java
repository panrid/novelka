package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.library.LibraryItem;
import panrid.space.novelka.server.library.LibraryRequest;
import panrid.space.novelka.server.library.LibraryService;
import panrid.space.novelka.server.library.LibraryStatus;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/library")
public final class LibraryController {
    private final AccessService access;
    private final LibraryService library;

    public LibraryController(AccessService access, LibraryService library) {
        this.access = access;
        this.library = library;
    }

    @GetMapping
    public ListPage<LibraryItem> list(Principal principal, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String sort, @RequestParam(defaultValue = "desc") String direction) throws Exception {
        return library.list(access.require(principal, Role.READER), status, new ListQuery(page, size, q, sort, direction));
    }

    @GetMapping("/counts")
    public Map<String, Long> counts(Principal principal) throws Exception {
        return library.counts(access.require(principal, Role.READER));
    }

    @PostMapping("/{novel}")
    public LibraryStatus set(Principal principal, @PathVariable String novel, @RequestBody LibraryRequest request) throws Exception {
        return library.set(access.require(principal, Role.READER), novel, request.status());
    }
}
