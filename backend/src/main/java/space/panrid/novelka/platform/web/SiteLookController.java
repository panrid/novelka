package space.panrid.novelka.platform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.platform.SiteLook;

/** The site's каркас and тема for everyone who has not chosen their own, guests included. */
@RestController
class SiteLookController {

    private final SiteLook look;

    SiteLookController(SiteLook look) {
        this.look = look;
    }

    @GetMapping("/api/site/look")
    SiteLook.Default look() {
        return look.current();
    }
}
