import { useCallback, useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react';

export type PageTurn = 'none' | 'slide' | 'fade' | 'curl';

/**
 * The chapter laid out as pages (етап 18): the text flows into columns as wide as the screen and
 * a page is one column. Turning moves to the next column; past the last page the reader goes
 * on to the next chapter. The place is kept as a share of the pages, like the scroll position.
 */
export function usePages({ enabled, frame, text, layoutKey, position, turnStyle, onPage, onBeforeStart, onAfterEnd }: {
    enabled: boolean;
    /** The visible window; its width is the width of a page. */
    frame: RefObject<HTMLElement | null>;
    text: RefObject<HTMLElement | null>;
    /** Anything that changes how much fits on a page (font, size, width…). */
    layoutKey: string;
    /** Where to open, 0–1; read again whenever the layout changes. */
    position: () => number;
    turnStyle: PageTurn;
    onPage: (position: number) => void;
    onBeforeStart: () => void;
    onAfterEnd: () => void;
}) {
    const [layout, setLayout] = useState({ page: 0, pages: 1, step: 0, width: 0 });
    const [effect, setEffect] = useState<'' | 'fadeOut' | 'curlNext' | 'curlBack'>('');
    const busy = useRef(false);

    useLayoutEffect(() => {
        if (!enabled) return;
        const measure = () => {
            const box = frame.current;
            const body = text.current;
            if (!box || !body) return;
            const width = box.clientWidth;
            const gap = parseFloat(getComputedStyle(body).columnGap) || 0;
            // The columns are as wide as the window (the text's columnWidth, set from `width`).
            const columns = parseFloat(getComputedStyle(body).columnWidth) || 0;
            if (Math.abs(columns - width) > 0.5) {
                setLayout((current) => ({ ...current, width }));
                return;
            }
            const pages = Math.max(1, Math.round((body.scrollWidth + gap) / (width + gap)));
            const page = Math.min(pages - 1, Math.max(0, Math.round(position() * (pages - 1))));
            setLayout({ page, pages, step: width + gap, width });
        };
        measure();
        // Older browsers have no ResizeObserver: a turned phone or a resized window still counts.
        const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
        if (observer && frame.current) observer.observe(frame.current);
        else window.addEventListener('resize', measure);
        void document.fonts?.ready.then(measure);
        return () => {
            observer?.disconnect();
            window.removeEventListener('resize', measure);
        };
        // position() is read on purpose only when the layout changes.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, layoutKey, frame, text, layout.width]);

    const turn = useCallback((direction: 1 | -1) => {
        if (busy.current) return;
        const next = layout.page + direction;
        if (next < 0) return onBeforeStart();
        if (next >= layout.pages) return onAfterEnd();
        const show = () => {
            setLayout((current) => ({ ...current, page: next }));
            onPage(layout.pages > 1 ? next / (layout.pages - 1) : 1);
        };
        if (turnStyle === 'fade') {
            busy.current = true;
            setEffect('fadeOut');
            setTimeout(() => { show(); setEffect(''); busy.current = false; }, 160);
        } else if (turnStyle === 'curl') {
            busy.current = true;
            setEffect(direction > 0 ? 'curlNext' : 'curlBack');
            setTimeout(show, direction > 0 ? 220 : 0);
            setTimeout(() => { setEffect(''); busy.current = false; }, 460);
        } else {
            show();
        }
    }, [layout, turnStyle, onPage, onBeforeStart, onAfterEnd]);

    // A swipe turns the page too.
    useEffect(() => {
        const box = frame.current;
        if (!enabled || !box) return;
        let startX: number | null = null;
        let startY = 0;
        const down = (event: TouchEvent) => {
            startX = event.touches[0]?.clientX ?? null;
            startY = event.touches[0]?.clientY ?? 0;
        };
        const up = (event: TouchEvent) => {
            if (startX === null) return;
            const dx = (event.changedTouches[0]?.clientX ?? startX) - startX;
            const dy = (event.changedTouches[0]?.clientY ?? startY) - startY;
            startX = null;
            if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy)) turn(dx < 0 ? 1 : -1);
        };
        box.addEventListener('touchstart', down, { passive: true });
        box.addEventListener('touchend', up, { passive: true });
        return () => {
            box.removeEventListener('touchstart', down);
            box.removeEventListener('touchend', up);
        };
    }, [enabled, frame, turn]);

    return { ...layout, turn, effect, animated: turnStyle === 'slide' };
}
