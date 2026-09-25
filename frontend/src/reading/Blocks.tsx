import type { ReactNode } from 'react';
import type { Span, TextBlock } from './api';
import styles from './Blocks.module.css';

type Props = {
    blocks: TextBlock[];
    /** A reader's own suggested text drawn in place of the paragraph, marked as theirs. */
    overlay?: Record<string, { spans: Span[]; label: string }>;
    /** Extra content under a paragraph (the review card for the team). */
    after?: ((blockId: string) => ReactNode) | undefined;
};

/** Text from the server is drawn as React elements; it is never inserted as HTML. */
export function Blocks({ blocks, overlay, after }: Props) {
    return (
        <>
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
        </>
    );
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
                let node: ReactNode = span.text;
                if (span.marks.includes('strike')) node = <s>{node}</s>;
                if (span.marks.includes('underline')) node = <u>{node}</u>;
                if (span.marks.includes('italic')) node = <em>{node}</em>;
                if (span.marks.includes('bold')) node = <strong>{node}</strong>;
                return <span key={index}>{node}</span>;
            })}
        </>
    );
}
