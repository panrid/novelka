/**
 * How a person wants the site and the reader to look (етап 18): two separate sets, each a
 * ready style or one's own built from the site's choices. The server accepts the same keys and
 * values (Appearance.java); new choices are added in both places.
 */

export type SiteStyle = 'default' | 'light' | 'bookish' | 'parchment' | 'night' | 'contrast' | 'minimal' | 'sakura'
    | 'ocean' | 'forest' | 'neon' | 'terminal';

/** The site's look: a ready style, and what the person changed in it («Свій стиль»). */
export type SiteLook = {
    preset: SiteStyle;
    custom: boolean;
    base: 'light' | 'dark';
    /** Index into SITE_TONES[base]. */
    bg: number;
    accent: string;
    ui: FontId;
    head: FontId;
    /** Pages' content larger or smaller than the default, 0.9–1.2. */
    scale: number;
    radius: number;
    density: 'compact' | 'normal' | 'airy';
    cards: 'flat' | 'border' | 'shadow';
    anim: 'system' | 'full' | 'light' | 'off';
    icons: 'lucide' | 'tabler' | 'phosphor' | 'iconoir' | 'heroicons';
    /** Line width of the icons, 1–2.5. */
    iconWeight: number;
    /** In menus and bars: like the text, the accent, or muted. */
    iconColor: 'text' | 'accent' | 'muted';
    /** Filled icons, where the set has them (Phosphor, Heroicons). */
    iconFill: boolean;
    /** The main menu on a wide screen: across the top or down the side. */
    nav: 'top' | 'side';
    /** Novels in the catalog and on the home page: rows, a grid of covers, or a shelf. */
    catalog: 'rows' | 'grid' | 'shelf';
};
export type SiteAppearance = Partial<SiteLook>;

export type ReaderPreset = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'book' | 'dyslexia';
export type ReaderColors = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'tea' | 'dusk' | 'own';
export type FontId = 'literata' | 'ptserif' | 'lora' | 'merriweather' | 'playfair' | 'inter' | 'nunito' | 'rubik' | 'plex'
    | 'comfortaa' | 'jetbrains';

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
    clock: boolean;
    percent: boolean;
    awake: boolean;
};
export type ReaderAppearance = Partial<ReaderLook>;
export type Appearance = { site?: SiteAppearance; reader?: ReaderAppearance };

/** Backgrounds the site offers: the page, cards, raised parts, lines and the text that reads on them. */
export type Tone = { name: string; bg: string; surface: string; surface2: string; line: string; text: string; reading: string; muted: string; faint: string };
export const SITE_TONES: Record<'light' | 'dark', Tone[]> = {
    light: [
        { name: 'Кремовий', bg: '#f7f5ef', surface: '#ffffff', surface2: '#eeebe2', line: '#dedad0', text: '#1c1e1b', reading: '#262823', muted: '#5f625b', faint: '#8a8c85' },
        { name: 'Білий', bg: '#ffffff', surface: '#ffffff', surface2: '#f2f3f5', line: '#e2e4e8', text: '#15171a', reading: '#1f2226', muted: '#5c6168', faint: '#8a9097' },
        { name: 'Папір', bg: '#f1ead9', surface: '#faf6ec', surface2: '#e8dfca', line: '#d6c9ad', text: '#2a2118', reading: '#2e251b', muted: '#6b5d4a', faint: '#948670' },
        { name: 'Пергамент', bg: '#e9dcc0', surface: '#f3e9d3', surface2: '#ddcfaf', line: '#c9b791', text: '#3b2f22', reading: '#3b2f22', muted: '#6f5f48', faint: '#978569' },
        { name: 'Рожевий', bg: '#fbf1f3', surface: '#ffffff', surface2: '#f6e4e8', line: '#ecd2d8', text: '#2a1d21', reading: '#33242a', muted: '#7a5f67', faint: '#a08890' },
        { name: 'Блакитний', bg: '#eef4f6', surface: '#ffffff', surface2: '#e1ebef', line: '#cfdde3', text: '#17232a', reading: '#1f2c33', muted: '#566770', faint: '#82939b' },
    ],
    dark: [
        { name: 'Графіт', bg: '#121412', surface: '#1b1e1b', surface2: '#242824', line: '#2f342f', text: '#e9e7df', reading: '#dcdad1', muted: '#9a9d94', faint: '#6d7069' },
        { name: 'Чорний', bg: '#000000', surface: '#111311', surface2: '#1b1e1b', line: '#262a26', text: '#e2e0d8', reading: '#cfcdc5', muted: '#8f9289', faint: '#62655e' },
        { name: 'Глибина', bg: '#0e1a24', surface: '#14232f', surface2: '#1b2e3c', line: '#26394a', text: '#e2ebf2', reading: '#d2dde6', muted: '#8fa3b3', faint: '#627585' },
        { name: 'Мох', bg: '#172016', surface: '#1e2a1d', surface2: '#263425', line: '#324231', text: '#e6eadb', reading: '#d6dccb', muted: '#9aa58f', faint: '#6d7863' },
        { name: 'Фіолет', bg: '#0b0716', surface: '#140e24', surface2: '#1d1532', line: '#2a2045', text: '#ece6fa', reading: '#ddd5ef', muted: '#a497c2', faint: '#73688f' },
        { name: 'Термінал', bg: '#0a0f0a', surface: '#0f160f', surface2: '#142014', line: '#1e301e', text: '#b8f5c0', reading: '#a6e8ae', muted: '#5fae6a', faint: '#3f7a48' },
    ],
};

