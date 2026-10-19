/**
 * Starts the service worker (public/sw.js) in the built site; development and tests go without.
 *
 * Updates: the worker takes over as soon as a new one is installed (skipWaiting), and pages are
 * always loaded from the network first, so a deploy reaches people on their next page load
 * without a prompt; offline, the last kept version opens. A tab left open for a long time asks
 * the server for a new worker when it is looked at again, at most once an hour.
 */
const CHECK_EVERY_MS = 60 * 60_000;

export function registerServiceWorker() {
    if (!import.meta.env.PROD || typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').then((registration) => {
            let checkedAt = Date.now();
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState !== 'visible' || Date.now() - checkedAt < CHECK_EVERY_MS) return;
                checkedAt = Date.now();
                registration.update().catch(() => undefined);
            });
        }, () => {
            // No worker (private mode, blocked): the site works as before, only not offline.
        });
    });
}
