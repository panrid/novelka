import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';

interface Option { value: string; label: string }

export function SelectField({ label, value, options, onChange, hideLabel = false }: {
    label: string; value: string; options: Option[]; onChange: (value: string) => void; hideLabel?: boolean;
}) {
    const id = useId();
    const button = useRef<HTMLButtonElement>(null);
    const popup = useRef<HTMLDivElement>(null);
    const search = useRef({ text: '', time: 0 });
    const [open, setOpen] = useState(false);
    const [active, setActive] = useState(0);
    const [position, setPosition] = useState<CSSProperties>({});
    const selected = options.findIndex(option => option.value === value);
    const show = () => { setActive(Math.max(0, selected)); setOpen(true); };
    const choose = (index: number) => {
        if (options[index]) onChange(options[index].value);
        setOpen(false);
        button.current?.focus();
    };

    useLayoutEffect(() => {
        if (!open) return;
        const place = () => {
            const rect = button.current!.getBoundingClientRect();
            const below = window.innerHeight - rect.bottom - 12;
            const above = rect.top - 12;
            const upwards = below < 180 && above > below;
            const width = Math.min(rect.width, window.innerWidth - 16);
            setPosition({ width, left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
                maxHeight: Math.max(44, Math.min(280, upwards ? above : below)),
                ...(upwards ? { bottom: window.innerHeight - rect.top + 4 } : { top: rect.bottom + 4 }) });
        };
        place();
        window.addEventListener('resize', place);
        window.addEventListener('scroll', place, true);
        return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
    }, [open]);

    useEffect(() => {
        if (!open) return;
        const dismiss = (event: PointerEvent) => {
            if (!button.current?.contains(event.target as Node) && !popup.current?.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener('pointerdown', dismiss);
        return () => document.removeEventListener('pointerdown', dismiss);
    }, [open]);

    useEffect(() => {
        if (open) document.getElementById(`${id}-option-${active}`)?.scrollIntoView({ block: 'nearest' });
    }, [open, active, id]);

    const keyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
        if (event.key === 'Escape') { event.preventDefault(); setOpen(false); return; }
        if (event.key === 'Tab') { setOpen(false); return; }
        if (['Enter', ' '].includes(event.key)) {
            event.preventDefault();
            if (open) choose(active); else show();
            return;
        }
        if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
            event.preventDefault();
            const start = open ? active : Math.max(0, selected);
            setActive(event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
                : Math.max(0, Math.min(options.length - 1, start + (event.key === 'ArrowDown' ? 1 : -1))));
            setOpen(true);
        } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
            event.preventDefault();
            const now = Date.now();
            search.current = { text: (now - search.current.time < 700 ? search.current.text : '') + event.key.toLocaleLowerCase('uk'), time: now };
            const match = options.findIndex(option => option.label.toLocaleLowerCase('uk').startsWith(search.current.text));
            if (match >= 0) { setActive(match); setOpen(true); }
        }
    };

    return <div className="select-field">
        <label id={`${id}-label`} htmlFor={id} className={hideLabel ? 'sr-only' : undefined}>{label}</label>
        <button ref={button} id={id} type="button" role="combobox" className="select-trigger"
            aria-labelledby={`${id}-label`} aria-expanded={open} aria-haspopup="listbox"
            aria-controls={open ? `${id}-list` : undefined} aria-activedescendant={open ? `${id}-option-${active}` : undefined}
            onClick={() => open ? setOpen(false) : show()} onKeyDown={keyDown} onBlur={() => setOpen(false)}>
            <span>{options[selected]?.label || 'Оберіть значення'}</span><span aria-hidden="true" className="select-chevron">⌄</span>
        </button>
        {open && createPortal(<div ref={popup} id={`${id}-list`} role="listbox" aria-labelledby={`${id}-label`}
            className="select-popup" style={position}>
            {options.map((option, index) => <div id={`${id}-option-${index}`} key={option.value} role="option"
                aria-selected={option.value === value} data-active={index === active} className="select-option"
                onPointerMove={() => setActive(index)} onPointerDown={event => event.preventDefault()} onClick={() => choose(index)}>
                <span>{option.label}</span><span aria-hidden="true">{option.value === value ? '✓' : ''}</span>
            </div>)}
        </div>, document.body)}
    </div>;
}
