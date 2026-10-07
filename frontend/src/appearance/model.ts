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
    radius: number;
    density: 'compact' | 'normal' | 'airy';
    cards: 'flat' | 'border' | 'shadow';
    anim: 'system' | 'full' | 'light' | 'off';
};
export type SiteAppearance = Partial<SiteLook>;

export type ReaderPreset = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'book' | 'dyslexia';
export type ReaderColors = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'tea' | 'dusk';
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
    preset: 'default', custom: false, base: 'dark', bg: 0, accent: '#8fb07f', ui: 'inter', head: 'literata',
    radius: 12, density: 'normal', cards: 'flat', anim: 'system',
};

export const SITE_PRESETS: { value: SiteStyle; label: string; look: Partial<SiteLook> }[] = [
    { value: 'default', label: 'Звичайний', look: {} },
    { value: 'light', label: 'Світлий', look: { base: 'light', bg: 0, accent: '#4f7a40', cards: 'shadow' } },
    { value: 'bookish', label: 'Книжковий', look: { base: 'light', bg: 2, accent: '#2f4a6b', ui: 'ptserif', head: 'playfair', radius: 2, cards: 'border' } },
    { value: 'parchment', label: 'Пергамент', look: { base: 'light', bg: 3, accent: '#7a5a2b', ui: 'merriweather', head: 'merriweather', radius: 6, cards: 'border' } },
    { value: 'night', label: 'Нічний', look: { base: 'dark', bg: 1 } },
    { value: 'contrast', label: 'Контрастний', look: { base: 'dark', bg: 1, accent: '#ffd400', ui: 'rubik', head: 'rubik', radius: 8, cards: 'border' } },
    { value: 'minimal', label: 'Мінімалізм', look: { base: 'light', bg: 1, accent: '#111111', head: 'inter', radius: 0, density: 'airy', anim: 'off' } },
    { value: 'sakura', label: 'Сакура', look: { base: 'light', bg: 4, accent: '#c2456b', ui: 'nunito', head: 'comfortaa', radius: 20, cards: 'shadow', anim: 'full' } },
    { value: 'ocean', label: 'Океан', look: { base: 'dark', bg: 2, accent: '#4fb3d9', ui: 'plex', head: 'plex', radius: 14, cards: 'shadow' } },
    { value: 'forest', label: 'Ліс', look: { base: 'dark', bg: 3, accent: '#d8b45a', ui: 'nunito', head: 'lora', radius: 10, cards: 'border' } },
    { value: 'neon', label: 'Неон', look: { base: 'dark', bg: 4, accent: '#ff3ea5', ui: 'rubik', head: 'rubik', radius: 10, cards: 'shadow', anim: 'full' } },
    { value: 'terminal', label: 'Ретро-термінал', look: { base: 'dark', bg: 5, accent: '#8cff9c', ui: 'jetbrains', head: 'jetbrains', radius: 0, cards: 'border', density: 'compact', anim: 'off' } },
];

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
    width: 'medium', margin: 16, colors: 'site', accent: null, hideBars: true, clock: false, percent: true, awake: false,
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

export function readerColors(appearance: Appearance) {
    return READER_COLORS.find((colors) => colors.value === readerLook(appearance).colors) ?? READER_COLORS[0]!;
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
    colors: READER_COLORS.map((item) => item.value),
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
        else if (k === 'custom' && typeof item === 'boolean') site[k] = item;
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
