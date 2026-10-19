import type { QueryClient } from '@tanstack/react-query';
import { chapterQuery } from '../reading/queries';

/** Chapters loaded ahead while reading: turning the page is instant, and they are there offline. */
export const AHEAD = 3;

type Connection = { saveData?: boolean };

/** How many chapters to load ahead now: none offline, one when the phone asks to save data. */
export function aheadCount(): number {
    if (typeof navigator === 'undefined') return AHEAD;
    if (navigator.onLine === false) return 0;
    const connection = (navigator as Navigator & { connection?: Connection }).connection;
    return connection?.saveData ? 1 : AHEAD;
}

/** Loaded ahead chapters stay fresh this long: going back and forth does not load them again. */
const AHEAD_FRESH_MS = 5 * 60_000;

/**
 * Loads the chapters after the open one, one by one (each says which comes next), up to
 * `count` of them. The service worker keeps what comes over the network, so these chapters
 * open without the internet too. Stops at the last chapter or at the first failure.
 * Returns the numbers it loaded.
 */
export async function prefetchAhead(client: QueryClient, slug: string, team: string | undefined,
    next: number | null | undefined, count: number = aheadCount()): Promise<number[]> {
    const loaded: number[] = [];
    let number = next;
    while (number && loaded.length < count) {
        try {
            const chapter = await client.fetchQuery({ ...chapterQuery(slug, number, team), staleTime: AHEAD_FRESH_MS, retry: false });
            loaded.push(number);
            number = chapter.next;
        } catch {
            break;
        }
    }
    return loaded;
}
