import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, appearance: {},
};
const EDITION = { editionId: 7, teamHandle: 'panrid', teamName: 'panrid', kind: 'machine', status: 'ongoing', chapterCount: 44, coverUrl: null };
const CHAPTER = {
    novelSlug: 'mah-vody', novelTitle: 'Маг води', edition: EDITION, number: 12, title: 'Спокійне життя',
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Текст глави.', marks: [] }], imageUrl: null }],
    previous: 11, next: 13, savedPosition: null, continuation: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('the look of the site and of the reader', () => {
    it('is chosen in the settings, kept with the account, and the reader has its own colours', async () => {
        const saved: unknown[] = [];
        const { router } = await renderAt('/me/settings', {
            'GET /api/me': { body: ME },
            'PUT /api/me/appearance': (body) => { saved.push(body); return { body: { ...ME, appearance: body } }; },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
        });

        await userEvent.click(await screen.findByRole('radio', { name: 'Світлий' }));
        expect(document.documentElement.dataset.theme).toBe('light');
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Кольори' })).getByRole('radio', { name: 'Чорний' }));
        expect(document.documentElement.dataset.theme).toBe('light'); // the reader's colours stay in the reader
        const look = { site: { preset: 'light' }, reader: { colors: 'black', custom: true } };
        await waitFor(() => expect(saved.at(-1)).toEqual(look), { timeout: 2000 });
        expect(JSON.parse(localStorage.getItem('novelka:appearance')!)).toEqual(look);

        await act(() => router.navigate({ to: '/n/$slug/$number', params: { slug: 'mah-vody', number: '12' } }));
        await screen.findByRole('heading', { name: '12. Спокійне життя' });
        expect(document.documentElement.dataset.theme).toBe('black');

        // «Аа» in the reader: a ready style, then one's own change; the text follows at once.
        await userEvent.click(screen.getByRole('button', { name: 'Вигляд читалки' }));
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Готовий стиль' })).getByRole('radio', { name: 'Книжка' }));
        const text = document.querySelector('article')!;
        expect(text.style.textAlign).toBe('justify');
        expect(text.dataset.paragraphs).toBe('indent');
        expect(document.documentElement.dataset.reader).toBe('sepia');
        await userEvent.click(screen.getByRole('button', { name: 'Більший текст' }));
        expect(text.style.fontSize).toBe('19px');
        expect(screen.getByText(/Свій стиль на основі «Книжка»/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Готово' }));
        await act(() => router.navigate({ to: '/' }));
        expect(document.documentElement.dataset.reader).toBeUndefined();
        expect(document.documentElement.dataset.theme).toBe('light');
    });
});
