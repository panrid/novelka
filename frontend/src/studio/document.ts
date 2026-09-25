import type { JSONContent } from '@tiptap/core';
import type { Span } from '../reading/api';
import type { StudioBlock } from './api';

/**
 * Converts between the site's blocks and the editor's document. Block ids ride along as
 * node attributes; when a paragraph is split, both halves carry the same id, so repeats
 * lose theirs and the server assigns new ones.
 */

const MARK_OF: Record<string, Span['marks'][number]> = { bold: 'bold', italic: 'italic', underline: 'underline', strike: 'strike' };

export function toDocument(blocks: StudioBlock[]): JSONContent {
    const content: JSONContent[] = blocks.map((block) => {
        switch (block.type) {
            case 'separator':
                return { type: 'horizontalRule', attrs: { blockId: block.id } };
            case 'image':
                return { type: 'image', attrs: { blockId: block.id, imageId: block.imageId, src: block.imageUrl ?? '' } };
            case 'heading':
                return { type: 'heading', attrs: { level: 2, blockId: block.id }, content: spansToText(block.content) };
            default:
                return { type: 'paragraph', attrs: { blockId: block.id, kind: block.type }, content: spansToText(block.content) };
        }
    });
    return { type: 'doc', content: content.length > 0 ? content : [{ type: 'paragraph' }] };
}

export function toBlocks(document: JSONContent): StudioBlock[] {
    const seen = new Set<string>();
    const idOf = (node: JSONContent) => {
        const id = node.attrs?.blockId as string | undefined;
        if (!id || seen.has(id)) return '';
        seen.add(id);
        return id;
    };
    const blocks: StudioBlock[] = [];
    for (const node of document.content ?? []) {
        if (node.type === 'horizontalRule') {
            blocks.push({ id: idOf(node), type: 'separator', content: [] });
        } else if (node.type === 'image' && node.attrs?.imageId) {
            blocks.push({ id: idOf(node), type: 'image', content: [], imageId: node.attrs.imageId as number, imageUrl: node.attrs.src as string });
        } else if (node.type === 'heading' || node.type === 'paragraph') {
            const content = textToSpans(node.content ?? []);
            if (content.length === 0) continue; // empty lines are just spacing in the editor
            const kind = node.type === 'heading' ? 'heading' : ((node.attrs?.kind as StudioBlock['type']) ?? 'paragraph');
            blocks.push({ id: idOf(node), type: kind === 'image' || kind === 'separator' ? 'paragraph' : kind, content });
        }
    }
    return blocks;
}

function spansToText(spans: Span[]): JSONContent[] {
    return spans.filter((span) => span.text.length > 0).map((span) => ({
        type: 'text',
        text: span.text,
        ...(span.marks.length > 0 ? { marks: span.marks.map((mark) => ({ type: mark })) } : {}),
    }));
}

function textToSpans(nodes: JSONContent[]): Span[] {
    const spans: Span[] = [];
    for (const node of nodes) {
        if (node.type !== 'text' || !node.text) continue;
        const marks = (node.marks ?? [])
            .map((mark) => MARK_OF[mark.type])
            .filter((mark): mark is Span['marks'][number] => Boolean(mark));
        const ordered = (['bold', 'italic', 'underline', 'strike'] as const).filter((mark) => marks.includes(mark));
        const last = spans.at(-1);
        if (last && last.marks.join() === ordered.join()) {
            last.text += node.text;
        } else {
            spans.push({ text: node.text, marks: [...ordered] });
        }
    }
    return spans;
}
