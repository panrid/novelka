import { useSyncExternalStore } from 'react';

const QUERY = '(min-width: 1024px)';

function subscribe(onChange: () => void) {
    const list = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(QUERY) : null;
    list?.addEventListener('change', onChange);
    return () => list?.removeEventListener('change', onChange);
}

/** A laptop-wide screen (the same 1024 px the stylesheets switch at). */
export function useWide(): boolean {
    return useSyncExternalStore(subscribe, () => typeof window !== 'undefined' && Boolean(window.matchMedia?.(QUERY).matches), () => false);
}
