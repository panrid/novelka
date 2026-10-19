import { ApiError } from '../api/client';

/**
 * Errors in the reader's browser go to the server's log (план, етап 23, п. 4): uncaught errors,
 * rejected promises nobody handled, and pages that fell in React. A few per page at most, each
 * message once; an answer the server gave on purpose (4xx) is not an error of the site.
 */
type Kind = 'error' | 'rejection' | 'render';

const MAX_PER_PAGE = 10;
const sent = new Set<string>();

/** The build: the bundle's own hashed file name changes with every release. */
function version(): string {
    try {
        return new URL(import.meta.url).pathname.split('/').pop() ?? 'dev';
    } catch {
        return 'dev';
    }
}

export function reportError(kind: Kind, error: unknown) {
    if (error instanceof ApiError && error.status < 500) return;
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    if (sent.size >= MAX_PER_PAGE || sent.has(message)) return;
    sent.add(message);
    const body = JSON.stringify({
        kind, message, stack: error instanceof Error ? error.stack ?? '' : '',
        url: window.location.pathname, version: version(),
    });
    try {
        // keepalive: the report still leaves when the error is followed by leaving the page.
        void fetch('/api/client-errors', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true })
            .catch(() => undefined);
    } catch {
        // Reporting must never break the page further.
    }
}

/** Once, at start: every uncaught error and unhandled rejection is reported. */
export function watchClientErrors() {
    window.addEventListener('error', (event) => reportError('error', event.error ?? event.message));
    window.addEventListener('unhandledrejection', (event) => reportError('rejection', event.reason));
}

/** Forgets what was sent: for tests only. */
export function resetReported() {
    sent.clear();
}
