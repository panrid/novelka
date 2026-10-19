/**
 * Webhooks (етап 22.5): «Я → Налаштування → API → Вебхуки». A person names an https address and
 * the events it wants — a chapter published, a run finished or stopped, a new suggestion, new
 * chapters in the original — and the site POSTs each one there, signed with the endpoint's
 * secret, retrying after pauses for about a day; every delivery is in the journal.
 */
@ApplicationModule(displayName = "Вебхуки")
package space.panrid.novelka.webhook;

import org.springframework.modulith.ApplicationModule;
