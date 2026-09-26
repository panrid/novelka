import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { Span, TextBlock } from './api';
import styles from './Blocks.module.css';

type Props = {
    blocks: TextBlock[];
    /** A reader's own suggested text drawn in place of the paragraph, marked as theirs. */
    overlay?: Record<string, { spans: Span[]; label: string }>;
    /** Extra content under a paragraph (the review card for the team). */
    after?: ((blockId: string) => ReactNode) | undefined;
    /** A word to mark wherever it occurs, in any of its forms (the glossary's «У тексті»). */
    highlight?: string | undefined;
};

/** Text from the server is drawn as React elements; it is never inserted as HTML. */
export function Blocks({ blocks, overlay, after, highlight }: Props) {
    const pattern = useMemo(() => stem(highlight), [highlight]);
    return (
        <HighlightContext.Provider value={pattern}>
            {blocks.map((block) => {
                const own = overlay?.[block.id];
                const extra = after?.(block.id);
                const hasExtra = Array.isArray(extra) ? extra.length > 0 : Boolean(extra);
                if (!own && !hasExtra) {
                    return <Block key={block.id} block={block} />;
                }
                return (
                    <div key={block.id}>
                        {own ? (
                            <p data-block-id={block.id} className={styles.own}>
                                <Spans spans={own.spans} />
                                <span className={styles.ownLabel}>{own.label}</span>
                            </p>
                        ) : <Block block={block} />}
                        {hasExtra && extra}
                    </div>
                );
            })}
        </HighlightContext.Provider>
    );
}

const HighlightContext = createContext<RegExp | null>(null);

/**
 * Ukrainian changes word endings («Рьо-сан», «відділення» → «відділенням»), so a longer word
 * is looked for without its last two letters; each word of a phrase the same way.
 */
function stem(word: string | undefined): RegExp | null {
    const words = word?.trim().split(/\s+/).filter(Boolean) ?? [];
    if (words.length === 0) return null;
    const parts = words.map((part) => (part.length > 4 ? part.slice(0, -2) : part).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    // …and the mark runs to the end of the last word, not to the cut.
    return new RegExp(parts.join('\\S*\\s+') + "[\\p{L}\\p{M}'’-]*", 'giu');
}

function Marked({ text }: { text: string }) {
    const pattern = useContext(HighlightContext);
    if (!pattern) return <>{text}</>;
    const out: ReactNode[] = [];
    let last = 0;
    for (const match of text.matchAll(pattern)) {
        const at = match.index ?? 0;
        if (at > last) out.push(text.slice(last, at));
        out.push(<mark key={at} data-found>{match[0]}</mark>);
        last = at + match[0].length;
    }
    if (out.length === 0) return <>{text}</>;
    out.push(text.slice(last));
    return <>{out}</>;
}

function Block({ block }: { block: TextBlock }) {
    switch (block.type) {
        case 'separator':
            return <div data-block-id={block.id} className={styles.separator} role="separator" aria-hidden>◇</div>;
        case 'image':
            return block.imageUrl ? (
                <figure data-block-id={block.id} className={styles.figure}>
                    <img src={block.imageUrl} alt="" loading="lazy" />
                </figure>
            ) : null;
        case 'heading':
            return <h2 data-block-id={block.id} className={styles.heading}><Spans spans={block.content} /></h2>;
        default:
            return (
                <p data-block-id={block.id} className={block.type === 'paragraph' ? undefined : styles.aside}>
                    <Spans spans={block.content} />
                </p>
            );
    }
}

export function Spans({ spans }: { spans: Span[] }) {
    return (
        <>
            {spans.map((span, index) => {
                let node: ReactNode = <Marked text={span.text} />;
                if (span.marks.includes('strike')) node = <s>{node}</s>;
                if (span.marks.includes('underline')) node = <u>{node}</u>;
                if (span.marks.includes('italic')) node = <em>{node}</em>;
                if (span.marks.includes('bold')) node = <strong>{node}</strong>;
                return <span key={index}>{node}</span>;
            })}
        </>
    );
}
