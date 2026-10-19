/**
 * Access tokens for developers and agents (етап 22.1): «Я → Налаштування → API». A token signs a
 * request in as its owner — never more than the owner may do, and its scopes narrow that further;
 * every change made with one is journaled.
 */
@ApplicationModule(displayName = "Токени API")
package space.panrid.novelka.apitoken;

import org.springframework.modulith.ApplicationModule;
