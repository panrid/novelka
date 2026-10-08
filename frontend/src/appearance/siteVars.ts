import { FONT_STACKS } from './fonts';
import { SITE_TONES, inkOn, type SiteLook } from './model';

/**
 * The CSS variables of a site look (styles/tokens.css names them). They go on <html>, so every
 * page and component that reads the tokens follows; the browser keeps a copy so the next visit
 * starts in this look before the app has loaded (public/appearance-boot.js).
 */
export function siteVars(look: SiteLook): Record<string, string> {
    const tone = SITE_TONES[look.base][look.bg] ?? SITE_TONES[look.base][0]!;
    const dark = look.base === 'dark';
    const r = look.radius;
    const density = { compact: 0.8, normal: 1, airy: 1.25 }[look.density];
    const vars: Record<string, string> = {
        '--bg': tone.bg,
        '--surface': tone.surface,
        '--surface-2': tone.surface2,
        '--line': tone.line,
        '--text': tone.text,
        '--text-reading': tone.reading,
        '--muted': tone.muted,
        '--faint': tone.faint,
        '--accent': look.accent,
        '--accent-ink': inkOn(look.accent),
        '--focus': look.accent,
        '--font-ui': FONT_STACKS[look.ui],
        '--font-reading': FONT_STACKS[look.head],
        '--radius-xs': `${Math.round(r * 0.5)}px`,
        '--radius-sm': `${Math.round(r * 0.7)}px`,
        '--radius': `${r}px`,
        '--radius-lg': `${Math.round(r * 1.5)}px`,
        '--density': String(density),
        '--icon-color': look.iconColor === 'accent' ? look.accent : look.iconColor === 'muted' ? tone.muted : 'currentColor',
        '--shelf': dark ? '#3a2e22' : '#b08b5e',
        '--gutter': `${Math.round(16 * density)}px`,
        '--card-border': look.cards === 'border' ? `1px solid ${tone.line}` : '1px solid transparent',
        '--card-shadow': look.cards === 'shadow'
            ? (dark ? `0 6px 18px rgb(0 0 0 / 45%), 0 0 0 1px ${tone.line}` : '0 6px 16px rgb(40 30 20 / 12%)')
            : 'none',
    };
    if (look.preset === 'neon' && !look.custom) {
        vars['--card-shadow'] = `0 0 0 1px ${look.accent}55, 0 0 18px ${look.accent}33`;
    }
    return vars;
}

/** Colours only: the reader's own palette replaces these while a chapter is open. */
export const COLOR_VARS = ['--bg', '--surface', '--surface-2', '--line', '--text', '--text-reading', '--muted', '--faint',
    '--accent', '--accent-ink', '--focus', '--card-border', '--card-shadow', '--icon-color'];
