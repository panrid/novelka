package space.panrid.novelka.webhook.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.security.TokenGrant;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.webhook.internal.Webhooks.Created;
import space.panrid.novelka.webhook.internal.Webhooks.DeliveryView;
import space.panrid.novelka.webhook.internal.Webhooks.EndpointView;
import space.panrid.novelka.webhook.internal.Webhooks.Overview;

/**
 * «Налаштування → API → Вебхуки» (етап 22.5). Only with the session, like the tokens: a token
 * could otherwise send the person's events anywhere.
 */
@RestController
class WebhookController {

    /** @param editionIds the translations to hear about; empty — every one the person may */
    record NewEndpoint(String url, List<String> events, List<Long> editionIds) {
    }

    /** What changes; null keeps it. */
    record EndpointChange(List<String> events, List<Long> editionIds, Boolean enabled) {
    }

    private final Webhooks webhooks;
    private final AccessPolicy access;

    WebhookController(Webhooks webhooks, AccessPolicy access) {
        this.webhooks = webhooks;
        this.access = access;
    }

    @GetMapping("/api/me/webhooks")
    Overview mine(HttpServletRequest request) {
        return webhooks.overview(signedInBySession(request));
    }

    @PostMapping("/api/me/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    Created create(@RequestBody NewEndpoint body, HttpServletRequest request) {
        return webhooks.create(signedInBySession(request), body.url(), body.events(), body.editionIds());
    }

    @PatchMapping("/api/me/webhooks/{id}")
    EndpointView update(@PathVariable long id, @RequestBody EndpointChange body, HttpServletRequest request) {
        return webhooks.update(signedInBySession(request), id, body.events(), body.editionIds(), body.enabled());
    }

    @DeleteMapping("/api/me/webhooks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, HttpServletRequest request) {
        webhooks.delete(signedInBySession(request), id);
    }

    @PostMapping("/api/me/webhooks/{id}/test")
    DeliveryView test(@PathVariable long id, HttpServletRequest request) {
        return webhooks.test(signedInBySession(request), id);
    }

    @GetMapping("/api/me/webhooks/{id}/deliveries")
    List<DeliveryView> deliveries(@PathVariable long id, HttpServletRequest request) {
        return webhooks.deliveries(signedInBySession(request), id);
    }

    @PostMapping("/api/me/webhooks/{id}/deliveries/{deliveryId}/retry")
    DeliveryView retry(@PathVariable long id, @PathVariable long deliveryId, HttpServletRequest request) {
        return webhooks.retry(signedInBySession(request), id, deliveryId);
    }

    private Viewer signedInBySession(HttpServletRequest request) {
        if (TokenGrant.of(request) != null) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Це можна зробити лише на сайті, не токеном.");
        }
        return access.requireSignedIn();
    }
}
