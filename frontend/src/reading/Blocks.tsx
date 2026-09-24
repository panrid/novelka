import type { ReactNode } from 'react';
import type { Span, TextBlock } from './api';
import styles from './Blocks.module.css';

/** Text from the server is drawn as React elements; it is never inserted as HTML. */
export function Blocks({ blocks }: { blocks: TextBlock[] }) {
    return (
        <>
            {blocks.map((block) => {
                switch (block.type) {
                    case 'separator':
                        return <div key={block.id} className={styles.separator} role="separator" aria-hidden>◇</div>;
                    case 'image':
                        return block.imageUrl ? (
                            <figure key={block.id} className={styles.figure}>
                                <img src={block.imageUrl} alt="" loading="lazy" />
                            </figure>
                        ) : null;
                    case 'heading':
                        return <h2 key={block.id} className={styles.heading}><Spans spans={block.content} /></h2>;
                    default:
                        return (
                            <p key={block.id} className={block.type === 'paragraph' ? undefined : styles.aside}>
                                <Spans spans={block.content} />
                            </p>
                        );
                }
            })}
        </>
    );
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