/** Accents picked so a button's text stays readable on them (dark or white ink is chosen for each). */
export const SITE_ACCENTS = ['#8fb07f', '#4f7a40', '#2f4a6b', '#4fb3d9', '#6c7ae0', '#b48cff', '#c2456b', '#ff3ea5',
    '#d9776b', '#e07b39', '#d8b45a', '#ffd400', '#7a5a2b', '#2a9d8f', '#8cff9c', '#111111'];

const SITE_DEFAULTS: SiteLook = {
    preset: 'default', custom: false, base: 'dark', bg: 0, accent: '#8fb07f', ui: 'inter', head: 'literata', scale: 1,
    radius: 12, density: 'normal', cards: 'flat', anim: 'system', icons: 'lucide', iconWeight: 1.75, iconColor: 'text', iconFill: false,
    nav: 'top', catalog: 'rows',
};

export const ICON_SETS: { value: SiteLook['icons']; label: string; fill: boolean }[] = [
    { value: 'lucide', label: 'Lucide', fill: false },
    { value: 'tabler', label: 'Tabler', fill: false },
    { value: 'phosphor', label: 'Phosphor', fill: true },
    { value: 'iconoir', label: 'Iconoir', fill: false },
    { value: 'heroicons', label: 'Heroicons', fill: true },
];

export const SITE_PRESETS: { value: SiteStyle; label: string; look: Partial<SiteLook> }[] = [
    { value: 'default', label: 'Звичайний', look: {} },
    { value: 'light', label: 'Світлий', look: { base: 'light', bg: 0, accent: '#4f7a40', cards: 'shadow', catalog: 'grid' } },
    { value: 'bookish', label: 'Книжковий', look: { base: 'light', bg: 2, accent: '#2f4a6b', ui: 'ptserif', head: 'playfair', radius: 2, cards: 'border', icons: 'phosphor', iconWeight: 1.5, iconColor: 'accent', nav: 'side', catalog: 'shelf' } },
    { value: 'parchment', label: 'Пергамент', look: { base: 'light', bg: 3, accent: '#7a5a2b', ui: 'merriweather', head: 'merriweather', radius: 6, cards: 'border', icons: 'iconoir', iconWeight: 1.5, catalog: 'shelf' } },
    { value: 'night', label: 'Нічний', look: { base: 'dark', bg: 1 } },
    { value: 'contrast', label: 'Контрастний', look: { base: 'dark', bg: 1, accent: '#ffd400', ui: 'rubik', head: 'rubik', radius: 8, cards: 'border', iconWeight: 2.25 } },
    { value: 'minimal', label: 'Мінімалізм', look: { base: 'light', bg: 1, accent: '#111111', head: 'inter', radius: 0, density: 'airy', anim: 'off', icons: 'tabler', iconWeight: 1.25 } },
    { value: 'sakura', label: 'Сакура', look: { base: 'light', bg: 4, accent: '#c2456b', ui: 'nunito', head: 'comfortaa', radius: 20, cards: 'shadow', anim: 'full', icons: 'phosphor', iconFill: true, iconColor: 'accent', catalog: 'grid' } },
    { value: 'ocean', label: 'Океан', look: { base: 'dark', bg: 2, accent: '#4fb3d9', ui: 'plex', head: 'plex', radius: 14, cards: 'shadow', icons: 'heroicons', nav: 'side', catalog: 'grid' } },
    { value: 'forest', label: 'Ліс', look: { base: 'dark', bg: 3, accent: '#d8b45a', ui: 'nunito', head: 'lora', radius: 10, cards: 'border', icons: 'iconoir', catalog: 'shelf' } },
    { value: 'neon', label: 'Неон', look: { base: 'dark', bg: 4, accent: '#ff3ea5', ui: 'rubik', head: 'rubik', radius: 10, cards: 'shadow', anim: 'full', iconWeight: 2, iconColor: 'accent', catalog: 'grid' } },
    { value: 'terminal', label: 'Ретро-термінал', look: { base: 'dark', bg: 5, accent: '#8cff9c', ui: 'jetbrains', head: 'jetbrains', radius: 0, cards: 'border', density: 'compact', anim: 'off', icons: 'tabler', iconWeight: 2, iconColor: 'accent', nav: 'side' } },
];

