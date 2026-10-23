/**
 * Каркаси й теми of the new design (doc/redesign/README.md «Вигляд: каркаси й теми», рішення 30–33, 38).
 * A тема is colours only, in a dark and a light variant, with the same variables in every каркас; the
 * values of the first four are exactly those of doc/redesign/mockup-src/lib.mjs. The colours of the site's
 * twelve styles are themes too: their own variant keeps the style's tone and accent, the other one is what
 * the sun / moon gave them, and what those styles never had comes from Графіт — until #303 draws them.
 */

/** «Свої кольори»: a tone of the site (SITE_TONES) and an accent (model.ts SITE_ACCENTS). */
export type OwnColors = { bg: number; accent: string };

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
        { name: 'Новелка', bg: '#f3f4f6', surface: '#ffffff', surface2: '#f1f2f5', line: '#e3e5ea', text: '#15171c', reading: '#1b1d22', muted: '#5b6170', faint: '#8a8f9b' },
    ],
    dark: [
        { name: 'Графіт', bg: '#121412', surface: '#1b1e1b', surface2: '#242824', line: '#2f342f', text: '#e9e7df', reading: '#dcdad1', muted: '#9a9d94', faint: '#6d7069' },
        { name: 'Чорний', bg: '#000000', surface: '#111311', surface2: '#1b1e1b', line: '#262a26', text: '#e2e0d8', reading: '#cfcdc5', muted: '#8f9289', faint: '#62655e' },
        { name: 'Глибина', bg: '#0e1a24', surface: '#14232f', surface2: '#1b2e3c', line: '#26394a', text: '#e2ebf2', reading: '#d2dde6', muted: '#8fa3b3', faint: '#627585' },
        { name: 'Мох', bg: '#172016', surface: '#1e2a1d', surface2: '#263425', line: '#324231', text: '#e6eadb', reading: '#d6dccb', muted: '#9aa58f', faint: '#6d7863' },
        { name: 'Фіолет', bg: '#0b0716', surface: '#140e24', surface2: '#1d1532', line: '#2a2045', text: '#ece6fa', reading: '#ddd5ef', muted: '#a497c2', faint: '#73688f' },
        { name: 'Термінал', bg: '#0a0f0a', surface: '#0f160f', surface2: '#142014', line: '#1e301e', text: '#b8f5c0', reading: '#a6e8ae', muted: '#5fae6a', faint: '#3f7a48' },
        { name: 'Новелка', bg: '#0f1115', surface: '#171a20', surface2: '#1f232b', line: '#2a2f39', text: '#e8eaee', reading: '#dadde3', muted: '#a3a9b6', faint: '#6f7584' },
    ],
};
/** The tone of «Звичайний» (now Графіт), where «Свої кольори» start. */
export const NOVELKA_TONE = 6;

/** Dark ink on a light accent, white on a dark one. */
export function inkOn(hex: string): string {
    const n = parseInt(hex.slice(1), 16);
    const light = 0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
    return light > 150 ? '#10140f' : '#ffffff';
}

export type FrameId = 'pult' | 'zv' | 'pal' | 'obl';
export type ThemeId = 'slanets' | 'graphite' | 'sukno' | 'slyva' | 'light' | 'bookish' | 'parchment' | 'night' | 'contrast'
    | 'minimal' | 'sakura' | 'ocean' | 'forest' | 'neon' | 'terminal';

/** What each каркас is, in the words of «Вигляд сайту». */
export const FRAMES: { value: FrameId; label: string; about: string }[] = [
    { value: 'pult', label: 'Пульт', about: 'щільно, панелі впритул, моно для чисел' },
    { value: 'zv', label: 'Звичний', about: 'як сайт зараз: картки, шапка, Golos і Literata' },
    { value: 'pal', label: 'Палітурка', about: 'як книжка: засічки, поля й лінійки' },
    { value: 'obl', label: 'Обкладинка', about: 'яскраво: великі номери, пухкі кнопки' },
];

/** In the order of «Вигляд сайту»: the first seven show at once, the rest behind «Усі теми». */
export const THEMES: { value: ThemeId; label: string }[] = [
    { value: 'slanets', label: 'Сланець' },
    { value: 'graphite', label: 'Графіт · зараз' },
    { value: 'sukno', label: 'Сукно' },
    { value: 'slyva', label: 'Слива' },
    { value: 'parchment', label: 'Пергамент' },
    { value: 'ocean', label: 'Океан' },
    { value: 'neon', label: 'Неон' },
    { value: 'light', label: 'Світлий' },
    { value: 'bookish', label: 'Книжковий' },
    { value: 'night', label: 'Нічний' },
    { value: 'contrast', label: 'Контрастний' },
    { value: 'minimal', label: 'Мінімалізм' },
    { value: 'sakura', label: 'Сакура' },
    { value: 'forest', label: 'Ліс' },
    { value: 'terminal', label: 'Ретро-термінал' },
];
export const THEMES_AT_ONCE = 7;
export const OWN_THEME_LABEL = 'Свої кольори';

