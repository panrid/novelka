package space.panrid.novelka.webhook;

/** The site stopped sending to a webhook that kept failing; its owner hears in «Вхідні». */
public record WebhookDisabled(long accountId, long endpointId, String url, int failures) {
}
