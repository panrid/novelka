import type { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/client';

/**
 * The reading place a signed-in reader reached offline (етап 21, п. 12): kept in this browser,
 * one entry per account and translation (the latest place wins), and sent once the network is
 * back. Only the account that read it sends it; signing out forgets the queue.
 */
export type QueuedProgress = { account: number; editionId: number; chapterNumber: number; position: number; at: number };

const KEY = 'novelka:offline-progress';

export function queuedProgress(): QueuedProgress[] {
    try {
        const raw = localStorage.getItem(KEY);
        const parsed: unknown = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? (parsed as QueuedProgress[]) : [];
    } catch {
        return [];
    }
}

function write(entries: QueuedProgress[]) {
    try {
        if (entries.length) localStorage.setItem(KEY, JSON.stringify(entries));
        else localStorage.removeItem(KEY);
    } catch {
        // Storage blocked or full: the place is only lost if the network never comes back.
    }
}

const same = (a: { account: number; editionId: number }, b: { account: number; editionId: number }) =>
    a.account === b.account && a.editionId === b.editionId;

/** Keeps the place to send later; an older one for the same translation is replaced. */
export function queueProgress(entry: QueuedProgress) {
    write([...queuedProgress().filter((queued) => !same(queued, entry)), entry]);
}

/** A newer place reached the server: the queued one for that translation must not overwrite it. */
export function dropQueuedProgress(account: number, editionId: number) {
    const entries = queuedProgress();
    const left = entries.filter((queued) => !same(queued, { account, editionId }));
    if (left.length !== entries.length) write(left);
}

export function forgetQueuedProgress() {
    write([]);
}

/** Whether a failed send is worth another try: no network or a server trouble, not a refusal. */
const worthRetry = (error: unknown) => !(error instanceof ApiError) || error.status === 0 || error.status >= 500;

let flushing: Promise<number> | null = null;

/**
 * Sends the account's queued places, oldest first. What the server refused (the translation is
 * gone, the session ended) is dropped; what met no network stays for the next time.
 * Returns how many reached the server.
 */
export function flushProgress(account: number, send: (entry: QueuedProgress) => Promise<unknown>): Promise<number> {
    flushing ??= (async () => {
        let sent = 0;
        const mine = queuedProgress().filter((entry) => entry.account === account).sort((a, b) => a.at - b.at);
        for (const entry of mine) {
            try {
                await send(entry);
                sent += 1;
            } catch (error) {
                if (worthRetry(error)) break;
            }
            // Read again: a newer place may have been queued (or sent) meanwhile.
            write(queuedProgress().filter((queued) => !(same(queued, entry) && queued.at === entry.at)));
        }
        return sent;
    })().finally(() => {
        flushing = null;
    });
    return flushing;
}

/**
 * Sends the queue when the app starts, when the network comes back and when the page is looked
 * at again; pages that show the place are refreshed after.
 */
export function watchProgressQueue(client: QueryClient, account: () => number | null, send: (entry: QueuedProgress) => Promise<unknown>) {
    const flush = () => {
        const id = account();
        if (id === null || !queuedProgress().some((entry) => entry.account === id)) return;
        void flushProgress(id, send).then((sent) => {
            if (!sent) return;
            for (const key of [['novel'], ['home'], ['library'], ['chapter']]) {
                void client.invalidateQueries({ queryKey: key });
            }
        });
    };
    window.addEventListener('online', flush);
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') flush();
    });
    // The account may still be loading at start: try again once it surely has.
    setTimeout(flush, 3_000);
    return flush;
}
