package space.panrid.novelka.illustration.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.math.BigDecimal;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
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
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.platform.web.UserFacingException;

/** At launch only the site owner draws, and only in editions where they translate. */
@RestController
@RequestMapping("/api/studio")
class IllustrationController {

    private final AccessPolicy access;
    private final Illustrations illustrations;
    private final Catalog catalog;
    private final DSLContext db;

    IllustrationController(AccessPolicy access, Illustrations illustrations, Catalog catalog, DSLContext db) {
        this.access = access;
        this.illustrations = illustrations;
        this.catalog = catalog;
        this.db = db;
    }

    private Viewer drawer(long editionId) {
        Viewer viewer = access.requireSiteRole(SiteRole.OWNER);
        access.requireTranslator(editionId);
        return viewer;
    }

    private boolean showShah(Viewer viewer) {
        return db.select(ACCOUNT.SHOW_SHAH).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchSingle().value1();
    }

    record Price(BigDecimal usd, int shah, boolean showShah, String model) {
    }

    /** What one picture will cost, in шаги or dollars. */
    @GetMapping("/editions/{editionId}/illustrations/price")
    Price price(@PathVariable long editionId) {
        Viewer viewer = drawer(editionId);
        IllustrationSettings settings = illustrations.settings();
        return new Price(Illustrations.usd(settings.microUsdPerImage()), illustrations.shah(settings.microUsdPerImage()),
                showShah(viewer), settings.model());
    }

    record Fragment(String fragment) {
    }

    record Description(String prompt) {
    }

    @PostMapping("/editions/{editionId}/illustrations/prompt")
    Description describe(@PathVariable long editionId, @RequestBody Fragment body) {
        drawer(editionId);
        String title = catalog.edition(editionId).orElseThrow(() -> UserFacingException.notFound("Такої новели немає.")).title();
        return new Description(illustrations.describe(title, body.fragment()));
    }

    record DrawRequest(String prompt, String fragment, String aspect, Integer chapter) {
    }

    @PostMapping("/editions/{editionId}/illustrations")
    @ResponseStatus(HttpStatus.CREATED)
    Illustrations.Drawn draw(@PathVariable long editionId, @RequestBody DrawRequest body) {
        Viewer viewer = drawer(editionId);
        return illustrations.draw(viewer.accountId(), body.prompt(), body.fragment(), body.aspect() == null ? "3:4" : body.aspect(),
                body.chapter());
    }

    record Overview(IllustrationSettings settings, Illustrations.Spent spent) {
    }

    @GetMapping("/illustrations/settings")
    Overview settings(@RequestParam(defaultValue = "30") int days) {
        access.requireSiteRole(SiteRole.OWNER);
        return new Overview(illustrations.settings(), illustrations.spent(Math.max(1, Math.min(days, 365))));
    }

    @PutMapping("/illustrations/settings")
    void saveSettings(@RequestBody IllustrationSettings settings) {
        Viewer viewer = access.requireSiteRole(SiteRole.OWNER);
        illustrations.saveSettings(settings, viewer.accountId());
    }
}
