import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const CARD = {
    editionId: 7, novelSlug: 'mah-vody', teamHandle: 'panrid', teamName: 'panrid', title: 'Маг води',
    author: 'Тадаші Хісахо', coverUrl: null, kind: 'machine', status: 'ongoing', adult: false, chapterCount: 44,
    tags: ['фентезі'], lastPublishedAt: new Date().toISOString(),
};
const EDITION = { editionId: 7, teamHandle: 'panrid', teamName: 'panrid', kind: 'machine', status: 'ongoing', chapterCount: 44, coverUrl: null };
const NOVEL = {
    slug: 'mah-vody', title: 'Маг води', author: 'Тадаші Хісахо', origin: 'translation',
    description: [{ id: 'd1', type: 'paragraph', content: [{ text: 'Рьо перевтілився.', marks: [] }], imageUrl: null }],
    tags: ['Фентезі'], edition: EDITION, editions: [EDITION], adult: false, lastPublishedAt: null, viewer: null,
    relay: { free: false, reason: null, lastNumber: 44, continuations: [] },
};
const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const CHAPTER = {
    novelSlug: 'mah-vody', novelTitle: 'Маг води', edition: EDITION, number: 12, title: 'Спокійне життя',
    blocks: [
        { id: 'b1', type: 'paragraph', content: [{ text: 'Це не було ', marks: [] }, { text: 'розкішне', marks: ['bold'] }, { text: ' ліжко.', marks: [] }], imageUrl: null },
        { id: 'b2', type: 'separator', content: [], imageUrl: null },
    ],
    previous: 11, next: 13, savedPosition: null, continuation: null,
};

afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
});

describe('home', () => {
    it('offers continue, popular and new chapters right away', async () => {
        await renderAt('/', {
            'GET /api/home': { body: {
                continueReading: [{ card: CARD, chapterNumber: 12, position: 0.6, chapterLabel: '11' }],
                popular: [CARD],
                newChapters: [{ card: CARD, firstNumber: 41, lastNumber: 44, publishedAt: new Date(Date.now() - 5 * 3600_000).toISOString() }],
            } },
        });

        expect(await screen.findByText('Продовжити')).toBeInTheDocument();
        expect(screen.getByText('Глава 11')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Популярне/ })).toBeInTheDocument();
        expect(screen.getByText(/Глави 41–44 · 5 годин тому/)).toBeInTheDocument();
    });
});

describe('novel page', () => {
    it('starts from the first chapter, or continues where this browser stopped', async () => {
        localStorage.setItem('novelka:progress:mah-vody:panrid', JSON.stringify({ number: 12, position: 0.3 }));
        await renderAt('/n/mah-vody', {
            'GET /api/novels/mah-vody': { body: NOVEL },
            'GET /api/novels/mah-vody/chapters': { body: { items: [{ number: 12, title: 'Спокійне життя', publishedAt: '2026-09-20T10:00:00Z' }], page: 1, hasMore: false } },
        });

        const button = await screen.findByRole('link', { name: 'Продовжити · гл. 12' });
        expect(button).toHaveAttribute('href', '/n/mah-vody/12');
        expect(screen.getByText('44 глави · триває')).toBeInTheDocument();
        expect(await screen.findByText('тут зупинились')).toBeInTheDocument();
    });

    it('explains an 18+ novel instead of pretending it is missing', async () => {
        await renderAt('/n/doroslyi', {
            'GET /api/novels/doroslyi': { status: 403, body: { detail: 'Ця новела для дорослих.', reason: 'adult' } },
        });

        expect(await screen.findByText('Ця новела для дорослих.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'До налаштувань приватності' })).toBeInTheDocument();
    });
});

describe('reader', () => {
    it('shows formatted text and hides the bars while reading down', async () => {
        await renderAt('/n/mah-vody/12', { 'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER } });

        expect(await screen.findByRole('heading', { name: '12. Спокійне життя' })).toBeInTheDocument();
        expect(screen.getByText('розкішне').closest('strong')).not.toBeNull();
        expect(screen.queryByRole('navigation', { name: 'Розділи' })).not.toBeInTheDocument();

        const header = screen.getByRole('link', { name: 'До новели' }).closest('header')!;
        expect(header.className).not.toMatch(/hidden/);

        scrollTo(400);
        await waitFor(() => expect(header.className).toMatch(/hidden/));
        // A finger swipe arrives as many small scroll events, not one jump.
        for (let y = 395; y >= 365; y -= 5) scrollTo(y);
        await waitFor(() => expect(header.className).not.toMatch(/hidden/));
        for (let y = 370; y <= 400; y += 5) scrollTo(y);
        await waitFor(() => expect(header.className).toMatch(/hidden/));
    });

    it('remembers the chapter in the browser for guests', async () => {
        await renderAt('/n/mah-vody/12', { 'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER } });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });

        expect(JSON.parse(localStorage.getItem('novelka:progress:mah-vody:panrid')!)).toMatchObject({ number: 12 });
    });

    it('keeps the place when leaving, even though the next page starts at the top', async () => {
        const saves: unknown[] = [];
        const { router } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: { ...CHAPTER, savedPosition: 0.5 } },
            'PUT /api/progress/7': (body) => { saves.push(body); return { status: 204 }; },
            'GET /api/novels/mah-vody': { body: NOVEL },
        });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });
        await waitFor(() => expect(saves).toContainEqual({ chapterNumber: 12, position: 0.5 }));

        scrollTo(3000);
        Object.defineProperty(window, 'scrollY', { value: 0, configurable: true }); // the router resets scroll
        await act(() => router.navigate({ to: '/n/$slug', params: { slug: 'mah-vody' } }));

        await waitFor(() => expect((saves.at(-1) as { position: number }).position).toBeGreaterThan(0.6));
        expect(saves).not.toContainEqual({ chapterNumber: 12, position: 0 });
    });

    it('turns pages with the arrow keys', async () => {
        const { router } = await renderAt('/n/mah-vody/12', {
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'GET /api/novels/mah-vody/chapters/13': { body: { ...CHAPTER, number: 13, title: 'Ринок', previous: 12, next: null } },
        });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });

        await userEvent.keyboard('{ArrowRight}');

        await waitFor(() => expect(router.state.location.pathname).toBe('/n/mah-vody/13'));
        expect(await screen.findByText(/остання перекладена глава/)).toBeInTheDocument();
    });
});

describe('library', () => {
    it('asks guests to sign in', async () => {
        await renderAt('/library');

        expect(await screen.findByRole('link', { name: 'Увійти' })).toHaveAttribute('href', '/login?next=%2Flibrary');
    });
});

function scrollTo(y: number) {
    act(() => {
        Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
        Object.defineProperty(document.documentElement, 'scrollHeight', { value: 5000, configurable: true });
        fireEvent.scroll(window);
    });
}
