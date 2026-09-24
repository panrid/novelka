import { forwardRef, useEffect, useId, useImperativeHandle, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { getJson } from '../api/client';

interface Person { id: string; username: string }
const QUERY = /(?:^|[^A-Za-z0-9_@-])@([A-Za-z0-9_-]{1,40})$/;

/**
 * Textarea that suggests people after "@". Arrows move, Enter or Tab inserts "@nick ", Escape closes.
 * Other keys go to {@code onKeyDown}, so a chat can still send with Enter when no suggestion list is open.
 */
export const MentionTextarea = forwardRef<HTMLTextAreaElement, {
    id: string; value: string; onChange: (value: string) => void; rows?: number; maxLength?: number; placeholder?: string;
    required?: boolean; onKeyDown?: (event: KeyboardEvent<HTMLTextAreaElement>) => void;
}>(function MentionTextarea({ id, value, onChange, rows = 3, maxLength, placeholder, required, onKeyDown }, forwarded) {
    const listId = useId();
    const area = useRef<HTMLTextAreaElement>(null);
    useImperativeHandle(forwarded, () => area.current!);
    const [query, setQuery] = useState<string | null>(null);
    const [people, setPeople] = useState<Person[]>([]);
    const [active, setActive] = useState(0);
    const [position, setPosition] = useState<CSSProperties>({});
    const shown = query !== null && people.length > 0;
    // Where the caret goes after a suggestion is inserted; applied as soon as the new value renders, before the next key.
    const pendingCaret = useRef<number | null>(null);
    useLayoutEffect(() => {
        if (pendingCaret.current === null || !area.current) return;
        area.current.setSelectionRange(pendingCaret.current, pendingCaret.current);
        pendingCaret.current = null;
    }, [value]);

    const detect = (text: string, caret: number) => {
        const match = text.slice(0, caret).match(QUERY);
        setQuery(match ? match[1].toLowerCase() : null);
    };
    useEffect(() => {
        if (!query) { setPeople([]); return; }
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            getJson<{ items: Person[] }>('/users/search?q=' + encodeURIComponent(query), controller.signal)
                .then(result => { setPeople(result.items); setActive(0); }).catch(() => {});
        }, 150);
        return () => { window.clearTimeout(timer); controller.abort(); };
    }, [query]);
    useLayoutEffect(() => {
        if (!shown) return;
        const rect = area.current!.getBoundingClientRect();
        const below = window.innerHeight - rect.bottom - 16, above = rect.top - 16;
        setPosition(below < 160 && above > below
            ? { bottom: window.innerHeight - rect.top + 4, left: rect.left, width: Math.min(rect.width, 320), maxHeight: Math.min(260, above) }
            : { top: rect.bottom + 4, left: rect.left, width: Math.min(rect.width, 320), maxHeight: Math.max(120, Math.min(260, below)) });
    }, [shown, people]);

    const insert = (person: Person) => {
        const element = area.current!;
        const caret = element.selectionStart;
        const start = value.slice(0, caret).lastIndexOf('@');
        const next = value.slice(0, start) + '@' + person.username + ' ' + value.slice(caret);
        pendingCaret.current = start + person.username.length + 2;
        onChange(next);
        setQuery(null);
        element.focus();
    };
    const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
        if (shown) {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                setActive(current => (current + (event.key === 'ArrowDown' ? 1 : -1) + people.length) % people.length);
                return;
            }
            if (event.key === 'Enter' || event.key === 'Tab') { event.preventDefault(); insert(people[active]); return; }
            if (event.key === 'Escape') { event.preventDefault(); setQuery(null); return; }
        }
        onKeyDown?.(event);
    };
    return <>
        <textarea ref={area} id={id} rows={rows} maxLength={maxLength} placeholder={placeholder} required={required} value={value}
            role="combobox" aria-autocomplete="list" aria-expanded={shown} aria-controls={shown ? listId : undefined}
            aria-activedescendant={shown ? `${listId}-${active}` : undefined}
            onChange={event => { onChange(event.target.value); detect(event.target.value, event.target.selectionStart); }}
            onClick={event => detect(event.currentTarget.value, event.currentTarget.selectionStart)}
            onBlur={() => setQuery(null)} onKeyDown={keyDown} />
        {shown && createPortal(<div id={listId} role="listbox" aria-label="Кого згадати" className="select-popup autocomplete-popup" style={position}>
            {people.map((person, index) => <div key={person.id} id={`${listId}-${index}`} role="option" aria-selected={index === active}
                data-active={index === active} className="select-option" onPointerMove={() => setActive(index)}
                onPointerDown={event => event.preventDefault()} onClick={() => insert(person)}>@{person.username}</div>)}
        </div>, document.body)}
    </>;
});
