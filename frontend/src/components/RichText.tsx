import { Fragment, type ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import { TOKEN, type Names } from '../lib/mentions';

/**
 * Plain text of a comment or chat message: lines starting with "> " form quotes and mention tokens show the current
 * nickname. Nothing is parsed as HTML.
 */
export function RichText({ body, names, className }: { body: string; names: Names; className?: string }) {
    const { user } = useAuth();
    const blocks: { quote: boolean; lines: string[] }[] = [];
    for (const line of body.split('\n')) {
        const quoted = line.startsWith('>');
        const text = quoted ? line.replace(/^> ?/, '') : line;
        const last = blocks[blocks.length - 1];
        if (last && last.quote === quoted) last.lines.push(text);
        else blocks.push({ quote: quoted, lines: [text] });
    }
    const inline = (text: string): ReactNode[] => {
        const parts: ReactNode[] = [];
        let index = 0;
        for (const match of text.matchAll(TOKEN)) {
            parts.push(text.slice(index, match.index));
            const name = names[match[1]];
            parts.push(name ? <span key={match.index} className={'mention' + (match[1] === user?.id ? ' mention-self' : '')}>@{name}</span> : match[0]);
            index = (match.index ?? 0) + match[0].length;
        }
        parts.push(text.slice(index));
        return parts;
    };
    const lines = (items: string[]) => items.map((line, index) => <Fragment key={index}>{index > 0 && <br />}{inline(line)}</Fragment>);
    return <div className={'rich-text' + (className ? ' ' + className : '')}>{blocks.map((block, index) => block.quote
        ? <blockquote key={index}>{lines(block.lines)}</blockquote>
        : <p key={index}>{lines(block.lines)}</p>)}</div>;
}
