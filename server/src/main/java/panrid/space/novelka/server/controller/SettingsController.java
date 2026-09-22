package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.*;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.settings.SettingsService;
import panrid.space.novelka.server.settings.SiteSettings;
import panrid.space.novelka.server.settings.OpenRouterCredits;
import panrid.space.novelka.server.settings.OpenRouterCreditsService;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings")
public final class SettingsController {
    private final AccessService access;
    private final SettingsService service;
    private final OpenRouterCreditsService credits;

    public SettingsController(AccessService access, SettingsService service, OpenRouterCreditsService credits) {
        this.access = access;
        this.service = service;
        this.credits = credits;
    }

    @GetMapping("/openrouter-credits")
    public OpenRouterCredits credits(Principal principal) throws Exception {
        access.require(principal, Role.OWNER);
        return credits.read();
    }

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
