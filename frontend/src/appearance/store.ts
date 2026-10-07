import { useSyncExternalStore } from 'react';
import { api } from '../api/client';
import { clean, readerColors, siteStyle, type Appearance, type ReaderAppearance, type SiteAppearance } from './model';

/**
 * The person's appearance settings: in this browser at once (so the page opens in its style,
 * see public/appearance-boot.js), and in the account when signed in, so every device agrees.
 */
const KEY = 'novelka:appearance';
const LEGACY_THEME = 'novelka:theme';
const LEGACY_SIZE = 'novelka:reader-size';

const listeners = new Set<() => void>();
let current: Appearance = load();
let signedIn = false;
let pending: ReturnType<typeof setTimeout> | undefined;
/** A change on its way to the account: an older copy of the account must not undo it. */
let unsent = false;
/** While the reader shows its own colours, the site's style waits under it. */
let readerOpen = false;

function load(): Appearance {
    try {
        const stored = localStorage.getItem(KEY);
        if (stored) return clean(JSON.parse(stored));
        // Before етап 18 the reader's theme was the site's theme, and the size lived apart.
        const legacy: Appearance = {};
        const theme = localStorage.getItem(LEGACY_THEME);
        if (theme === 'light') legacy.site = { preset: 'light' };
        if (theme === 'black') legacy.site = { preset: 'night' };
        const size = Number(localStorage.getItem(LEGACY_SIZE));
        const reader = size ? clean({ reader: { size } }).reader : undefined;
        if (reader) legacy.reader = reader;
        return clean(legacy);
    } catch {
        return {};
    }
}

function keep(next: Appearance) {
    current = next;
    try {
        localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
        // Private mode: the choice lasts until the page is closed.
    }
    apply();
    listeners.forEach((listener) => listener());
}

/** Puts the site's style (or, inside the reader, the reader's colours) on the page. */
export function apply() {
    const root = document.documentElement;
    const site = siteStyle(current);
    const reader = readerColors(current);
    const theme = readerOpen && reader.theme ? reader.theme : site.theme;
    root.dataset.style = site.value;
    root.dataset.theme = theme;
    const color = { dark: '#121412', light: '#f7f5ef', black: '#000000' }[theme];
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', color);
}

export function useAppearance(): Appearance {
    return useSyncExternalStore((listener) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
    }, () => current);
}

function changed(next: Appearance) {
    keep(next);
    if (!signedIn) return;
    clearTimeout(pending);
    // Steps of a slider become one request.
    unsent = true;
    pending = setTimeout(() => {
        void api('/api/me/appearance', { method: 'PUT', body: JSON.stringify(current) }).then(
            () => { unsent = false; },
            () => { unsent = false; }, // Offline: this browser keeps the choice and sends it with the next change.
        );
    }, 600);
}

export function setSite(patch: SiteAppearance) {
    changed({ ...current, site: { ...current.site, ...patch } });
}

export function setReader(patch: ReaderAppearance) {
    changed({ ...current, reader: { ...current.reader, ...patch } });
}

/**
 * The account has the last word: once signed in, its settings replace this browser's. An
 * account that never chose anything takes what this browser has, so nothing is lost.
 */
export function syncWithAccount(account: unknown | null) {
    signedIn = account !== null;
    if (!signedIn || unsent) return;
    const saved = clean(account);
    const hasAny = Boolean(saved.site || saved.reader);
    if (hasAny) {
        if (JSON.stringify(saved) !== JSON.stringify(current)) keep(saved);
    } else if (current.site || current.reader) {
        changed(current);
    }
}

/** The reader shows its own colours while it is open. */
export function readerOpened(open: boolean) {
    readerOpen = open;
    apply();
}
