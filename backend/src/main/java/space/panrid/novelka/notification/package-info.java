/**
 * Inbox: one row per recipient for replies, mentions, team mentions, new chapters and
 * reviewed suggestions, built from other modules' events; and the live stream (SSE).
 */
@ApplicationModule(displayName = "Сповіщення")
package space.panrid.novelka.notification;

import org.springframework.modulith.ApplicationModule;
