import { forgetQueuedProgress } from './progressQueue';

/**
 * What the phone keeps for one person must not reach the next one (етап 21, п. 12): the
 * service worker's API cache holds the account, its library and its places, so it goes when
 * the person signs out or another account signs in. Built files and the app shell stay.
 */
const ACCOUNT_KEY = 'novelka:offline-account';

export async function forgetPersonalData() {
    forgetQueuedProgress();
    if (typeof caches === 'undefined') return;
    try {
        const names = await caches.keys();
        await Promise.all(names.filter((name) => name.startsWith('novelka-api')).map((name) => caches.delete(name)));
    } catch {
        // Storage blocked: there is nothing kept either.
    }
}

function stored(): string | null {
    try {
        return localStorage.getItem(ACCOUNT_KEY);
    } catch {
        return null;
    }
}

/** Called with every answer to «who am I»: a different account than before forgets the kept data. */
export async function noteAccount(id: number | null) {
    const now = id === null ? 'guest' : String(id);
    if (stored() === now) return;
    await forgetPersonalData();
    try {
        localStorage.setItem(ACCOUNT_KEY, now);
    } catch {
        // Not remembered: the next answer forgets again, which is only safer.
    }
}
