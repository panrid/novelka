/**
 * How a person wants the site and the reader to look (етап 18): two separate sets, each a
 * ready style or one's own built from the site's choices. The server accepts the same keys and
 * values (Appearance.java); new choices are added in both places.
 */

export type SiteStyle = 'default' | 'light' | 'night';
export type ReaderColors = 'site' | 'paper' | 'night' | 'black';

export type SiteAppearance = { preset?: SiteStyle };
export type ReaderAppearance = { colors?: ReaderColors; size?: number };
export type Appearance = { site?: SiteAppearance; reader?: ReaderAppearance };

/** The tokens each style uses (styles/tokens.css): the three looks the site had before stay as they were. */
export const SITE_STYLES: { value: SiteStyle; label: string; theme: 'dark' | 'light' | 'black'; color: string }[] = [
    { value: 'default', label: 'Звичайний', theme: 'dark', color: '#121412' },
    { value: 'light', label: 'Світлий', theme: 'light', color: '#f7f5ef' },
    { value: 'night', label: 'Нічний', theme: 'black', color: '#000000' },
];

export const READER_COLORS: { value: ReaderColors; label: string; theme: 'dark' | 'light' | 'black' | null }[] = [
    { value: 'site', label: 'Як на сайті', theme: null },
    { value: 'paper', label: 'Папір', theme: 'light' },
    { value: 'night', label: 'Ніч', theme: 'dark' },
    { value: 'black', label: 'Чорний', theme: 'black' },
];

export const READER_SIZE = { min: 15, max: 26, initial: 18 } as const;

export function siteStyle(appearance: Appearance) {
    return SITE_STYLES.find((style) => style.value === appearance.site?.preset) ?? SITE_STYLES[0]!;
}

export function readerColors(appearance: Appearance) {
    return READER_COLORS.find((colors) => colors.value === appearance.reader?.colors) ?? READER_COLORS[0]!;
}

export function readerSize(appearance: Appearance): number {
    const size = appearance.reader?.size;
    return typeof size === 'number' && size >= READER_SIZE.min && size <= READER_SIZE.max ? size : READER_SIZE.initial;
}

/** Only the keys and values this version knows: an older or damaged copy never breaks the page. */
export function clean(raw: unknown): Appearance {
    const value = (raw && typeof raw === 'object' ? raw : {}) as Record<string, Record<string, unknown> | undefined>;
    const out: Appearance = {};
    const preset = value.site?.preset;
    if (SITE_STYLES.some((style) => style.value === preset)) out.site = { preset: preset as SiteStyle };
    const reader: ReaderAppearance = {};
    const colors = value.reader?.colors;
    if (READER_COLORS.some((c) => c.value === colors)) reader.colors = colors as ReaderColors;
    const size = value.reader?.size;
    if (typeof size === 'number' && Number.isInteger(size) && size >= READER_SIZE.min && size <= READER_SIZE.max) reader.size = size;
    if (Object.keys(reader).length > 0) out.reader = reader;
    return out;
}
