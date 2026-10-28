import { Fragment, type ReactNode } from 'react';

/** Deliberately renders text as React nodes: raw HTML and executable link schemes stay inert. */
export function WorkspaceMarkdown({ text, open, image }: {
    text: string; open: (target: string) => void; image: (target: string) => string | undefined;
}) {
    const inline = (line: string): ReactNode[] => line.split(/(!?\[\[[^\]]+\]\]|\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*|\[[^\]]+\]\([^)]*\))/g).map((part, index) => {
        if (/^!?\[\[/.test(part)) {
            const [target, label] = part.replace(/^!?\[\[|\]\]$/g, '').split('|');
            const src = part.startsWith('!') ? image(target!) : undefined;
            if (src) return <img key={index} src={src} alt={label ?? target} style={{ maxWidth: '100%' }} />;
            return <button key={index} type="button" onClick={() => open(target!)}>{label ?? target}</button>;
        }
        if (part.startsWith('**')) return <strong key={index}>{part.slice(2, -2)}</strong>;
        if (part.startsWith('`')) return <code key={index}>{part.slice(1, -1)}</code>;
        if (part.startsWith('*')) return <em key={index}>{part.slice(1, -1)}</em>;
        const link = /^\[([^\]]+)\]\((https?:\/\/[^\s]+)\)$/.exec(part);
        if (link) return <a key={index} href={link[2]} target="_blank" rel="noopener noreferrer">{link[1]}</a>;
        return <Fragment key={index}>{part}</Fragment>;
    });
    return <>{text.split('```').map((section, sectionIndex) => sectionIndex % 2
        ? <pre key={sectionIndex}>{section}</pre>
        : <Fragment key={sectionIndex}>{section.split('\n').map((line, index) => {
        const heading = /^(#{1,6})\s+(.+)$/.exec(line);
        if (heading) return <div key={index} id={`workspace-heading-${heading[2]}`} role="heading" aria-level={heading[1]!.length} style={{ fontWeight: 700, marginTop: 16 }}>{inline(heading[2]!)}</div>;
        if (/^\s*[-*]\s/.test(line)) return <p key={index}>• {inline(line.replace(/^\s*[-*]\s/, ''))}</p>;
        return line ? <p key={index}>{inline(line)}</p> : <br key={index} />;
    })}</Fragment>)}</>;
}
