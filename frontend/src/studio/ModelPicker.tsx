import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useDebounced } from '../lib/useDebounced';
import { autotranslateApi, dollars } from './autotranslate';
import styles from './modelPicker.module.css';

/**
 * A model from OpenRouter's catalogue: type part of its name, pick from the list; each
 * option says what an average chapter costs with it (analysis, translation, proofreading).
 */
export function ModelPicker({ label, value, onChange, chars, hint }: {
    label: string; value: string; onChange: (model: string) => void; chars: number; hint?: string | undefined;
}) {
    const [text, setText] = useState(value);
    const [open, setOpen] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const query = useDebounced(text, 300);
    const models = useQuery({
        queryKey: ['models', query, chars],
        queryFn: () => autotranslateApi.models(query === value ? '' : query, chars),
        enabled: open,
        staleTime: 5 * 60_000,
    });
    const options = open ? models.data ?? [] : [];
    const choose = (id: string) => {
        setText(id);
        onChange(id);
        setOpen(false);
    };
    const id = `model-${label.replace(/\s+/g, '-')}`;
    return (
        <div className={styles.picker}>
            <label className={styles.label} htmlFor={id}>{label}</label>
            <input id={id} className={styles.input} value={text} role="combobox" aria-expanded={options.length > 0}
                aria-controls={`${id}-list`} aria-autocomplete="list" autoComplete="off" spellCheck={false}
                onFocus={() => setOpen(true)}
                // Only a choice from the list counts: leaving the field half-typed keeps the model as it was.
                onBlur={() => { setOpen(false); setText(value); }}
                onChange={(event) => { setText(event.target.value); setOpen(true); setHighlight(0); }}
                onKeyDown={(event) => {
                    if (!options.length) return;
                    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                        event.preventDefault();
                        setHighlight((at) => (at + (event.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length);
                    } else if (event.key === 'Enter') {
                        event.preventDefault();
                        choose(options[highlight]!.id);
                    } else if (event.key === 'Escape') {
                        setOpen(false);
                        setText(value);
                    }
                }} />
            {hint && <div className={styles.hint}>{hint}</div>}
            {options.length > 0 && (
                <ul id={`${id}-list`} className={styles.list} role="listbox" aria-label={label}>
                    {options.map((model, index) => (
                        <li key={model.id} role="option" aria-selected={index === highlight}
                            className={index === highlight ? styles.on : styles.option}
                            onMouseDown={(event) => { event.preventDefault(); choose(model.id); }}>
                            <span className={styles.name}>{model.id}</span>
                            <span className={styles.price}>≈ {dollars(model.chapterUsd, 3)} за главу</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
