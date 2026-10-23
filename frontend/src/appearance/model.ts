/**
 * How a person wants the site and the reader to look (етап 18, етап 20). The site: a каркас (its build —
 * menus, fonts, shapes) and a тема (colours), chosen apart (doc/redesign/README.md, рішення 30–33); a key
 * left out follows the site's own choice. The server accepts the same keys and values (Appearance.java);
 * new choices are added in both places.
 */

import { FRAMES, NOVELKA_TONE, THEMES, inkOn, type FrameId, type OwnColors, type ThemeId } from './themes';

export { NOVELKA_TONE, SITE_TONES, inkOn, type Tone } from './themes';
export type { FrameId, OwnColors, ThemeId } from './themes';

/** The site's look as it shows. */
export type SiteLook = {
    frame: FrameId;
    theme: ThemeId | 'own';
    /** «Світла чи темна»: dark unless chosen; on a computer also the sun / moon in the header. */
    mode: 'dark' | 'light' | 'system';
    own: OwnColors;
    /** Pages' content larger or smaller than the default, 0.9–1.2. */
    scale: number;
    /** Novels in the catalog and on the home page: rows, a grid of covers, or a shelf. */
    catalog: 'rows' | 'grid' | 'shelf';
    /** Pictures of characters (the novel's «Персонажі»): circles or squares. */
    portraits: 'round' | 'square';
    anim: 'system' | 'light' | 'off';
};
export type SiteAppearance = Partial<SiteLook>;
/** The site's каркас and тема for those who never chose theirs (the owner picks it; /api/site/look). */
export type SiteDefault = { frame: FrameId; theme: ThemeId };
export const SITE_DEFAULT: SiteDefault = { frame: 'pult', theme: 'slanets' };

export type ReaderPreset = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'book' | 'dyslexia';
export type ReaderColors = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'tea' | 'dusk' | 'own';
export type FontId = 'literata' | 'ptserif' | 'lora' | 'merriweather' | 'playfair' | 'inter' | 'nunito' | 'rubik' | 'plex'
    | 'comfortaa' | 'jetbrains' | 'golos';

/** Everything the reader can change; a stored copy keeps only what differs from nothing at all. */
export type ReaderLook = {
    preset: ReaderPreset;
    /** Changed after choosing the preset: «Свій стиль». */
    custom: boolean;
    /** Scrolling down, or turning pages like a book. */
    mode: 'scroll' | 'pages';
    /** How a page turns; «none» for phones that do not keep up. */
    pageAnim: 'none' | 'slide' | 'fade' | 'curl';
    font: FontId;
    size: number;
    lineHeight: number;
    align: 'left' | 'justify';
    paragraphs: 'gap' | 'indent';
    width: 'narrow' | 'medium' | 'wide' | 'full';
    margin: number;
    colors: ReaderColors;
    /** «Свої» colours: the page and the text, from READER_BACKGROUNDS and READER_INKS. */
    bg: string;
    text: string;
    /** Where a tap turns a page: the side edges, everywhere but the left edge, top and bottom, or nowhere. */
    taps: 'sides' | 'forward' | 'vertical' | 'none';
    /** Links and marks in the text; null keeps the palette's own. */
    accent: string | null;
    hideBars: boolean;
    /** Безшовне читання (етап 20, рішення 7): the next chapter follows at the end of this one. */
    seamless: boolean;
    clock: boolean;
    percent: boolean;
    awake: boolean;
};
export type ReaderAppearance = Partial<ReaderLook>;
/** The look in use, and the reader's own one kept aside while ready reader styles are tried. */
export type Appearance = { site?: SiteAppearance; reader?: ReaderAppearance; readerOwn?: ReaderAppearance };

/** Accents picked so a button's text stays readable on them (dark or white ink is chosen for each). */
export const SITE_ACCENTS = ['#8ea6ff', '#2a4bd7', '#8fb07f', '#4f7a40', '#2f4a6b', '#4fb3d9', '#6c7ae0', '#b48cff', '#c2456b', '#ff3ea5',
    '#d9776b', '#e07b39', '#d8b45a', '#ffd400', '#7a5a2b', '#2a9d8f', '#8cff9c', '#111111'];

