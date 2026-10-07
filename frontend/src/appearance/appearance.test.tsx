import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';
import { setReader, setReaderPreset } from './store';

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
        const { router } = await renderAt('/me/settings/appearance', {
            'GET /api/me': { body: ME },
            'PUT /api/me/appearance': (body) => { saved.push(body); return { body: { ...ME, appearance: body } }; },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
        });

        await userEvent.click(within(await screen.findByRole('radiogroup', { name: 'Готовий стиль' })).getByRole('radio', { name: /Світлий/ }));
        expect(document.documentElement.dataset.theme).toBe('light');
        expect(document.documentElement.style.getPropertyValue('--bg')).toBe('#f7f5ef');
        // One's own change on top of the style: the accent.
        await userEvent.click(screen.getByRole('radio', { name: '#c2456b' }));
        expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#c2456b');
        expect(screen.getByText(/Свій стиль на основі «Світлий»/)).toBeInTheDocument();

        await act(() => router.navigate({ to: '/me/settings/reader' }));
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Кольори' })).getByRole('radio', { name: 'Чорний' }));
        expect(document.documentElement.dataset.theme).toBe('light'); // the reader's colours stay in the reader
        await waitFor(() => expect(saved.at(-1)).toMatchObject({
            site: { preset: 'light', accent: '#c2456b', custom: true }, reader: { colors: 'black', custom: true },
        }), { timeout: 2000 });
        expect(JSON.parse(localStorage.getItem('novelka:appearance')!)).toMatchObject({ site: { preset: 'light' }, reader: { colors: 'black' } });

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

    it('turns pages like a book and goes on to the next chapter after the last page', async () => {
        setReaderPreset('book');
        setReader({ pageAnim: 'none' });
        const { router } = await renderAt('/n/mah-vody/12', {
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/novels/mah-vody/chapters/13': { body: { ...CHAPTER, number: 13, title: 'Далі', previous: 12, next: null } },
        });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });
        // jsdom lays nothing out, so the whole chapter is one page.
        expect(await screen.findByText('сторінка 1 з 1')).toBeInTheDocument();
        await userEvent.keyboard('{ArrowRight}');
        await waitFor(() => expect(router.state.location.pathname).toBe('/n/mah-vody/13'));
    });
});
