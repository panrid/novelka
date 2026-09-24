import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';

export interface Suggestion { value: string; label: string; hint?: string }

/**
 * Text field with a site-styled suggestion list instead of a native datalist, whose popup the browser draws
 * (Safari shows its own menu). Free text is always allowed; arrows move, Enter picks, Escape closes.
 */
export function Autocomplete({ id, value, onChange, onPick, suggestions, placeholder, maxLength, required, describedBy, onEnter }: {
    id: string; value: string; onChange: (value: string) => void; onPick: (suggestion: Suggestion) => void;
    suggestions: Suggestion[]; placeholder?: string; maxLength?: number; required?: boolean; describedBy?: string;
    /** Enter without a highlighted suggestion, e.g. to add the typed tag. */
    onEnter?: () => void;
}) {
    const listId = useId();
    const input = useRef<HTMLInputElement>(null);
    const popup = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState(false);
    const [active, setActive] = useState(-1);
    const [position, setPosition] = useState<CSSProperties>({});
    const shown = open && suggestions.length > 0;

    useLayoutEffect(() => {
        if (!shown) return;
        const place = () => {
            const rect = input.current!.getBoundingClientRect();
            const viewport = window.visualViewport;
            const bottom = (viewport?.offsetTop ?? 0) + (viewport?.height ?? window.innerHeight);
            setPosition({ top: rect.bottom + 4, left: rect.left, width: rect.width, maxHeight: Math.max(120, Math.min(300, bottom - rect.bottom - 16)) });
        };
        place();
        window.addEventListener('resize', place);
        window.addEventListener('scroll', place, true);
        return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
    }, [shown]);

    useEffect(() => { setActive(-1); }, [suggestions]);
    useEffect(() => {
        if (shown && active >= 0) document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: 'nearest' });
    }, [shown, active, listId]);

    const pick = (suggestion: Suggestion) => { onPick(suggestion); setOpen(false); };
    const keyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            setOpen(true);
            const step = event.key === 'ArrowDown' ? 1 : -1;
            setActive(current => suggestions.length ? (current + step + suggestions.length) % suggestions.length : -1);
        } else if (event.key === 'Enter') {
            if (shown && active >= 0) { event.preventDefault(); pick(suggestions[active]); }
            else if (onEnter) { event.preventDefault(); onEnter(); setOpen(false); }
        } else if (event.key === 'Escape' && shown) { event.preventDefault(); setOpen(false); }
    };

    return <>
        <input ref={input} id={id} value={value} placeholder={placeholder} maxLength={maxLength} required={required} autoComplete="off" spellCheck={false}
            role="combobox" aria-autocomplete="list" aria-expanded={shown} aria-controls={shown ? listId : undefined}
            aria-activedescendant={shown && active >= 0 ? `${listId}-${active}` : undefined} aria-describedby={describedBy}
            onChange={event => { onChange(event.target.value); setOpen(true); }} onFocus={() => setOpen(true)}
            onBlur={() => setOpen(false)} onKeyDown={keyDown} />
        {shown && createPortal(<div ref={popup} id={listId} role="listbox" className="select-popup autocomplete-popup" style={position}>
            {suggestions.map((suggestion, index) => <div key={suggestion.value} id={`${listId}-${index}`} role="option" aria-selected={index === active}
                data-active={index === active} className="select-option" onPointerMove={() => setActive(index)}
                onPointerDown={event => event.preventDefault()} onClick={() => pick(suggestion)}>
                <span>{suggestion.label}</span>{suggestion.hint && <small className="muted">{suggestion.hint}</small>}
            </div>)}
        </div>, document.body)}
    </>;
}
