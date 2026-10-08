import { useSyncExternalStore } from 'react';
import { api } from '../api/client';
import { loadFont } from './fonts';
import {
    clean, presetLook, readerColors, readerLook, siteLook, sitePresetLook, siteStyle,
    type Appearance, type ReaderAppearance, type ReaderPreset, type SiteAppearance, type SiteStyle,
} from './model';
import { COLOR_VARS, siteVars } from './siteVars';

/**
 * The person's appearance settings: in this browser at once (so the page opens in its style,
 * see public/appearance-boot.js), and in the account when signed in, so every device agrees.
 */
const KEY = 'novelka:appearance';
const LEGACY_THEME = 'novelka:theme';
const LEGACY_SIZE = 'novelka:reader-size';
const VARS_KEY = 'novelka:appearance-css';

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

/**
 * Puts the site's look on the page as CSS variables — or, inside the reader with its own
 * palette, the reader's colours over the site's fonts and shapes.
 */
export function apply() {
    const root = document.documentElement;
    const site = siteLook(current);
    const style = siteStyle(current);
    const reader = readerColors(current);
    const own = readerOpen && reader.theme !== null;
    const theme = own && reader.theme ? reader.theme : style.theme;
    const vars = siteVars(site);
    for (const [name, value] of Object.entries(vars)) {
        if (own && COLOR_VARS.includes(name)) root.style.removeProperty(name);
        else root.style.setProperty(name, value);
    }
    const accent = readerOpen ? readerLook(current).accent : null;
    if (accent) root.style.setProperty('--accent', accent);
    root.dataset.style = site.preset;
    root.dataset.theme = theme;
    root.dataset.motion = site.anim;
    root.dataset.nav = site.nav;
    root.dataset.catalog = site.catalog;
    if (own) root.dataset.reader = reader.value;
    else delete root.dataset.reader;
    void loadFont(site.ui);
    void loadFont(site.head);
    if (readerOpen) void loadFont(readerLook(current).font);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', own ? reader.bg : style.color);
    try {
        // The next visit starts in this look before the app loads (public/appearance-boot.js).
        localStorage.setItem(VARS_KEY, JSON.stringify({ vars, theme: style.theme, preset: site.preset, motion: site.anim, nav: site.nav, catalog: site.catalog }));
    } catch {
        // Private mode: the default look until the app loads.
    }
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

/** A change to the site's look: from now on it is one's own («Свій стиль»). */
export function setSite(patch: SiteAppearance) {
    changed({ ...current, site: { ...current.site, ...patch, custom: true } });
}

/** A ready site style replaces whatever was changed before. */
export function setSitePreset(preset: SiteStyle) {
    changed({ ...current, site: sitePresetLook(preset) });
}

/** A change to the reader's look: from now on it is one's own («Свій стиль»). */
export function setReader(patch: ReaderAppearance) {
    changed({ ...current, reader: { ...current.reader, ...patch, custom: true } });
}

/** A ready reader style replaces whatever was changed before. */
export function setReaderPreset(preset: ReaderPreset) {
    changed({ ...current, reader: presetLook(preset) });
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
