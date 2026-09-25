/**
 * The markup of comments, chat and messages (architecture.md): **bold** or __bold__,
 * *italic* or _italic_, ++underline++, ~~strike~~, ||spoiler||, lines starting with «>»
 * as quotes, and @nick, $team and links. An unmatched marker stays plain text.
 */

export type Inline =
    | { kind: 'text'; text: string }
    | { kind: 'bold' | 'italic' | 'underline' | 'strike' | 'spoiler'; children: Inline[] }
    | { kind: 'mention'; nick: string }
    | { kind: 'team'; handle: string }
    | { kind: 'link'; url: string };

export type Paragraph = { quote: boolean; lines: Inline[][] };

const PAIRS: readonly [string, 'bold' | 'italic' | 'underline' | 'strike' | 'spoiler'][] = [
    ['**', 'bold'], ['__', 'bold'], ['++', 'underline'], ['~~', 'strike'], ['||', 'spoiler'], ['*', 'italic'], ['_', 'italic'],
];
const WORD = /[\p{L}\p{N}]/u;
const TOKENS = /(https?:\/\/[^\s<>"]+[^\s<>".,;:!?)»])|(?<![\p{L}\p{N}_@$-])([@$])([\p{L}\p{N}][\p{L}\p{N}_-]{2,29})/gu;

export function parse(text: string): Paragraph[] {
    const out: Paragraph[] = [];
    for (const line of text.replace(/\r/g, '').split('\n')) {
        const quote = /^>\s?/.test(line);
        const content = quote ? line.replace(/^>\s?/, '') : line;
        const last = out[out.length - 1];
        if (last && last.quote === quote && (quote || content !== '')) {
            last.lines.push(inline(content));
        } else if (content !== '' || quote) {
            out.push({ quote, lines: [inline(content)] });
        } else if (last && !last.quote) {
            // An empty line starts a new paragraph.
            out.push({ quote: false, lines: [] });
        }
    }
    return out.filter((paragraph) => paragraph.lines.length > 0);
}

export function inline(text: string): Inline[] {
    const out: Inline[] = [];
    let start = 0;
    let at = 0;
    while (at < text.length) {
        let taken = false;
        for (const [marker, kind] of PAIRS) {
            if (!text.startsWith(marker, at) || !opens(text, at, marker)) continue;
            const close = closing(text, at + marker.length, marker);
            if (close < 0) continue;
            plain(text.slice(start, at), out);
            out.push({ kind, children: inline(text.slice(at + marker.length, close)) });
            at = close + marker.length;
            start = at;
            taken = true;
            break;
        }
        if (!taken) at++;
    }
    plain(text.slice(start), out);
    return out;
}

function opens(text: string, at: number, marker: string): boolean {
    const next = text[at + marker.length];
    if (next === undefined || /\s/.test(next)) return false;
    // snake_case and nicks like user_abc keep their underscores.
    return marker !== '_' || !WORD.test(text[at - 1] ?? '');
}

function closing(text: string, from: number, marker: string): number {
    for (let at = text.indexOf(marker, from + 1); at >= 0; at = text.indexOf(marker, at + 1)) {
        if (/\s/.test(text[at - 1] ?? ' ')) continue;
        if (marker.length === 1 && text[at + 1] === marker) continue;
        if (marker === '_' && WORD.test(text[at + 1] ?? '')) continue;
        // «***» closing «**» after an inner «*»: the last two close the outer mark.
        while (marker.length === 2 && text[at + 2] === marker[0]) at++;
        return at;
    }
    return -1;
}

function plain(text: string, out: Inline[]) {
    let start = 0;
    for (const match of text.matchAll(TOKENS)) {
        if (match.index > start) out.push({ kind: 'text', text: text.slice(start, match.index) });
        if (match[1]) out.push({ kind: 'link', url: match[1] });
        else if (match[2] === '@') out.push({ kind: 'mention', nick: match[3]! });
        else out.push({ kind: 'team', handle: match[3]! });
        start = match.index + match[0].length;
    }
    if (start < text.length) out.push({ kind: 'text', text: text.slice(start) });
}
