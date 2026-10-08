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
    const [effect, setEffect] = useState<'' | 'fadeOut'>('');
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
            turnSheet(frame, text, layout.step, direction > 0 ? layout.page : next, direction > 0, show,
                () => { busy.current = false; });
        } else {
            show();
        }
    }, [layout, turnStyle, onPage, onBeforeStart, onAfterEnd, frame, text]);

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

const TURN_MS = 480;

/**
 * «Аркуш»: a copy of a page lies over the text and turns on its left edge like a book's leaf.
 * Forward, the current page turns away and shows the next one already under it; back, the
 * previous page turns in over the current one. {@code show} moves the text itself.
 */
function turnSheet(frame: RefObject<HTMLElement | null>, text: RefObject<HTMLElement | null>, step: number, page: number,
    forward: boolean, show: () => void, done: () => void) {
    const box = frame.current;
    const body = text.current;
    const host = box?.parentElement;
    const still = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!box || !body || !host || typeof host.animate !== 'function' || still) {
        show();
        done();
        return;
    }
    const sheet = document.createElement('div');
    sheet.setAttribute('aria-hidden', 'true');
    const hostStyle = getComputedStyle(host);
    Object.assign(sheet.style, {
        position: 'absolute', inset: '0', zIndex: '5', pointerEvents: 'none', overflow: 'hidden',
        paddingLeft: hostStyle.paddingLeft, paddingRight: hostStyle.paddingRight,
        background: getComputedStyle(document.body).backgroundColor, transformOrigin: 'left center',
        boxShadow: '0 0 28px rgb(0 0 0 / 30%)',
    });
    const leaf = document.createElement('div');
    Object.assign(leaf.style, { height: '100%', overflow: 'hidden' });
    const copy = body.cloneNode(true) as HTMLElement;
    copy.style.transition = 'none';
    copy.style.opacity = '1';
    copy.style.transform = `translateX(${-page * step}px)`;
    leaf.append(copy);
    // The fold darkens as the leaf turns away from the light.
    const shade = document.createElement('div');
    Object.assign(shade.style, {
        position: 'absolute', inset: '0', background: 'linear-gradient(to left, rgb(0 0 0 / 35%), rgb(0 0 0 / 5%) 70%)', opacity: '0',
    });
    sheet.append(leaf, shade);
    host.append(sheet);
    const flat = 'perspective(1800px) rotateY(0deg)';
    const away = 'perspective(1800px) rotateY(-92deg)';
    const options: KeyframeAnimationOptions = { duration: TURN_MS, easing: forward ? 'cubic-bezier(.45,.05,.55,.95)' : 'cubic-bezier(.2,.6,.35,1)', fill: 'forwards' };
    if (forward) show();
    const turning = sheet.animate([{ transform: forward ? flat : away }, { transform: forward ? away : flat }], options);
    shade.animate([{ opacity: forward ? 0 : 1 }, { opacity: forward ? 1 : 0 }], options);
    turning.onfinish = () => {
        if (!forward) show();
        // One frame for the text to move under the leaf before it goes.
        requestAnimationFrame(() => {
            sheet.remove();
            done();
        });
    };
}