/**
 * A theme's variables: bg — the page; s0 bars; s1 cards and panels; s2 secondary buttons and fields; line;
 * tx text; strong headings; read the book's text; dim captions; faint the third plane; acc / onacc main
 * actions and the translation; acc2 / onacc2 the original, new, AI, «опубліковано»; term a glossary word;
 * selw selected; del / delbg and ins / insbg правки; skel the page's outline while loading; scrim; warn.
 */
export type ThemeVars = {
    bg: string; s0: string; s1: string; s2: string; line: string; tx: string; strong: string; read: string; dim: string; faint: string;
    acc: string; onacc: string; acc2: string; onacc2: string; term: string; selw: string; del: string; delbg: string; ins: string; insbg: string;
    skel: string; scrim: string; warn: string;
};
type Variants = { dark: ThemeVars; light: ThemeVars };

/** doc/redesign/mockup-src/lib.mjs, .th-* and .th-*.lt. */
const DRAWN: Record<'slanets' | 'graphite' | 'sukno' | 'slyva', Variants> = {
    slanets: {
        dark: { bg: '#1C1F25', s0: '#17191E', s1: '#22262D', s2: '#2A303A', line: '#333842', tx: '#D6DAE1', strong: '#EEF0F4', read: '#D9DCE2', dim: '#8D95A4', faint: '#636A77', acc: '#F2B35B', onacc: '#1D1404', acc2: '#5CCFBF', onacc2: '#0A1F1C', term: 'rgba(92,207,191,.16)', selw: 'rgba(242,179,91,.30)', del: '#F39479', delbg: 'rgba(243,148,121,.13)', ins: '#F5C27A', insbg: 'rgba(242,179,91,.16)', skel: '#2B3038', scrim: 'rgba(8,9,12,.62)', warn: '#F2B35B' },
        light: { bg: '#F3F4F6', s0: '#E8EAEE', s1: '#FFFFFF', s2: '#EEF1F5', line: '#D5D9E0', tx: '#1B1F27', strong: '#0B0E14', read: '#1E222A', dim: '#596070', faint: '#8C93A0', acc: '#A86200', onacc: '#FFFFFF', acc2: '#0B786B', onacc2: '#FFFFFF', term: '#D7F1EC', selw: '#F7D9A6', del: '#B23A17', delbg: '#FBE4DC', ins: '#7A4600', insbg: '#FCEBCF', skel: '#E3E6EB', scrim: 'rgba(20,24,32,.35)', warn: '#8A5200' },
    },
    graphite: {
        dark: { bg: '#0F1115', s0: '#0B0D10', s1: '#171A20', s2: '#1F232B', line: '#2A2F39', tx: '#E8EAEE', strong: '#F1F2F5', read: '#DADDE3', dim: '#A3A9B6', faint: '#6F7584', acc: '#8EA6FF', onacc: '#0B0D12', acc2: '#F2C46B', onacc2: '#1A1405', term: 'rgba(255,196,64,.20)', selw: 'rgba(142,166,255,.28)', del: '#FFB48A', delbg: 'rgba(255,140,80,.18)', ins: '#D3DCFF', insbg: 'rgba(142,166,255,.22)', skel: '#23272F', scrim: 'rgba(5,6,9,.62)', warn: '#F2C46B' },
        light: { bg: '#F3F4F6', s0: '#E9EBEF', s1: '#FFFFFF', s2: '#F1F2F5', line: '#E3E5EA', tx: '#15171C', strong: '#0B0D12', read: '#1B1D22', dim: '#5B6170', faint: '#8A909C', acc: '#2A4BD7', onacc: '#FFFFFF', acc2: '#7A4B00', onacc2: '#FFFFFF', term: '#FFF1C2', selw: '#DCE6FF', del: '#7A3410', delbg: '#FDE6D3', ins: '#102A8C', insbg: '#DCE6FF', skel: '#E6E8EC', scrim: 'rgba(15,17,21,.35)', warn: '#7A4B00' },
    },
    sukno: {
        dark: { bg: '#0E1613', s0: '#0A110E', s1: '#131E1A', s2: '#1A2722', line: '#2A3A33', tx: '#E9E2CC', strong: '#F4EEDC', read: '#DCD5BE', dim: '#A9A58F', faint: '#767B6C', acc: '#D2A84E', onacc: '#17120A', acc2: '#9CC7A4', onacc2: '#0E1613', term: 'rgba(210,168,78,.17)', selw: 'rgba(210,168,78,.34)', del: '#E59072', delbg: 'rgba(229,144,114,.14)', ins: '#EBCB7C', insbg: 'rgba(210,168,78,.14)', skel: '#22302A', scrim: 'rgba(4,8,6,.62)', warn: '#D2A84E' },
        light: { bg: '#E6E9E1', s0: '#DCE1D6', s1: '#F7F8F2', s2: '#EDEFE6', line: '#C9D0C2', tx: '#18221D', strong: '#0C130F', read: '#1E2621', dim: '#535E4F', faint: '#7F877A', acc: '#85601A', onacc: '#FFFFFF', acc2: '#2F6B45', onacc2: '#FFFFFF', term: '#F1E2B7', selw: '#E6CB82', del: '#A4431E', delbg: '#F6DDD2', ins: '#6A4A0C', insbg: '#F1E2B7', skel: '#DDE2D6', scrim: 'rgba(14,22,19,.35)', warn: '#85601A' },
    },
    slyva: {
        dark: { bg: '#191120', s0: '#140D1A', s1: '#241830', s2: '#2F2140', line: '#3F2E50', tx: '#F7ECF4', strong: '#FFF6FB', read: '#EEE1EA', dim: '#BCA7BE', faint: '#8E7A94', acc: '#FF7EA4', onacc: '#2B0716', acc2: '#F4E27C', onacc2: '#2A2306', term: 'rgba(255,126,164,.2)', selw: 'rgba(255,126,164,.36)', del: '#FFB08F', delbg: 'rgba(255,140,100,.16)', ins: '#F4E27C', insbg: 'rgba(244,226,124,.15)', skel: '#2E2240', scrim: 'rgba(10,4,14,.62)', warn: '#F4E27C' },
        light: { bg: '#FFF4F7', s0: '#FBE9EF', s1: '#FFFFFF', s2: '#FCE7EF', line: '#F1D2DE', tx: '#2A1430', strong: '#1A0A1F', read: '#2A1430', dim: '#6D5370', faint: '#9F89A1', acc: '#CF2E66', onacc: '#FFFFFF', acc2: '#7C6300', onacc2: '#FFFFFF', term: '#FFD9E5', selw: '#FFC2D5', del: '#A93A12', delbg: '#FFE3D6', ins: '#6B5400', insbg: '#FFF1B8', skel: '#F6DCE6', scrim: 'rgba(42,20,48,.35)', warn: '#7C6300' },
    },
};