export const SITE_SCALES = [0.9, 1, 1.1, 1.2];

const SITE_DEFAULTS: Omit<SiteLook, 'frame' | 'theme'> = {
    mode: 'dark', own: { bg: NOVELKA_TONE, accent: '#8ea6ff' }, scale: 1, catalog: 'rows', portraits: 'round', anim: 'system',
};

/** The site's look as it shows: the person's choices over the site's own каркас and тема. */
export function siteLook(appearance: Appearance, site: SiteDefault = SITE_DEFAULT): SiteLook {
    const chosen = Object.entries(appearance.site ?? {}).filter(([, value]) => value !== undefined);
    return { ...SITE_DEFAULTS, ...site, ...Object.fromEntries(chosen) } as SiteLook;
}

/** Light or dark right now: «Як у системі» follows the device. */
export function isLight(look: SiteLook, systemLight: boolean): boolean {
    return look.mode === 'light' || (look.mode === 'system' && systemLight);
}


/** The reader's palettes (styles/tokens.css, [data-reader]); theme is the light or dark base for its controls. */
export const READER_COLORS: { value: ReaderColors; label: string; theme: 'dark' | 'light' | 'black' | null; bg: string; text: string }[] = [
    { value: 'site', label: 'Як на сайті', theme: null, bg: 'var(--bg)', text: 'var(--text)' },
    { value: 'paper', label: 'Папір', theme: 'light', bg: '#f7f5ef', text: '#262823' },
    { value: 'sepia', label: 'Сепія', theme: 'light', bg: '#f1e4c8', text: '#3b2f22' },
    { value: 'gray', label: 'Сірий', theme: 'light', bg: '#d9dbd6', text: '#232521' },
    { value: 'tea', label: 'Зелений чай', theme: 'light', bg: '#e6efe3', text: '#1f2a1c' },
    { value: 'night', label: 'Ніч', theme: 'dark', bg: '#121412', text: '#dcdad1' },
    { value: 'dusk', label: 'Сутінки', theme: 'dark', bg: '#1a2230', text: '#c3cddc' },
    { value: 'black', label: 'Чорний', theme: 'black', bg: '#000000', text: '#cfcdc5' },
];

export type TapZone = { what: 'back' | 'next' | 'bars'; x: [number, number]; y: [number, number] };

/** Where a tap turns a page back, forward, or shows the controls; the first zone that holds the point wins. */
export function tapZones(taps: ReaderLook['taps']): TapZone[] {
    switch (taps) {
        case 'forward':
            return [{ what: 'bars', x: [0, 1], y: [0, 0.2] }, { what: 'back', x: [0, 0.25], y: [0, 1] }, { what: 'next', x: [0.25, 1], y: [0, 1] }];
        case 'vertical':
            return [{ what: 'back', x: [0, 1], y: [0, 0.33] }, { what: 'next', x: [0, 1], y: [0.67, 1] }, { what: 'bars', x: [0, 1], y: [0.33, 0.67] }];
        case 'none':
            return [{ what: 'bars', x: [0, 1], y: [0, 1] }];
        default:
            return [{ what: 'back', x: [0, 0.3], y: [0, 1] }, { what: 'next', x: [0.7, 1], y: [0, 1] }, { what: 'bars', x: [0.3, 0.7], y: [0, 1] }];
    }
}

/** Which zone a tap at (x, y), as shares of the screen, falls in. */
export function tapAt(taps: ReaderLook['taps'], x: number, y: number): TapZone['what'] {
    return tapZones(taps).find((zone) => x >= zone.x[0] && x <= zone.x[1] && y >= zone.y[0] && y <= zone.y[1])?.what ?? 'bars';
}

/** «Свої» colours: pages and inks the reader offers to combine. */
export const READER_BACKGROUNDS = ['#ffffff', '#f7f5ef', '#f4ecd8', '#e9dcc0', '#e6efe3', '#e3ecf3', '#f6e4e8', '#d9dbd6',
    '#3a3632', '#2b2b2b', '#1a2230', '#1b261d', '#121412', '#000000'];
