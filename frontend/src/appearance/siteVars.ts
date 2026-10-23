import { FONT_STACKS } from './fonts';
import type { SiteLook } from './model';
import type { ThemeVars } from './themes';

/**
 * The CSS variables of a site look (styles/tokens.css names them). They go on <html>, so every
 * page and component that reads the tokens follows; the browser keeps a copy so the next visit
 * starts in this look before the app has loaded (public/appearance-boot.js).
 *
 * The тема's colours go into today's tokens; fonts and shapes stay as they are until the каркас takes
 * them over with the new design's variables (#293).
 */
export function siteVars(look: SiteLook, theme: ThemeVars, light: boolean): Record<string, string> {
    return {
        '--bg': theme.bg,
        '--surface': theme.s1,
        '--surface-2': theme.s2,
        '--line': theme.line,
        '--text': theme.tx,
        '--text-reading': theme.read,
        '--muted': theme.dim,
        '--faint': theme.faint,
        '--accent': theme.acc,
        '--accent-ink': theme.onacc,
        '--focus': theme.acc,
        '--strong': theme.strong,
        '--term': theme.term,
        '--sel': theme.selw,
        '--del': theme.delbg,
        '--del-ink': theme.del,
        '--ins': theme.insbg,
        '--ins-ink': theme.ins,
        '--warn': theme.warn,
        '--font-ui': FONT_STACKS.golos,
        '--font-reading': FONT_STACKS.literata,
        '--radius-xs': '6px',
        '--radius-sm': '8px',
        '--radius': '12px',
        '--radius-lg': '18px',
        '--density': '1',
        '--portrait-radius': look.portraits === 'square' ? '8px' : '50%',
        '--icon-color': 'currentColor',
        '--shelf': light ? '#b08b5e' : '#3a2e22',
        '--gutter': '16px',
        '--card-border': '1px solid transparent',
        '--card-shadow': 'none',
    };
}

/** Colours only: the reader's own palette replaces these while a chapter is open. */
export const COLOR_VARS = ['--bg', '--surface', '--surface-2', '--line', '--text', '--text-reading', '--muted', '--faint',
    '--accent', '--accent-ink', '--focus', '--strong', '--term', '--sel', '--del', '--del-ink', '--ins', '--ins-ink', '--warn',
    '--card-border', '--card-shadow', '--icon-color'];
