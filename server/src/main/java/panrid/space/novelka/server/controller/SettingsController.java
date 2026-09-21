package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.*;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.settings.SettingsService;
import panrid.space.novelka.server.settings.SiteSettings;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings")
public final class SettingsController {
    private final AccessService access;
    private final SettingsService service;

    public SettingsController(AccessService access, SettingsService service) { this.access = access; this.service = service; }

    @GetMapping
    public SiteSettings read(Principal principal) throws Exception {
        access.require(principal, Role.ADMIN);
        return service.read();
    }

    @PostMapping
    public SiteSettings save(Principal principal, @RequestBody SiteSettings request) throws Exception {
        return service.save(access.require(principal, Role.OWNER), request);
    }
}
