import { useMemo } from 'react';

type Part = { kind: 'same' | 'removed' | 'added'; text: string };

function compare(before: string[], after: string[], limit: number): Part[] {
    if (before.length * after.length > limit) return [
        { kind: 'removed', text: before.join('') }, { kind: 'added', text: after.join('') },
    ];
    const lengths = Array.from({ length: before.length + 1 }, () => new Uint16Array(after.length + 1));
    for (let left = before.length - 1; left >= 0; left--)
        for (let right = after.length - 1; right >= 0; right--)
            lengths[left][right] = before[left] === after[right]
                ? lengths[left + 1][right + 1] + 1
                : Math.max(lengths[left + 1][right], lengths[left][right + 1]);
    const parts: Part[] = [];
    let left = 0, right = 0;
    while (left < before.length || right < after.length) {
        if (left < before.length && right < after.length && before[left] === after[right]) {
            parts.push({ kind: 'same', text: before[left++] });
            right++;
        }
        else if (right < after.length && (left === before.length || lengths[left][right + 1] > lengths[left + 1][right]))
            parts.push({ kind: 'added', text: after[right++] });
        else parts.push({ kind: 'removed', text: before[left++] });
    }
    return parts;
}

function sentenceParts(text: string) {
    return text.match(/[^.!?。！？\n]+[.!?。！？]*[ \t]*|\n+/gu) || [text];
}

function words(text: string) {
    return text.match(/\s+|[^\s]+/gu) || [text];
}

function DiffLine({ kind, parts }: { kind: 'removed' | 'added'; parts: Part[] }) {
    return <div className={'diff-line ' + kind}><span className="diff-marker" aria-label={kind === 'removed' ? 'Вилучено' : 'Додано'}>
        {kind === 'removed' ? '−' : '+'}</span><span>{parts.filter(part => part.kind === 'same' || part.kind === kind).map((part, index) =>
            part.kind === 'same' ? <span key={index}>{part.text}</span> : kind === 'removed'
                ? <del key={index}>{part.text}</del> : <ins key={index}>{part.text}</ins>)}</span></div>;
}

export function TextDiff({ before, after }: { before: string; after: string }) {
    const parts = useMemo(() => compare(sentenceParts(before), sentenceParts(after), 12000), [before, after]);
    const lines = [];
    let removed: string[] = [], added: string[] = [];
    const flush = () => {
        for (let index = 0; index < Math.max(removed.length, added.length); index++) {
            const old = removed[index], next = added[index];
            const changes = old !== undefined && next !== undefined
                ? compare(words(old), words(next), 12000) : null;
            if (old !== undefined) lines.push(<DiffLine key={'old-' + lines.length} kind="removed"
                parts={changes || [{ kind: 'removed', text: old }]} />);
            if (next !== undefined) lines.push(<DiffLine key={'new-' + lines.length} kind="added"
                parts={changes || [{ kind: 'added', text: next }]} />);
        }
        removed = []; added = [];
    };
    for (const part of parts) {
        if (part.kind === 'removed') removed.push(part.text);
        else if (part.kind === 'added') added.push(part.text);
        else { flush(); lines.push(<div className="diff-line context" key={'same-' + lines.length}><span className="diff-marker"> </span><span>{part.text}</span></div>); }
    }
    flush();
    return <div className="text-diff" aria-label="Порівняння початкового та запропонованого тексту">
        <p className="diff-legend"><span>− Вилучено</span><span>+ Додано</span></p>
        <div className="diff-lines">{lines}</div>
    </div>;
}
