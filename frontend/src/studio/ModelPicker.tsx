import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { plural } from '../lib/plural';
import { useDebounced } from '../lib/useDebounced';
import { autotranslateApi, dollars, type ModelChoice, type ModelShow } from './autotranslate';
import styles from './modelPicker.module.css';

/**
 * A model from OpenRouter's catalogue: type words of its name, pick from the list. Text models
 * show what their stage of an average chapter costs (or the price per million tokens when no
 * stage is given); models that draw show about what a picture costs. Dear models are listed
 * too: the quote then asks more шаги.
 */
export function ModelPicker({ label, value, onChange, chars = 6000, stage, show = 'usual', output = 'text', hint }: {
    label: string; value: string; onChange: (model: string, choice: ModelChoice) => void; chars?: number;
    stage?: 'analyze' | 'translate' | 'proofread' | undefined; show?: ModelShow; output?: 'text' | 'image'; hint?: string | undefined;
}) {
    const [text, setText] = useState(value);
    const [open, setOpen] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const query = useDebounced(text, 300);
    const models = useQuery({
        queryKey: ['models', query, chars, stage, show, output],
        queryFn: () => autotranslateApi.models(query === value ? '' : query, chars, output, stage, show),
        enabled: open,
        staleTime: 5 * 60_000,
    });
    const options = open ? models.data ?? [] : [];
    const choose = (choice: ModelChoice) => {
        setText(choice.id);
        onChange(choice.id, choice);
        setOpen(false);
    };
    const price = (model: ModelChoice) => output === 'image' ? `≈ ${dollars(model.chapterUsd, 3)} за картинку`
        : stage ? `≈ ${dollars(model.chapterUsd, 3)} за главу`
            : `${dollars(model.inputPerMillion)} / ${dollars(model.outputPerMillion)} за 1 млн`;
    const id = `model-${label.replace(/\s+/g, '-')}`;
    // The list is long: keep the option chosen with the arrows in sight.
    useEffect(() => {
        document.getElementById(`${id}-${highlight}`)?.scrollIntoView?.({ block: 'nearest' });
    }, [id, highlight]);
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
                        choose(options[highlight]!);
                    } else if (event.key === 'Escape') {
                        setOpen(false);
                        setText(value);
                    }
                }} />
            {hint && <div className={styles.hint}>{hint}</div>}
            {options.length > 0 && (
                <ul id={`${id}-list`} className={styles.list} role="listbox" aria-label={label}>
                    <li role="presentation" className={styles.count}>{plural(options.length, 'модель', 'моделі', 'моделей')}</li>
                    {options.map((model, index) => (
                        <li key={model.id} id={`${id}-${index}`} role="option" aria-selected={index === highlight}
                            className={index === highlight ? styles.on : styles.option}
                            onMouseDown={(event) => { event.preventDefault(); choose(model); }}>
                            <span className={styles.name}>
                                {model.id}
                                {model.rating === 'recommended' && <span className={styles.good}> · рекомендована</span>}
                                {model.rating === 'weak' && <span className={styles.weak}> · слабка для перекладу</span>}
                            </span>
                            <span className={styles.price}>{price(model)}</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