export const READER_INKS = ['#000000', '#262823', '#3b2f22', '#5a4632', '#1f2a1c', '#17232a', '#3a2a4a',
    '#ffffff', '#e8e4da', '#dcdad1', '#c3cddc', '#e8d9b8', '#a6e8ae', '#ffb86b'];

/** How readable text is on a background (WCAG contrast ratio, 1–21). */
export function contrast(a: string, b: string): number {
    const lum = (hex: string) => {
        const n = parseInt(hex.slice(1), 16);
        const [r, g, bl] = [n >> 16, (n >> 8) & 255, n & 255].map((c) => {
            const v = c / 255;
            return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
        });
        return 0.2126 * r! + 0.7152 * g! + 0.0722 * bl!;
    };
    const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
    return (x! + 0.05) / (y! + 0.05);
}

/** Marks and links: picked so they read on both light and dark palettes. */
export const READER_ACCENTS = ['#4f7a40', '#8fb07f', '#3f6f9e', '#8a5a22', '#b5476b', '#8b6fd1', '#c49a3a'];

export const READER_FONTS: { value: FontId; label: string }[] = [
    { value: 'literata', label: 'Літерата — книжковий' },
    { value: 'ptserif', label: 'PT Serif — класичний' },
    { value: 'lora', label: 'Лора — м’який із засічками' },
    { value: 'merriweather', label: 'Мерівезер — для екрана' },
    { value: 'inter', label: 'Інтер — без засічок' },
    { value: 'nunito', label: 'Нуніто — округлий' },
    { value: 'rubik', label: 'Рубік — широкий, легко читати' },
    { value: 'jetbrains', label: 'JetBrains Mono — моноширинний' },
];

export const READER_SIZE = { min: 14, max: 28, initial: 18 } as const;
export const READER_LINE_HEIGHT = { min: 1.3, max: 2.1 } as const;
export const READER_MARGIN = { min: 0, max: 48 } as const;

const READER_DEFAULTS: ReaderLook = {
    preset: 'site', custom: false, mode: 'scroll', pageAnim: 'slide', font: 'literata', size: 18, lineHeight: 1.75, align: 'left', paragraphs: 'gap',
    width: 'medium', margin: 16, colors: 'site', bg: '#f4ecd8', text: '#3b2f22', taps: 'sides', accent: null, hideBars: true, seamless: true, clock: false, percent: true, awake: false,
};

export const READER_PRESETS: { value: ReaderPreset; label: string; look: Partial<ReaderLook> }[] = [
    { value: 'site', label: 'Як на сайті', look: {} },
    { value: 'paper', label: 'Папір', look: { colors: 'paper' } },
    { value: 'sepia', label: 'Сепія', look: { colors: 'sepia', font: 'ptserif' } },
    { value: 'gray', label: 'Сірий', look: { colors: 'gray', font: 'lora' } },
    { value: 'night', label: 'Ніч', look: { colors: 'night' } },
    { value: 'black', label: 'Чорний', look: { colors: 'black' } },
    { value: 'book', label: 'Книжка', look: { colors: 'sepia', font: 'ptserif', align: 'justify', paragraphs: 'indent', lineHeight: 1.55, width: 'wide', mode: 'pages', pageAnim: 'curl' } },
    { value: 'dyslexia', label: 'Для дислексії', look: { colors: 'paper', font: 'rubik', size: 20, lineHeight: 2, width: 'narrow' } },
];

/** The reader's look as it shows: the defaults, then the chosen preset, then the person's own changes. */
export function readerLook(appearance: Appearance): ReaderLook {
    return { ...READER_DEFAULTS, ...appearance.reader } as ReaderLook;
}

/** Choosing a preset replaces the look; changing anything afterwards makes it one's own. */
export function presetLook(preset: ReaderPreset): ReaderAppearance {
    const found = READER_PRESETS.find((item) => item.value === preset) ?? READER_PRESETS[0]!;
    return { ...READER_DEFAULTS, ...found.look, preset: found.value, custom: false };
}

export function readerColors(appearance: Appearance): typeof READER_COLORS[number] {
    const look = readerLook(appearance);
    if (look.colors === 'own') {
        return { value: 'own', label: 'Свої', theme: inkOn(look.bg) === '#ffffff' ? 'dark' : 'light', bg: look.bg, text: look.text };
    }
    return READER_COLORS.find((colors) => colors.value === look.colors) ?? READER_COLORS[0]!;
}

