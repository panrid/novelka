import { useSyncExternalStore } from 'react';
import { api } from '../api/client';
import { loadFont } from './fonts';
import {
    SITE_DEFAULT,
    clean, isLight, ownReaderVars, presetLook, readerColors, readerLook, siteLook,
    type Appearance, type OwnColors, type ReaderAppearance, type ReaderPreset, type SiteAppearance, type SiteDefault, type SiteLook,
} from './model';
import { COLOR_VARS, siteVars } from './siteVars';
import { FRAMES, THEMES, themeVars } from './themes';

/**
 * The person's appearance settings: in this browser at once (so the page opens in its look,
 * see public/appearance-boot.js), and in the account when signed in, so every device agrees.
 * What they never chose follows the site's каркас and тема (/api/site/look), kept here too.
 */
const KEY = 'novelka:appearance';
const SITE_KEY = 'novelka:site-look';
const LEGACY_THEME = 'novelka:theme';
const LEGACY_SIZE = 'novelka:reader-size';
const VARS_KEY = 'novelka:appearance-css';

const listeners = new Set<() => void>();
let current: Appearance = load();
let siteDefault: SiteDefault = loadSiteDefault();
let signedIn = false;
let pending: ReturnType<typeof setTimeout> | undefined;
/** A change on its way to the account: an older copy of the account must not undo it. */
let unsent = false;
/** While the reader shows its own colours, the site's style waits under it. */
let readerOpen = false;
/** «Як у системі»: the device's light or dark. */
const system = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: light)') : null;

/** The site's look as it shows, light or dark right now, and the site's own choice. */
export type SiteNow = { look: SiteLook; light: boolean; siteDefault: SiteDefault };
let now: SiteNow = site();

system?.addEventListener?.('change', () => {
    if (now.look.mode === 'system') {
        apply();
        listeners.forEach((listener) => listener());
    }
});

function site(): SiteNow {
    const look = siteLook(current, siteDefault);
    return { look, light: isLight(look, system?.matches ?? false), siteDefault };
}

function load(): Appearance {
    try {
        // A look saved before #292 (one site style) becomes a тема here (model.ts clean).
        const stored = localStorage.getItem(KEY);
        if (stored) return clean(JSON.parse(stored));
        // Before етап 18 the reader's theme was the site's theme, and the size lived apart.
        const old: Record<string, unknown> = {};
        const theme = localStorage.getItem(LEGACY_THEME);
        if (theme === 'light') old.site = { preset: 'light' };
        if (theme === 'black') old.site = { preset: 'night' };
        const size = Number(localStorage.getItem(LEGACY_SIZE));
        if (size) old.reader = { size };
        return clean(old);
    } catch {
        return {};
    }
}

function loadSiteDefault(): SiteDefault {
    try {
        const stored = JSON.parse(localStorage.getItem(SITE_KEY) ?? 'null') as Partial<SiteDefault> | null;
        if (stored && FRAMES.some((f) => f.value === stored.frame) && THEMES.some((t) => t.value === stored.theme)) {
            return { frame: stored.frame!, theme: stored.theme! };
        }
    } catch {
        // Storage blocked: the code's default.
    }
    return SITE_DEFAULT;
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
    now = site();
    const { look, light } = now;
    const colors = themeVars(look.theme, look.own, light);
    const siteTheme: 'dark' | 'light' | 'black' = light ? 'light' : colors.bg.toLowerCase() === '#000000' ? 'black' : 'dark';
    const reader = readerColors(current);
    const own = readerOpen && reader.theme !== null;
    const theme = own && reader.theme ? reader.theme : siteTheme;
    const vars = siteVars(look, colors, light);
    // «Свої» colours of the reader are not in the stylesheet: they go on the page like the site's.
    const readerVars = reader.value === 'own' ? ownReaderVars(readerLook(current)) : null;
    for (const [name, value] of Object.entries(vars)) {
        if (own && COLOR_VARS.includes(name)) {
            if (readerVars?.[name]) root.style.setProperty(name, readerVars[name]);
            else root.style.removeProperty(name);
        } else root.style.setProperty(name, value);
    }
    // The reader has its own text size; the scale is for the rest of the site.
    root.style.setProperty('--ui-scale', readerOpen ? '1' : String(look.scale));
    const accent = readerOpen ? readerLook(current).accent : null;
    if (accent) root.style.setProperty('--accent', accent);
    root.dataset.frame = look.frame;
    root.dataset.theme = theme;
    root.dataset.motion = look.anim;
    root.dataset.catalog = look.catalog;
    if (own) root.dataset.reader = reader.value;
    else delete root.dataset.reader;
    void loadFont('golos');
    void loadFont('literata');
    if (readerOpen) void loadFont(readerLook(current).font);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', own ? reader.bg : colors.bg);
    try {
        // The next visit starts in this look before the app loads (public/appearance-boot.js).
        localStorage.setItem(VARS_KEY, JSON.stringify({
            vars: { ...vars, '--ui-scale': String(look.scale) }, readerVars, readerTheme: readerVars ? reader.theme : null,
            theme: siteTheme, frame: look.frame, motion: look.anim, catalog: look.catalog,
        }));
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

/** The site's look with the site's own choices filled in, and whether it is light now. */
export function useSite(): SiteNow {
    return useSyncExternalStore((listener) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
    }, () => now);
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

/** A change to the site's look: its каркас, тема, light or dark, size and the rest. */
export function setSite(patch: SiteAppearance) {
    changed({ ...current, site: { ...current.site, ...patch } });
}

/** «Свої кольори»: picking a tone or an accent makes them the тема. */
export function setOwnColors(patch: Partial<OwnColors>) {
    setSite({ theme: 'own', own: { ...now.look.own, ...patch } });
}

/** The sun / moon button in the header (етап 20): the same тема, light or dark — «Світла чи темна» in the settings. */
export function toggleSiteBase() {
    setSite({ mode: now.light ? 'dark' : 'light' });
}

/** The site's own каркас and тема, as the server has them; whoever never chose follows them. */
export function setSiteDefault(next: SiteDefault) {
    if (next.frame === siteDefault.frame && next.theme === siteDefault.theme) return;
    siteDefault = { frame: next.frame, theme: next.theme };
    try {
        localStorage.setItem(SITE_KEY, JSON.stringify(siteDefault));
    } catch {
        // Private mode: asked again on the next visit.
    }
    apply();
    listeners.forEach((listener) => listener());
}

/** Asks the server for the site's own каркас and тема once the app starts. */
export function fetchSiteDefault() {
    void api<SiteDefault>('/api/site/look').then((look) => {
        if (look && FRAMES.some((f) => f.value === look.frame) && THEMES.some((t) => t.value === look.theme)) setSiteDefault(look);
    }, () => {
        // Offline: the copy this browser keeps.
    });
}

/** A change to the reader's look: from now on it is one's own («Свій стиль»). */
export function setReader(patch: ReaderAppearance) {
    const reader = { ...current.reader, ...patch, custom: true };
    changed({ ...current, reader, readerOwn: reader });
}

/** Back to one's own reader style after trying ready ones. */
export function restoreOwnReader() {
    if (current.readerOwn) changed({ ...current, reader: current.readerOwn });
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
