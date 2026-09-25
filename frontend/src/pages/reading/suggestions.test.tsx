import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const EDITION = { editionId: 7, teamHandle: 'panrid', teamName: 'panrid', kind: 'machine', status: 'ongoing', chapterCount: 44, coverUrl: null };
const CHAPTER = {
    novelSlug: 'mah-vody', novelTitle: 'Маг води', edition: EDITION, number: 12, title: 'Спокійне життя',
    blocks: [
        { id: 'b1', type: 'paragraph', content: [{ text: 'Але це не була розкішна ліжко.', marks: [] }], imageUrl: null },
        { id: 'b2', type: 'paragraph', content: [{ text: 'Ліжко скрипнуло.', marks: [] }], imageUrl: null },
    ],
    previous: 11, next: 13, savedPosition: null, continuation: null, teamRole: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('suggestions in the reader', () => {
    it('adds a replacement to the batch and sends the batch', async () => {
        let drafts = 0;
        const { calls } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/suggestions/mine': () => ({ body: { items: [], draftsInEdition: drafts } }),
            'GET /api/suggestions/count': { body: { occurrences: 2 } },
            'PUT /api/suggestions/draft/replace': () => { drafts = 1; return { body: { id: 5 } }; },
            'POST /api/suggestions/submit': () => { drafts = 0; return { body: { count: 1 } }; },
        });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });

        // A selection in one paragraph offers «Замінити в главі».
        const paragraph = screen.getByText('Ліжко скрипнуло.');
        const range = document.createRange();
        range.selectNodeContents(paragraph.firstChild!);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        await userEvent.click(await screen.findByRole('button', { name: '⇄ Замінити в главі' }));

        const dialog = await screen.findByRole('dialog');
        const replacement = within(dialog).getByLabelText('На що');
        await userEvent.clear(replacement);
        await userEvent.type(replacement, 'Ліжко рипнуло.');
        expect(await within(dialog).findByText('У цій главі: 2')).toBeInTheDocument();
        await userEvent.click(within(dialog).getByRole('button', { name: 'Додати до пакета' }));

        expect(calls.find((call) => call.path === '/api/suggestions/draft/replace')?.body)
            .toMatchObject({ editionId: 7, number: 12, find: 'Ліжко скрипнуло.', replacement: 'Ліжко рипнуло.' });
        expect(await screen.findByText('Ненадісланих правок: 1')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Надіслати' }));
        expect(await screen.findByText('Правки надіслано команді. Дякуємо!')).toBeInTheDocument();
    });

    it('shows the reader their own suggestion in place of the paragraph', async () => {
        await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/suggestions/mine': { body: { items: [{ id: 5, kind: 'block', state: 'pending', blockId: 'b1',
                proposed: [{ text: 'Але це не було розкішне ліжко.', marks: [] }], find: null, replacement: null, note: null }], draftsInEdition: 0 } },
        });

        expect(await screen.findByText('Але це не було розкішне ліжко.')).toBeInTheDocument();
        expect(screen.getByText('ваша правка · на перевірці')).toBeInTheDocument();
        expect(screen.queryByText('Але це не була розкішна ліжко.')).not.toBeInTheDocument();
    });
});

describe('review by the team', () => {
    it('accepts a suggestion inside the text and applies the decisions at once', async () => {
        const { calls } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: { ...CHAPTER, teamRole: 'editor' } },
            'GET /api/suggestions/mine': { body: { items: [], draftsInEdition: 0 } },
            'GET /api/studio/editions/7/chapters/12/suggestions': { body: [{
                id: 9, kind: 'block', authorNick: 'oleh', note: 'рід', blockId: 'b1',
                current: [{ text: 'Але це не була розкішна ліжко.', marks: [] }],
                proposed: [{ text: 'Але це не було розкішне ліжко.', marks: [] }],
                proposedTitle: null, proposedBlocks: null, find: null, replacement: null, occurrences: 0, stale: false,
                createdAt: '2026-09-25T10:00:00Z',
            }] },
            'POST /api/studio/editions/7/chapters/12/suggestions/review': { body: { revisionId: 50, accepted: 1, rejected: 0, stale: 0 } },
        });

        await userEvent.click(await screen.findByRole('button', { name: 'Перевірити' }));
        expect(screen.getByText('oleh · «рід»')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: '✓ Прийняти' }));
        await userEvent.click(screen.getByRole('button', { name: 'Застосувати (1)' }));

        await waitFor(() => expect(calls.find((call) => call.path.endsWith('/suggestions/review'))?.body)
            .toEqual({ decisions: [{ id: 9, accept: true }] }));
    });
});

describe('my suggestions', () => {
    it('lists what was sent and what the team decided', async () => {
        await renderAt('/me/suggestions', {
            'GET /api/me': { body: ME },
            'GET /api/me/suggestions': { body: [
                { id: 1, novelSlug: 'mah-vody', novelTitle: 'Маг води', teamHandle: 'panrid', chapter: 12, kind: 'replace',
                    preview: '«скрипнуло» → «рипнуло»', state: 'rejected', reviewNote: 'так в оригіналі', updatedAt: '2026-09-25T10:00:00Z' },
            ] },
        });

        expect(await screen.findByText('«скрипнуло» → «рипнуло»')).toBeInTheDocument();
        expect(screen.getByText(/відхилено .* команда: «так в оригіналі»/)).toBeInTheDocument();
    });
});