/** The variables of «Свої» colours: the rest of the reader's palette is mixed from the two. */
export function ownReaderVars(look: ReaderLook): Record<string, string> {
    const mix = (share: number) => `color-mix(in srgb, ${look.text} ${share}%, ${look.bg})`;
    const accent = look.accent ?? (inkOn(look.bg) === '#ffffff' ? '#8fb07f' : '#4f7a40');
    return {
        '--bg': look.bg, '--surface': mix(5), '--surface-2': mix(10), '--line': mix(18), '--text': look.text,
        '--text-reading': look.text, '--muted': mix(65), '--faint': mix(45), '--accent': accent, '--accent-ink': inkOn(accent),
        '--focus': accent, '--card-border': '1px solid transparent', '--card-shadow': 'none', '--icon-color': 'currentColor',
    };
}

export function readerSize(appearance: Appearance): number {
    return readerLook(appearance).size;
}

export const READER_WIDTHS: Record<ReaderLook['width'], string> = { narrow: '30em', medium: '38em', wide: '48em', full: 'none' };

const ONE_OF: { [K in keyof ReaderLook]?: readonly unknown[] } = {
    preset: READER_PRESETS.map((item) => item.value),
    mode: ['scroll', 'pages'],
    pageAnim: ['none', 'slide', 'fade', 'curl'],
    font: READER_FONTS.map((item) => item.value),
    align: ['left', 'justify'],
    paragraphs: ['gap', 'indent'],
    width: ['narrow', 'medium', 'wide', 'full'],
    colors: [...READER_COLORS.map((item) => item.value), 'own'],
    bg: READER_BACKGROUNDS,
    text: READER_INKS,
    taps: ['sides', 'forward', 'vertical', 'none'],
};
const RANGE: { [K in keyof ReaderLook]?: [number, number, boolean] } = {
    size: [READER_SIZE.min, READER_SIZE.max, true],
    lineHeight: [1.2, 2.2, false],
    margin: [READER_MARGIN.min, READER_MARGIN.max, true],
};
const SITE_ONE_OF: { [K in keyof SiteLook]?: readonly unknown[] } = {
    frame: FRAMES.map((item) => item.value),
    theme: [...THEMES.map((item) => item.value), 'own'],
    mode: ['dark', 'light', 'system'],
    catalog: ['rows', 'grid', 'shelf'],
    portraits: ['round', 'square'],
    anim: ['system', 'light', 'off'],
    scale: SITE_SCALES,
};
const FLAGS: (keyof ReaderLook)[] = ['custom', 'hideBars', 'seamless', 'clock', 'percent', 'awake'];

/** Only the keys and values this version knows: an older or damaged copy never breaks the page. */
export function clean(raw: unknown): Appearance {
    const value = (raw && typeof raw === 'object' ? raw : {}) as Record<string, Record<string, unknown> | undefined>;
    const out: Appearance = {};
    // A look saved before #292 (one site style): its colours become a тема.
    const site = cleanSite(isLegacy(value) ? upgradeSite(value.site, value.siteOwn) : value.site);
    if (site) out.site = site;
    const reader = cleanReader(value.reader);
    if (reader) out.reader = reader;
    // The reader's «Свій стиль» waits here while the person tries ready ones.
    const readerOwn = cleanReader(value.readerOwn);
    if (readerOwn) out.readerOwn = readerOwn;
    return out;
}

function cleanSite(raw: Record<string, unknown> | undefined): SiteAppearance | undefined {
    const site: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(raw && typeof raw === 'object' ? raw : {})) {
        const k = key as keyof SiteLook;
        if (SITE_ONE_OF[k]?.includes(item)) site[k] = item;
        else if (k === 'own' && isOwn(item)) site[k] = { bg: item.bg, accent: item.accent };
    }
    return Object.keys(site).length > 0 ? site as SiteAppearance : undefined;
}

