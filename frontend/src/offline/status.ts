import { useSyncExternalStore } from 'react';

/**
 * Whether the site works from what the phone kept (етап 21, п. 12): the browser says it is
 * offline, or the last API answer came from the service worker's cache (public/sw.js marks it
 * with X-Novelka-Cache: offline). The first fresh answer ends it.
 */
type State = { online: boolean; fromCache: boolean };

let state: State = { online: typeof navigator === 'undefined' || navigator.onLine !== false, fromCache: false };
const listeners = new Set<() => void>();

function set(next: Partial<State>) {
    const merged = { ...state, ...next };
    if (merged.online === state.online && merged.fromCache === state.fromCache) return;
    state = merged;
    listeners.forEach((listener) => listener());
}

/** Every API answer reports here whether it was a kept one. */
export function noteResponse(fromCache: boolean) {
    set(fromCache ? { fromCache } : { fromCache, online: true });
}

function subscribe(listener: () => void) {
    listeners.add(listener);
    const online = () => set({ online: true });
    const offline = () => set({ online: false });
    window.addEventListener('online', online);
    window.addEventListener('offline', offline);
    return () => {
        listeners.delete(listener);
        window.removeEventListener('online', online);
        window.removeEventListener('offline', offline);
    };
}

export function isOffline(): boolean {
    return !state.online || state.fromCache;
}

export function useOffline(): boolean {
    return useSyncExternalStore(subscribe, isOffline, () => false);
}
