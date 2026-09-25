import { describe, expect, it } from 'vitest';
import type { StudioBlock } from './api';
import { toBlocks, toDocument } from './document';

const BLOCKS: StudioBlock[] = [
    { id: 'b1', type: 'paragraph', content: [{ text: 'Це не було ', marks: [] }, { text: 'ліжко', marks: ['bold', 'italic'] }] },
    { id: 'b2', type: 'separator', content: [] },
    { id: 'b3', type: 'image', content: [], imageId: 7, imageUrl: '/media/x.jpg' },
    { id: 'b4', type: 'afterword', content: [{ text: 'Від перекладача.', marks: [] }] },
];

describe('editor document', () => {
    it('round-trips blocks, marks, pictures and paragraph kinds', () => {
        expect(toBlocks(toDocument(BLOCKS))).toEqual(BLOCKS);
    });

    it('keeps the id in the first half of a split paragraph only', () => {
        const document = toDocument(BLOCKS.slice(0, 1));
        document.content!.push({ ...document.content![0]!, content: [{ type: 'text', text: 'Друга половина' }] });

        expect(toBlocks(document).map((block) => block.id)).toEqual(['b1', '']);
    });

    it('drops empty lines and unknown marks', () => {
        const blocks = toBlocks({
            type: 'doc',
            content: [
                { type: 'paragraph', attrs: { blockId: 'x' } },
                { type: 'paragraph', content: [{ type: 'text', text: 'код', marks: [{ type: 'code' }, { type: 'underline' }] }] },
            ],
        });

        expect(blocks).toEqual([{ id: '', type: 'paragraph', content: [{ text: 'код', marks: ['underline'] }] }]);
    });
});