export const SITE_SCALES = [0.9, 1, 1.1, 1.2];

export const SITE_FONTS: { value: FontId; label: string }[] = [
    { value: 'inter', label: 'Інтер' }, { value: 'rubik', label: 'Рубік' }, { value: 'nunito', label: 'Нуніто' },
    { value: 'plex', label: 'IBM Plex Sans' }, { value: 'comfortaa', label: 'Комфортаа' }, { value: 'jetbrains', label: 'JetBrains Mono' },
    { value: 'literata', label: 'Літерата' }, { value: 'lora', label: 'Лора' }, { value: 'ptserif', label: 'PT Serif' },
    { value: 'merriweather', label: 'Мерівезер' }, { value: 'playfair', label: 'Плейфер' },
];

/** The site's look as it shows: the defaults, the chosen style, the person's own changes. */
export function siteLook(appearance: Appearance): SiteLook {
    const preset = SITE_PRESETS.find((item) => item.value === appearance.site?.preset) ?? SITE_PRESETS[0]!;
    return { ...SITE_DEFAULTS, ...preset.look, ...appearance.site, preset: preset.value } as SiteLook;
}

export function sitePresetLook(preset: SiteStyle): SiteAppearance {
    const found = SITE_PRESETS.find((item) => item.value === preset) ?? SITE_PRESETS[0]!;
    return { ...SITE_DEFAULTS, ...found.look, preset: found.value, custom: false };
}

/** Kept for the reader and older callers: the light/dark/black base of the site's look. */
export function siteStyle(appearance: Appearance) {
    const look = siteLook(appearance);
    const theme: 'dark' | 'light' | 'black' = look.base === 'light' ? 'light' : look.bg === 1 ? 'black' : 'dark';
    return { value: look.preset, theme, color: SITE_TONES[look.base][look.bg]?.bg ?? '#121412' };
}

/** Dark ink on a light accent, white on a dark one. */
export function inkOn(hex: string): string {
    const n = parseInt(hex.slice(1), 16);
    const light = 0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
    return light > 150 ? '#10140f' : '#ffffff';
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
    width: 'medium', margin: 16, colors: 'site', bg: '#f4ecd8', text: '#3b2f22', taps: 'sides', accent: null, hideBars: true, clock: false, percent: true, awake: false,
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
    preset: SITE_PRESETS.map((item) => item.value),
    base: ['light', 'dark'],
    ui: SITE_FONTS.map((item) => item.value),
    head: SITE_FONTS.map((item) => item.value),
    density: ['compact', 'normal', 'airy'],
    cards: ['flat', 'border', 'shadow'],
    anim: ['system', 'full', 'light', 'off'],
    icons: ICON_SETS.map((item) => item.value),
    iconColor: ['text', 'accent', 'muted'],
    nav: ['top', 'side'],
    catalog: ['rows', 'grid', 'shelf'],
    scale: SITE_SCALES,
};
const SITE_RANGE: { [K in keyof SiteLook]?: [number, number] } = { bg: [0, 5], radius: [0, 24] };
const FLAGS: (keyof ReaderLook)[] = ['custom', 'hideBars', 'clock', 'percent', 'awake'];

/** Only the keys and values this version knows: an older or damaged copy never breaks the page. */
export function clean(raw: unknown): Appearance {
    const value = (raw && typeof raw === 'object' ? raw : {}) as Record<string, Record<string, unknown> | undefined>;
    const out: Appearance = {};
    const site: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value.site ?? {})) {
        const k = key as keyof SiteLook;
        const range = SITE_RANGE[k];
        if (SITE_ONE_OF[k]?.includes(item)) site[k] = item;
        else if (range && typeof item === 'number' && Number.isInteger(item) && item >= range[0] && item <= range[1]) site[k] = item;
        else if ((k === 'custom' || k === 'iconFill') && typeof item === 'boolean') site[k] = item;
        else if (k === 'iconWeight' && typeof item === 'number' && item >= 1 && item <= 2.5) site[k] = item;
        else if (k === 'accent' && typeof item === 'string' && SITE_ACCENTS.includes(item)) site[k] = item;
    }
    if (Object.keys(site).length > 0) out.site = site as SiteAppearance;
    const reader: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value.reader ?? {})) {
        const k = key as keyof ReaderLook;
        const range = RANGE[k];
        if (ONE_OF[k]?.includes(item)) reader[k] = item;
        else if (range && typeof item === 'number' && item >= range[0] && item <= range[1] && (!range[2] || Number.isInteger(item))) reader[k] = item;
        else if (FLAGS.includes(k) && typeof item === 'boolean') reader[k] = item;
        else if (k === 'accent' && typeof item === 'string' && READER_ACCENTS.includes(item)) reader[k] = item;
    }
    if (Object.keys(reader).length > 0) out.reader = reader as ReaderAppearance;
    return out;
}
