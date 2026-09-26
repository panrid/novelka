import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';

const ME = {
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const EDITION = { editionId: 4, teamHandle: 'panrid', teamName: 'panrid', kind: 'machine', status: 'ongoing', chapterCount: 13, coverUrl: null, rating: null, ratings: 0 };
const CHAPTER = {
    novelSlug: 'mah-vody', novelTitle: 'Маг води', edition: EDITION, number: 12, title: 'Спокійне життя', label: null,
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Текст.', marks: [] }], imageUrl: null }],
    previous: 11, next: 13, savedPosition: null, continuation: null, teamRole: null,
};
const THREAD = {
    total: 2, page: 1, hasMore: false,
    items: [{
        id: 40, authorNick: 'lysytsia', authorAvatarUrl: null, body: 'Чудова глава, **дякую**!', createdAt: new Date().toISOString(),
        editedAt: null, removed: null, score: 3, myVote: 0, mine: false, replyTo: null,
        replies: [{ id: 41, authorNick: 'mavka', authorAvatarUrl: null, body: '@lysytsia згодна', createdAt: new Date().toISOString(),
            editedAt: null, removed: null, score: 0, myVote: 0, mine: true, replyTo: 40, replies: [] }],
    }],
};

afterEach(() => vi.unstubAllGlobals());

describe('discussion', () => {
    it('opens from the reader with the count, votes and answers', async () => {
        const { calls } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/editions/4/comments': { body: THREAD },
            'PUT /api/comments/40/vote': { body: { score: 4 } },
            'POST /api/editions/4/comments': { status: 201, body: { id: 42 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Обговорення глави, коментарів: 2' }));
        const sheet = await screen.findByRole('dialog', { name: 'Обговорення глави' });
        expect(within(sheet).getByText('дякую').tagName).toBe('STRONG');

        const first = within(sheet).getAllByRole('article')[0]!;
        await userEvent.click(within(first).getByRole('button', { name: 'Подобається' }));
        expect(calls.find((call) => call.path === '/api/comments/40/vote')?.body).toEqual({ value: 1 });

        await userEvent.click(within(first).getByRole('button', { name: 'Відповісти' }));
        await userEvent.type(within(sheet).getByLabelText('Коментар до глави'), 'І я!');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Надіслати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.method === 'POST' && call.path === '/api/editions/4/comments')?.body)
            .toEqual({ chapter: 12, body: '@lysytsia І я!', replyTo: 40 }));
    });

    it('opens right away at a comment linked from the inbox', async () => {
        await renderAt('/n/mah-vody/12#c41', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/editions/4/comments': { body: THREAD },
        });
        const sheet = await screen.findByRole('dialog', { name: 'Обговорення глави' });
        expect(await within(sheet).findByText(/згодна/)).toBeInTheDocument();
        expect(within(sheet).getByRole('button', { name: 'Змінити' })).toBeInTheDocument();
    });

    it('quotes a chosen passage folded under a spoiler', async () => {
        const { calls } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/editions/4/comments': { body: { ...THREAD, total: 0, items: [] } },
            'POST /api/editions/4/comments': { status: 201, body: { id: 43 } },
        });
        const text = (await screen.findByText('Текст.')).firstChild!;
        const range = document.createRange();
        range.setStart(text, 0);
        range.setEnd(text, 5);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
        await userEvent.click(await screen.findByRole('button', { name: '❝ Цитувати' }));
        const sheet = await screen.findByRole('dialog', { name: 'Обговорення глави' });
        expect(within(sheet).getByText('❝ Текст')).toBeInTheDocument();
        await userEvent.type(within(sheet).getByRole('textbox'), 'Гарно сказано');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Надіслати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body)
            .toMatchObject({ body: '>#b1 Текст\nГарно сказано' }));
    });
});