/** The old styles' own base, tone and accent (model.ts LEGACY_STYLES says the same for older looks). */
const FROM_STYLES: Record<Exclude<ThemeId, keyof typeof DRAWN>, ['light' | 'dark', number, string]> = {
    light: ['light', 0, '#4f7a40'], bookish: ['light', 2, '#2f4a6b'], parchment: ['light', 3, '#7a5a2b'], night: ['dark', 1, '#8ea6ff'],
    contrast: ['dark', 1, '#ffd400'], minimal: ['light', 1, '#111111'], sakura: ['light', 4, '#c2456b'], ocean: ['dark', 2, '#4fb3d9'],
    forest: ['dark', 3, '#d8b45a'], neon: ['dark', 4, '#ff3ea5'], terminal: ['dark', 5, '#8cff9c'],
};

/** A tone of the site and an accent; the rest from Графіт of the same variant. */
function fromTone(variant: 'dark' | 'light', bg: number, accent: string): ThemeVars {
    const tone = SITE_TONES[variant][bg] ?? SITE_TONES[variant][0]!;
    return {
        ...DRAWN.graphite[variant], bg: tone.bg, s1: tone.surface, s2: tone.surface2, line: tone.line, tx: tone.text, read: tone.reading,
        dim: tone.muted, faint: tone.faint, acc: accent, onacc: inkOn(accent),
    };
}

/** The colours of a theme (or of «Свої кольори») in its dark or light variant. */
export function themeVars(theme: ThemeId | 'own', own: OwnColors, light: boolean): ThemeVars {
    const variant = light ? 'light' : 'dark';
    if (theme === 'own') return fromTone(variant, own.bg, own.accent);
    if (theme in DRAWN) return DRAWN[theme as keyof typeof DRAWN][variant];
    const [, bg, accent] = FROM_STYLES[theme as keyof typeof FROM_STYLES] ?? FROM_STYLES.light;
    return fromTone(variant, bg, accent);
}
