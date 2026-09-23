import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/**
 * Short explanation behind a "?" button. Click, tap, Enter or Space toggles it; a mouse may also hover.
 * The bubble is portalled so scrollable tables and cards do not clip it.
 */
export function HelpTip({ label, children }: { label: string; children: ReactNode }) {
    const id = useId();
    const button = useRef<HTMLButtonElement>(null);
    const bubble = useRef<HTMLSpanElement>(null);
    const [open, setOpen] = useState<'click' | 'hover' | null>(null);
    const [position, setPosition] = useState<CSSProperties>({ visibility: 'hidden' });

    useLayoutEffect(() => {
        if (!open) return;
        const place = () => {
            const rect = button.current!.getBoundingClientRect();
            // Measure against the visible area: on phones the layout viewport can be wider or taller.
            const viewport = window.visualViewport;
            const left = viewport?.offsetLeft ?? 0, right = left + (viewport?.width ?? document.documentElement.clientWidth);
            const width = Math.min(260, right - left - 16);
            const height = bubble.current?.offsetHeight ?? 0;
            const bottom = (viewport?.offsetTop ?? 0) + (viewport?.height ?? window.innerHeight);
            const upwards = rect.bottom + 6 + height > bottom - 8 && rect.top - 6 - height > 8;
            setPosition({ width, left: Math.max(left + 8, Math.min(rect.left + rect.width / 2 - width / 2, right - width - 8)),
                top: upwards ? rect.top - 6 - height : rect.bottom + 6 });
        };
        place();
        window.addEventListener('resize', place);
        window.addEventListener('scroll', place, true);
        return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
    }, [open]);

    useEffect(() => {
        if (!open) return;
        const dismiss = (event: PointerEvent) => {
            const target = event.target as Node;
            if (!button.current?.contains(target) && !bubble.current?.contains(target)) setOpen(null);
        };
        const escape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') { setOpen(null); button.current?.focus(); }
        };
        document.addEventListener('pointerdown', dismiss);
        document.addEventListener('keydown', escape);
        return () => { document.removeEventListener('pointerdown', dismiss); document.removeEventListener('keydown', escape); };
    }, [open]);

    return <span className="help-tip">
        <button ref={button} type="button" className="help-tip-button" aria-label={'Пояснення: ' + label}
            aria-expanded={!!open} aria-controls={open ? id : undefined}
            onClick={() => setOpen(value => value === 'click' ? null : 'click')}
            onPointerEnter={event => { if (event.pointerType === 'mouse') setOpen(value => value ?? 'hover'); }}
            onPointerLeave={event => { if (event.pointerType === 'mouse') setOpen(value => value === 'hover' ? null : value); }}>?</button>
        {open && createPortal(<span ref={bubble} id={id} role="note" className="help-tip-bubble" style={position}>{children}</span>, document.body)}
    </span>;
}
