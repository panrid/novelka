import { useEffect } from 'react';

/** A field the on-screen keyboard is open for. */
function typing(): boolean {
    const element = document.activeElement as HTMLElement | null;
    if (!element) return false;
    return element.isContentEditable || element.tagName === 'TEXTAREA'
        || (element.tagName === 'INPUT' && !['checkbox', 'radio', 'button', 'submit', 'range'].includes((element as HTMLInputElement).type));
}

/**
 * Keeps two CSS variables on <html> in step with the on-screen keyboard: --keyboard-inset (how
 * much of the bottom it covers) and --visible-height (what is left). Sheets use them to stay
 * above the keyboard, so the text being edited is where a finger can reach it.
 *
 * Safari on iOS 26 may leave visualViewport.offsetTop above zero after the keyboard closes
 * (WebKit bug 297779), and fixed bars (the reader's buttons) then hang mid-screen. Without a
 * field in focus there is no keyboard, so no inset is taken, and a scroll to where the page
 * already is makes Safari put its viewport back.
 */
export function useKeyboardInset() {
    useEffect(() => {
        const viewport = window.visualViewport;
        if (!viewport) return;
        const root = document.documentElement;
        let settle: ReturnType<typeof setTimeout> | undefined;
        const update = () => {
            const inset = typing() ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0;
            root.style.setProperty('--keyboard-inset', `${Math.round(inset)}px`);
            root.style.setProperty('--visible-height', `${Math.round(typing() ? viewport.height : window.innerHeight)}px`);
        };
        const putBack = () => {
            clearTimeout(settle);
            settle = setTimeout(() => {
                if (!typing() && viewport.offsetTop > 0 && viewport.scale <= 1.01) {
                    window.scrollTo(window.scrollX, window.scrollY);
                }
                update();
            }, 250);
        };
        const onResize = () => {
            update();
            if (!typing()) putBack();
        };
        update();
        viewport.addEventListener('resize', onResize);
        viewport.addEventListener('scroll', update);
        document.addEventListener('focusin', update);
        document.addEventListener('focusout', putBack);
        return () => {
            clearTimeout(settle);
            viewport.removeEventListener('resize', onResize);
            viewport.removeEventListener('scroll', update);
            document.removeEventListener('focusin', update);
            document.removeEventListener('focusout', putBack);
        };
    }, []);
}
