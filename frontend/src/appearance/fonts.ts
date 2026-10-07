import type { FontId } from './model';

/**
 * Open fonts with Ukrainian letters, served from the site itself (the CSP allows no other
 * font host). Inter and Literata are always there; the others load when someone picks them.
 */
export const FONT_STACKS: Record<FontId, string> = {
    literata: "'Literata Variable', Georgia, serif",
    ptserif: "'PT Serif', Georgia, serif",
    lora: "'Lora Variable', Georgia, serif",
    merriweather: "'Merriweather', Georgia, serif",
    playfair: "'Playfair Display Variable', Georgia, serif",
    plex: "'IBM Plex Sans Variable', -apple-system, sans-serif",
    comfortaa: "'Comfortaa Variable', -apple-system, sans-serif",
    inter: "'Inter Variable', -apple-system, 'Segoe UI', sans-serif",
    nunito: "'Nunito Variable', -apple-system, sans-serif",
    rubik: "'Rubik Variable', -apple-system, sans-serif",
    jetbrains: "'JetBrains Mono Variable', ui-monospace, monospace",
};

const LOADERS: Partial<Record<FontId, () => Promise<unknown>>> = {
    ptserif: () => Promise.all([import('@fontsource/pt-serif/400.css'), import('@fontsource/pt-serif/400-italic.css'), import('@fontsource/pt-serif/700.css')]),
    lora: () => Promise.all([import('@fontsource-variable/lora'), import('@fontsource-variable/lora/wght-italic.css')]),
    merriweather: () => Promise.all([import('@fontsource/merriweather/400.css'), import('@fontsource/merriweather/400-italic.css'), import('@fontsource/merriweather/700.css')]),
    nunito: () => Promise.all([import('@fontsource-variable/nunito'), import('@fontsource-variable/nunito/wght-italic.css')]),
    rubik: () => Promise.all([import('@fontsource-variable/rubik'), import('@fontsource-variable/rubik/wght-italic.css')]),
    jetbrains: () => import('@fontsource-variable/jetbrains-mono'),
    playfair: () => Promise.all([import('@fontsource-variable/playfair-display'), import('@fontsource-variable/playfair-display/wght-italic.css')]),
    plex: () => Promise.all([import('@fontsource-variable/ibm-plex-sans'), import('@fontsource-variable/ibm-plex-sans/wght-italic.css')]),
    comfortaa: () => import('@fontsource-variable/comfortaa'),
};

const loaded = new Set<FontId>();

export function loadFont(font: FontId): Promise<unknown> {
    const load = LOADERS[font];
    if (!load || loaded.has(font)) return Promise.resolve();
    loaded.add(font);
    return load().catch(() => loaded.delete(font));
}
