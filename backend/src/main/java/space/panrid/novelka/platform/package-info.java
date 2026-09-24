/**
 * Shared infrastructure: security, API errors, mail, rate limits, serving the web app.
 * Open module: feature modules use its sub-packages directly; it depends on none of them.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Платформа")
package space.panrid.novelka.platform;

import org.springframework.modulith.ApplicationModule;
