/**
 * Calls to language models through OpenRouter. Every call is written down before it is
 * sent, so money is never spent twice for the same answer and a lost answer is never
 * retried blindly.
 */
@ApplicationModule(displayName = "ШІ")
package space.panrid.novelka.ai;

import org.springframework.modulith.ApplicationModule;
