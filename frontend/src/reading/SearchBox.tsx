import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Search } from 'lucide-react';
import { useEffect, useId, useState } from 'react';
import { readingApi, type Card, type TagCount } from './api';
import { Cover } from './Cover';
import styles from './SearchBox.module.css';

type Option = { kind: 'novel'; card: Card } | { kind: 'tag'; tag: TagCount } | { kind: 'all' };

/**
 * A search field that hints at novels and tags while a person types (a combobox: arrows move
 * through the hints, Enter opens one, Escape hides them). Enter with no hint chosen searches the
 * catalog for the words.
 */
export function SearchBox({ value, onChange, onSubmit, label, placeholder, compact = false }: {
    value: string;
    onChange: (value: string) => void;
    /** Enter on the words themselves. */
    onSubmit: (words: string) => void;
    label: string;
    placeholder?: string;
    /** The header's small field. */
    compact?: boolean;
}) {
    const navigate = useNavigate();
    const id = useId();
    const [open, setOpen] = useState(false);
    const [active, setActive] = useState(-1);
    const words = useDebounced(value.trim(), 200);
    const hints = useQuery({
        queryKey: ['hints', words],
        queryFn: () => readingApi.hints(words),
        enabled: open && words.length > 0,
        staleTime: 60_000,
        placeholderData: keepPreviousData,
    });

    const options: Option[] = words.length === 0 || !hints.data ? [] : [
        ...hints.data.tags.map((tag): Option => ({ kind: 'tag', tag })),
        ...hints.data.novels.map((card): Option => ({ kind: 'novel', card })),
        ...(hints.data.novels.length > 0 || hints.data.tags.length > 0 ? [{ kind: 'all' } as Option] : []),
    ];
    const shown = open && value.trim().length > 0 && options.length > 0;

    const choose = (option: Option) => {
        setOpen(false);
        setActive(-1);
        if (option.kind === 'novel') {
            onChange('');
            void navigate({ to: '/n/$slug', params: { slug: option.card.novelSlug }, search: { t: option.card.teamHandle } });
        } else if (option.kind === 'tag') {
            onChange('');
            void navigate({ to: '/catalog', search: { tags: [option.tag.slug] } });
        } else {
            onSubmit(value.trim());
        }
    };

    return (
        <div className={`${styles.box} ${compact ? styles.compact : ''}`}>
            <form role="search" className={styles.field} onSubmit={(event) => {
                event.preventDefault();
                if (shown && active >= 0 && options[active]) choose(options[active]);
                else {
                    setOpen(false);
                    onSubmit(value.trim());
                }
            }}>
                <Search size={compact ? 16 : 18} aria-hidden className={styles.icon} />
                <input
                    type="search"
                    role="combobox"
                    aria-label={label}
                    aria-expanded={shown}
                    aria-controls={`${id}-hints`}
                    aria-autocomplete="list"
                    aria-activedescendant={shown && active >= 0 ? `${id}-${active}` : undefined}
                    autoComplete="off"
                    placeholder={placeholder}
                    value={value}
                    onChange={(event) => {
                        onChange(event.target.value);
                        setOpen(true);
                        setActive(-1);
                    }}
                    onFocus={() => setOpen(true)}
                    // A hint is pressed before the field loses focus, so closing waits a moment.
                    onBlur={() => setTimeout(() => setOpen(false), 150)}
                    onKeyDown={(event) => {
                        if (event.key === 'ArrowDown' && options.length > 0) {
                            event.preventDefault();
                            setOpen(true);
                            setActive((active + 1) % options.length);
                        } else if (event.key === 'ArrowUp' && options.length > 0) {
                            event.preventDefault();
                            setActive(active <= 0 ? options.length - 1 : active - 1);
                        } else if (event.key === 'Escape') {
                            setOpen(false);
                            setActive(-1);
                        }
                    }}
                />
            </form>
            {shown && (
                <ul id={`${id}-hints`} role="listbox" aria-label="Підказки" className={styles.hints}>
                    {options.map((option, index) => (
                        <li key={key(option)} id={`${id}-${index}`} role="option" aria-selected={index === active}
                            className={`${styles.hint} ${index === active ? styles.active : ''}`}
                            onMouseDown={(event) => event.preventDefault()}
                            onMouseEnter={() => setActive(index)}
                            onClick={() => choose(option)}>
                            {option.kind === 'tag' && (
                                <>
                                    <span className={styles.tag}>#</span>
                                    <span className={styles.grow}>{option.tag.name}</span>
                                    <span className={styles.muted}>{option.tag.novels}</span>
                                </>
                            )}
                            {option.kind === 'novel' && (
                                <>
                                    <Cover url={option.card.coverUrl} title={option.card.title} seed={option.card.novelSlug} width={28} />
                                    <span className={styles.grow}>
                                        <span className={styles.title}>{option.card.title}</span>
                                        <span className={styles.muted}>{[option.card.author, option.card.teamName].filter(Boolean).join(' · ')}</span>
                                    </span>
                                </>
                            )}
                            {option.kind === 'all' && <span className={styles.all}>Усі результати за «{value.trim()}» ›</span>}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

function key(option: Option) {
    return option.kind === 'novel' ? `n${option.card.editionId}` : option.kind === 'tag' ? `t${option.tag.slug}` : 'all';
}

function useDebounced<T>(value: T, ms: number): T {
    const [settled, setSettled] = useState(value);
    useEffect(() => {
        const timer = setTimeout(() => setSettled(value), ms);
        return () => clearTimeout(timer);
    }, [value, ms]);
    return settled;
}