function isOwn(item: unknown): item is OwnColors {
    const own = item as Partial<OwnColors> | null;
    return Boolean(own && typeof own === 'object' && Number.isInteger(own.bg) && own.bg! >= 0 && own.bg! <= 6
        && typeof own.accent === 'string' && /^#[0-9a-fA-F]{6}$/.test(own.accent));
}

const LEGACY_KEYS = ['preset', 'custom', 'base', 'bg', 'accent', 'ui', 'head', 'radius', 'density', 'cards', 'icons', 'iconWeight',
    'iconColor', 'iconFill', 'nav'];

function isLegacy(value: Record<string, Record<string, unknown> | undefined>): boolean {
    const site = value.site ?? {};
    if (['frame', 'theme', 'mode', 'own'].some((key) => key in site)) return false;
    return Boolean(value.siteOwn) || LEGACY_KEYS.some((key) => key in site) || site.anim === 'full';
}

/** The old site styles' colours: the base, the tone and the accent (V62__appearance_frame_theme.sql has the same). */
const LEGACY_STYLES: Record<string, ['light' | 'dark', number, string]> = {
    default: ['dark', NOVELKA_TONE, '#8ea6ff'], light: ['light', 0, '#4f7a40'], bookish: ['light', 2, '#2f4a6b'],
    parchment: ['light', 3, '#7a5a2b'], night: ['dark', 1, '#8ea6ff'], contrast: ['dark', 1, '#ffd400'], minimal: ['light', 1, '#111111'],
    sakura: ['light', 4, '#c2456b'], ocean: ['dark', 2, '#4fb3d9'], forest: ['dark', 3, '#d8b45a'], neon: ['dark', 4, '#ff3ea5'],
    terminal: ['dark', 5, '#8cff9c'],
};

function legacyColors(site: Record<string, unknown> = {}) {
    const style = typeof site.preset === 'string' && site.preset in LEGACY_STYLES ? site.preset : 'default';
    const [base, bg, accent] = LEGACY_STYLES[style]!;
    const look = {
        style,
        base: site.base === 'light' || site.base === 'dark' ? site.base : base,
        bg: Number.isInteger(site.bg) && (site.bg as number) >= 0 && (site.bg as number) <= 6 ? site.bg as number : typeof site.bg === 'number' ? 0 : bg,
        accent: (typeof site.accent === 'string' ? site.accent : accent).toLowerCase(),
    };
    // The sun / moon kept the tone and the accent; only «Звичайний» swapped its accent for the light pair.
    const own = !(look.bg === bg && (look.accent === accent || (style === 'default' && look.accent === '#2a4bd7')));
    return { ...look, own };
}

/** Whoever set up the look keeps their colours; fonts, shapes and the menu now come with the каркас. */
export function upgradeSite(site: Record<string, unknown> = {}, siteOwn?: Record<string, unknown>): Record<string, unknown> {
    const look = legacyColors(site);
    const out: Record<string, unknown> = {};
    if (look.own) {
        out.theme = 'own';
        out.own = { bg: look.bg, accent: look.accent };
    } else if (look.style !== 'default') {
        out.theme = look.style;
    }
    if (look.base === 'light') out.mode = 'light';
    if (!look.own && siteOwn && typeof siteOwn === 'object') {
        const kept = legacyColors(siteOwn);
        if (kept.own) out.own = { bg: kept.bg, accent: kept.accent };
    }
    for (const key of ['scale', 'catalog', 'portraits'] as const) {
        if (key in site) out[key] = site[key];
    }
    if (site.anim === 'light' || site.anim === 'off') out.anim = site.anim;
    return out;
}

function cleanReader(raw: Record<string, unknown> | undefined): ReaderAppearance | undefined {
    const reader: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(raw && typeof raw === 'object' ? raw : {})) {
        const k = key as keyof ReaderLook;
        const range = RANGE[k];
        if (ONE_OF[k]?.includes(item)) reader[k] = item;
        else if (range && typeof item === 'number' && item >= range[0] && item <= range[1] && (!range[2] || Number.isInteger(item))) reader[k] = item;
        else if (FLAGS.includes(k) && typeof item === 'boolean') reader[k] = item;
        else if (k === 'accent' && typeof item === 'string' && READER_ACCENTS.includes(item)) reader[k] = item;
    }
    return Object.keys(reader).length > 0 ? reader as ReaderAppearance : undefined;
}
