/**
 * How a person wants the site and the reader to look (етап 18): two separate sets, each a
 * ready style or one's own built from the site's choices. The server accepts the same keys and
 * values (Appearance.java); new choices are added in both places.
 */

export type SiteStyle = 'default' | 'light' | 'night';
export type SiteAppearance = { preset?: SiteStyle };

export type ReaderPreset = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'book' | 'dyslexia';
export type ReaderColors = 'site' | 'paper' | 'sepia' | 'gray' | 'night' | 'black' | 'tea' | 'dusk';
export type FontId = 'literata' | 'ptserif' | 'lora' | 'merriweather' | 'inter' | 'nunito' | 'rubik' | 'jetbrains';

/** Everything the reader can change; a stored copy keeps only what differs from nothing at all. */
export type ReaderLook = {
    preset: ReaderPreset;
    /** Changed after choosing the preset: «Свій стиль». */
    custom: boolean;
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

/** The tokens each style uses (styles/tokens.css): the three looks the site had before stay as they were. */
export const SITE_STYLES: { value: SiteStyle; label: string; theme: 'dark' | 'light' | 'black'; color: string }[] = [
    { value: 'default', label: 'Звичайний', theme: 'dark', color: '#121412' },
    { value: 'light', label: 'Світлий', theme: 'light', color: '#f7f5ef' },
    { value: 'night', label: 'Нічний', theme: 'black', color: '#000000' },
];

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
    preset: 'site', custom: false, font: 'literata', size: 18, lineHeight: 1.75, align: 'left', paragraphs: 'gap',
    width: 'medium', margin: 16, colors: 'site', accent: null, hideBars: true, clock: false, percent: true, awake: false,
};

export const READER_PRESETS: { value: ReaderPreset; label: string; look: Partial<ReaderLook> }[] = [
    { value: 'site', label: 'Як на сайті', look: {} },
    { value: 'paper', label: 'Папір', look: { colors: 'paper' } },
    { value: 'sepia', label: 'Сепія', look: { colors: 'sepia', font: 'ptserif' } },
    { value: 'gray', label: 'Сірий', look: { colors: 'gray', font: 'lora' } },
    { value: 'night', label: 'Ніч', look: { colors: 'night' } },
    { value: 'black', label: 'Чорний', look: { colors: 'black' } },
    { value: 'book', label: 'Книжка', look: { colors: 'sepia', font: 'ptserif', align: 'justify', paragraphs: 'indent', lineHeight: 1.55, width: 'wide' } },
    { value: 'dyslexia', label: 'Для дислексії', look: { colors: 'paper', font: 'rubik', size: 20, lineHeight: 2, width: 'narrow' } },
];

export function siteStyle(appearance: Appearance) {
    return SITE_STYLES.find((style) => style.value === appearance.site?.preset) ?? SITE_STYLES[0]!;
}

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
const FLAGS: (keyof ReaderLook)[] = ['custom', 'hideBars', 'clock', 'percent', 'awake'];

/** Only the keys and values this version knows: an older or damaged copy never breaks the page. */
export function clean(raw: unknown): Appearance {
    const value = (raw && typeof raw === 'object' ? raw : {}) as Record<string, Record<string, unknown> | undefined>;
    const out: Appearance = {};
    const preset = value.site?.preset;
    if (SITE_STYLES.some((style) => style.value === preset)) out.site = { preset: preset as SiteStyle };
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
