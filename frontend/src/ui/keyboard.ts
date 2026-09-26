import { useEffect } from 'react';

/**
 * Keeps two CSS variables on <html> in step with the on-screen keyboard: --keyboard-inset (how
 * much of the bottom it covers) and --visible-height (what is left). Sheets use them to stay
 * above the keyboard, so the text being edited is where a finger can reach it.
 */
export function useKeyboardInset() {
    useEffect(() => {
        const viewport = window.visualViewport;
        if (!viewport) return;
        const root = document.documentElement;
        const update = () => {
            const inset = Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop);
            root.style.setProperty('--keyboard-inset', `${Math.round(inset)}px`);
            root.style.setProperty('--visible-height', `${Math.round(viewport.height)}px`);
        };
        update();
        viewport.addEventListener('resize', update);
        viewport.addEventListener('scroll', update);
        return () => {
            viewport.removeEventListener('resize', update);
            viewport.removeEventListener('scroll', update);
        };
    }, []);
}
