package space.panrid.novelka.ledger.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.ledger.Ledger;
import space.panrid.novelka.platform.web.UserFacingException;

@RestController
class LedgerController {

    private final AccessPolicy access;
    private final LedgerService ledger;
    private final DSLContext db;

    LedgerController(AccessPolicy access, LedgerService ledger, DSLContext db) {
        this.access = access;
        this.ledger = ledger;
        this.db = db;
    }

    record Mine(int available, int reserved, BigDecimal usdPerShah, List<LedgerService.OpenHold> running,
            List<LedgerService.Entry> history, boolean hasMore) {
    }

    /** The «Шаги» page of anyone: what is left, what runs hold, what came and went. */
    @GetMapping("/api/me/shahs")
    Mine mine(@RequestParam(defaultValue = "1") int page) {
        Viewer viewer = access.requireSignedIn();
        Ledger.Balance balance = ledger.balance(viewer.accountId());
        List<LedgerService.Entry> history = ledger.history(viewer.accountId(), page);
        boolean more = history.size() > LedgerService.PAGE;
        return new Mine(balance.available(), balance.reserved(), usd(ledger.microUsdPerShah()), ledger.openHolds(viewer.accountId()),
                more ? history.subList(0, LedgerService.PAGE) : history, more);
    }

    record Grant(Integer shah, String note) {
    }

    record Granted(int available) {
    }

    @PostMapping("/api/admin/users/{nick}/shahs")
    @ResponseStatus(HttpStatus.CREATED)
    Granted grant(@PathVariable String nick, @RequestBody Grant body) {
        Viewer owner = access.requireSiteRole(SiteRole.OWNER);
        long accountId = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(Locale.ROOT)))
                .fetchOptional(ACCOUNT.ID).orElseThrow(() -> UserFacingException.notFound("Такої людини немає."));
        if (body.shah() == null) {
            throw UserFacingException.badRequest("Вкажіть, скільки шагів нарахувати.");
        }
        ledger.grant(accountId, body.shah(), body.note(), owner.accountId());
        return new Granted(ledger.balance(accountId).available());
    }

    record Price(BigDecimal usdPerShah) {
    }

    @GetMapping("/api/admin/shahs/price")
    Price price() {
        access.requireSiteRole(SiteRole.OWNER);
        return new Price(usd(ledger.microUsdPerShah()));
    }

    @PutMapping("/api/admin/shahs/price")
    void savePrice(@RequestBody Price body) {
        Viewer owner = access.requireSiteRole(SiteRole.OWNER);
        if (body.usdPerShah() == null) {
            throw UserFacingException.badRequest("Вкажіть ціну шагу.");
        }
        ledger.savePrice(body.usdPerShah().movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact(), owner.accountId());
    }

    private static BigDecimal usd(long micro) {
        return BigDecimal.valueOf(micro, 6).setScale(3, RoundingMode.HALF_UP);
    }
}
