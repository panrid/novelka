import { diffArrays } from 'diff';
import { useSyncExternalStore } from 'react';
import styles from './WordDiff.module.css';

export type DiffPart = { kind: 'same' | 'added' | 'removed'; value: string };

/**
 * What changed between two texts, whole words at a time. The library's own word diff takes
 * Cyrillic letters one by one («ста|П|рому»), so words are cut here: runs of letters and signs
 * between spaces. A change of a few words in a row reads as one: the spaces between them go
 * with the change, not between crossed-out pieces.
 */
export function wordDiff(before: string, after: string): DiffPart[] {
    const words = (value: string) => value.match(/\s+|[^\s]+/gu) ?? [];
    const raw: DiffPart[] = diffArrays(words(before), words(after)).map((part) => ({
        kind: part.added ? 'added' : part.removed ? 'removed' : 'same',
        value: part.value.join(''),
    }));
    // A lone space between two changes belongs to them.
    const parts: DiffPart[] = [];
    for (let i = 0; i < raw.length; i++) {
        const part = raw[i]!;
        const between = part.kind === 'same' && /^\s+$/.test(part.value) && i > 0 && i < raw.length - 1
            && raw[i - 1]!.kind !== 'same' && raw[i + 1]!.kind !== 'same';
        if (between) {
            parts.push({ kind: 'removed', value: part.value }, { kind: 'added', value: part.value });
        } else {
            parts.push(part);
        }
    }
    // Gather the crossed-out and the new words of one change, so it reads «old → new».
    const out: DiffPart[] = [];
    let removed = '';
    let added = '';
    const flush = () => {
        // A space both sides end with is not part of the change.
        const tail = removed && added ? (/\s+$/.exec(removed)?.[0] ?? '') : '';
        const shared = tail && added.endsWith(tail) ? tail : '';
        if (removed) out.push({ kind: 'removed', value: removed.slice(0, removed.length - shared.length) });
        if (added) out.push({ kind: 'added', value: added.slice(0, added.length - shared.length) });
        if (shared) out.push({ kind: 'same', value: shared });
        removed = '';
        added = '';
    };
    for (const part of parts) {
        if (part.kind === 'removed') removed += part.value;
        else if (part.kind === 'added') added += part.value;
        else {
            flush();
            out.push(part);
        }
    }
    flush();
    return out.reduce<DiffPart[]>((joined, part) => {
        const last = joined[joined.length - 1];
        if (last && last.kind === 'same' && part.kind === 'same') last.value += part.value;
        else joined.push({ ...part });
        return joined;
    }, []);
}

export type DiffMode = 'inline' | 'split';

const KEY = 'novelka:diff-mode';
const listeners = new Set<() => void>();

function readMode(): DiffMode {
    try {
        return localStorage.getItem(KEY) === 'split' ? 'split' : 'inline';
    } catch {
        return 'inline';
    }
}

/** How changes are shown, the same in every place and remembered in this browser. */
export function useDiffMode(): [DiffMode, (mode: DiffMode) => void] {
    const mode = useSyncExternalStore((listener) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
    }, readMode, () => 'inline' as DiffMode);
    const set = (next: DiffMode) => {
        try {
            localStorage.setItem(KEY, next);
        } catch {
            // Private mode: the choice lasts until the page is closed.
        }
        listeners.forEach((listener) => listener());
    };
    return [mode, set];
}

/** «Разом» — one paragraph with the changes marked; «Два абзаци» — as it was and as it will be. */
export function DiffModeSwitch() {
    const [mode, setMode] = useDiffMode();
    return (
        <div className={styles.switch} role="group" aria-label="Як показати зміни">
            <button type="button" aria-pressed={mode === 'inline'} onClick={() => setMode('inline')}>Разом</button>
            <button type="button" aria-pressed={mode === 'split'} onClick={() => setMode('split')}>Було і стало</button>
        </div>
    );
}

/** A paragraph's change, shown the way the reader chose. */
export function WordDiff({ before, after, className }: { before: string; after: string; className?: string | undefined }) {
    const [mode] = useDiffMode();
    if (mode === 'split') {
        return (
            <div className={`${styles.split} ${className ?? ''}`}>
                <div className={styles.side}>
                    <span className={styles.label}>Було</span>
                    <p>{before ? wordDiff(before, after).filter((part) => part.kind !== 'added').map((part, index) =>
                        part.kind === 'removed' ? <del key={index} className={styles.removed}>{part.value}</del> : <span key={index}>{part.value}</span>)
                        : <span className={styles.empty}>(не було)</span>}</p>
                </div>
                <div className={styles.side}>
                    <span className={styles.label}>Стало</span>
                    <p>{after ? wordDiff(before, after).filter((part) => part.kind !== 'removed').map((part, index) =>
                        part.kind === 'added' ? <ins key={index} className={styles.added}>{part.value}</ins> : <span key={index}>{part.value}</span>)
                        : <span className={styles.empty}>(прибрано)</span>}</p>
                </div>
            </div>
        );
    }
    return (
        <p className={`${styles.inline} ${className ?? ''}`}>
            {wordDiff(before, after).map((part, index) =>
                part.kind === 'added' ? <ins key={index} className={styles.added}>{part.value}</ins>
                    : part.kind === 'removed' ? <del key={index} className={styles.removed}>{part.value}</del>
                        : <span key={index}>{part.value}</span>)}
        </p>
    );
}
