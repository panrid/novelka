import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { readingApi, type GlossaryTerm } from './api';
import { wordPattern } from './wordForms';

type Mark = { range: Range; term: GlossaryTerm; blockId: string };

const KEY = 'novelka:hints';

/** «Лампочка» stays on or off in this browser between chapters. */
export function hintsRemembered(): boolean {
    try {
        return localStorage.getItem(KEY) === '1';
    } catch {
        return false;
    }
}

export function rememberHints(on: boolean) {
    try {
        localStorage.setItem(KEY, on ? '1' : '0');
    } catch {
        // Storage blocked: the lamp is on for this visit only.
    }
}

/**
 * «Лампочка» (підказки зі словника): the glossary's words in the chapter are marked — through
 * the CSS Custom Highlight API, so the text itself is not touched — and a tap on one tells which
 * entry it is. Only words the reader's chapter has met and the novel told something about.
 *
 * @param layoutKey changes whenever the text is drawn anew (another chapter, pages or scrolling)
 */
export function useGlossaryHints({ enabled, slug, team, chapter, layoutKey }: {
    enabled: boolean; slug: string; team: string | undefined; chapter: number; layoutKey: string;
}) {
    const hints = useQuery({
        queryKey: ['hints', slug, team ?? '', chapter],
        queryFn: () => readingApi.glossaryHints(slug, team, chapter),
        enabled,
        staleTime: 5 * 60_000,
    });
    const marks = useRef<Mark[]>([]);

    useEffect(() => {
        const registry = typeof CSS !== 'undefined' ? CSS.highlights : undefined;
        marks.current = [];
        const data = hints.data;
        if (!enabled || !data || data.terms.length === 0) {
            registry?.delete('glossary');
            return;
        }
        const found: Mark[] = [];
        const byId = new Map(data.terms.map((term) => [term.id, term]));
        // Longest names first: «Крижаний спис» is one mark, not «спис» inside it.
        const own = [...data.terms].sort((a, b) => b.ukrainian.length - a.ukrainian.length)
            .map((term) => ({ term, pattern: wordPattern(term.ukrainian, term.looseForms) }));
        document.querySelectorAll('article [data-block-id]').forEach((block) => {
            const blockId = block.getAttribute('data-block-id')!;
            const here = data.links.filter((link) => link.blockId === blockId);
            // With the original on the site, an entry counts where the paragraph's original names it;
            // the team's own ties come first, and words it untied here stay plain.
            const named = new Set(here.filter((link) => link.linked && link.text === null).map((link) => link.entryId));
            const untied = here.filter((link) => !link.linked && link.text !== null)
                .map((link) => ({ entryId: link.entryId, text: link.text!.toLowerCase() }));
            const tied = here.filter((link) => link.linked && link.text !== null && byId.has(link.entryId))
                .map((link) => ({ term: byId.get(link.entryId)!, pattern: wordPattern(link.text!) }));
            const patterns = [...tied, ...own.filter(({ term }) => !data.byOriginal || named.has(term.id))];
            const nodes: { node: Text; start: number }[] = [];
            let text = '';
            const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
            for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                nodes.push({ node: node as Text, start: text.length });
                text += node.textContent ?? '';
            }
            const taken: [number, number][] = [];
            for (const { term, pattern } of patterns) {
                pattern.lastIndex = 0;
                for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
                    const start = match.index;
                    const end = start + match[0].length;
                    if (taken.some(([from, to]) => start < to && end > from)) continue;
                    const words = match[0].toLowerCase();
                    if (untied.some((link) => link.entryId === term.id && link.text === words)) continue;
                    const range = rangeOf(nodes, start, end);
                    if (range) {
                        taken.push([start, end]);
                        found.push({ range, term, blockId });
                    }
                }
            }
        });
        marks.current = found;
        if (registry && typeof Highlight !== 'undefined') registry.set('glossary', new Highlight(...found.map((mark) => mark.range)));
        return () => { registry?.delete('glossary'); };
    }, [enabled, hints.data, layoutKey]);

    /** The marked word under a tap, if any: the entry and the paragraph it stands in. */
    function at(x: number, y: number): { term: GlossaryTerm; blockId: string; text: string } | null {
        if (!enabled || marks.current.length === 0) return null;
        const caret = caretAt(x, y);
        if (!caret) return null;
        const mark = marks.current.find(({ range }) => range.isPointInRange(caret.node, caret.offset)
            && !(caret.node === range.endContainer && caret.offset === range.endOffset));
        return mark ? { term: mark.term, blockId: mark.blockId, text: mark.range.toString() } : null;
    }

    return { at, loading: enabled && hints.isPending };
}

function rangeOf(nodes: { node: Text; start: number }[], start: number, end: number): Range | null {
    const from = nodes.findLast((item) => item.start <= start);
    const to = nodes.findLast((item) => item.start < end);
    if (!from || !to) return null;
    const range = document.createRange();
    range.setStart(from.node, start - from.start);
    range.setEnd(to.node, end - to.start);
    return range;
}

function caretAt(x: number, y: number): { node: Node; offset: number } | null {
    if (typeof document.caretPositionFromPoint === 'function') {
        const position = document.caretPositionFromPoint(x, y);
        return position ? { node: position.offsetNode, offset: position.offset } : null;
    }
    const range = typeof document.caretRangeFromPoint === 'function' ? document.caretRangeFromPoint(x, y) : null;
    return range ? { node: range.startContainer, offset: range.startOffset } : null;
}
