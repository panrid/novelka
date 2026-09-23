package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.models.ModelCatalog;
import panrid.space.novelka.server.models.ModelCatalogService;

import java.security.Principal;

@RestController
@RequestMapping("/api/models")
public final class ModelsController {
    private final AccessService access;
    private final ModelCatalogService catalog;

    public ModelsController(AccessService access, ModelCatalogService catalog) {
        this.access = access;
        this.catalog = catalog;
    }

    @GetMapping
    public ModelCatalog list(Principal principal) throws Exception {
        access.require(principal, Role.ADMIN);
        return catalog.catalog(false);
    }

    @PostMapping("/refresh")
    public ModelCatalog refresh(Principal principal) throws Exception {
        access.require(principal, Role.OWNER);
        return catalog.catalog(true);
    }
}
