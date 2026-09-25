import { Link } from '@tanstack/react-router';
import { useState, type ReactNode } from 'react';
import { parse, type Inline } from './parseMarkup';
import styles from './community.module.css';

/** Text people wrote, drawn as React elements; never inserted as HTML. */
export function Markup({ text }: { text: string }) {
    return (
        <div className={styles.markup}>
            {parse(text).map((paragraph, index) => {
                const lines = paragraph.lines.map((line, at) => (
                    <span key={at}>{at > 0 && <br />}{line.map((node, i) => <Node key={i} node={node} />)}</span>
                ));
                return paragraph.quote ? <blockquote key={index}>{lines}</blockquote> : <p key={index}>{lines}</p>;
            })}
        </div>
    );
}

function Node({ node }: { node: Inline }): ReactNode {
    switch (node.kind) {
        case 'text':
            return node.text;
        case 'mention':
            return <Link to="/u/$nick" params={{ nick: node.nick }} className={styles.mention}>@{node.nick}</Link>;
        case 'team':
            return <Link to="/team/$handle" params={{ handle: node.handle }} className={styles.mention}>${node.handle}</Link>;
        case 'link':
            return <a href={node.url} target="_blank" rel="noopener noreferrer nofollow ugc">{node.url}</a>;
        case 'spoiler':
            return <Spoiler>{node.children.map((child, i) => <Node key={i} node={child} />)}</Spoiler>;
        default: {
            const children = node.children.map((child, i) => <Node key={i} node={child} />);
            if (node.kind === 'bold') return <strong>{children}</strong>;
            if (node.kind === 'italic') return <em>{children}</em>;
            if (node.kind === 'underline') return <u>{children}</u>;
            return <s>{children}</s>;
        }
    }
}

function Spoiler({ children }: { children: ReactNode }) {
    const [open, setOpen] = useState(false);
    return (
        <span role="button" tabIndex={0} aria-label={open ? undefined : 'Спойлер, натисніть, щоб показати'}
            className={open ? styles.spoilerOpen : styles.spoiler}
            onClick={(event) => { event.stopPropagation(); setOpen(true); }}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') setOpen(true); }}>
            {children}
        </span>
    );
}
