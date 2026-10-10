import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
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
        expect(screen.getByText('44 глави · в роботі')).toBeInTheDocument();
        expect(await screen.findByText('тут зупинились')).toBeInTheDocument();
    });

    it('does not hand a new account the place someone else left in this browser', async () => {
        localStorage.setItem('novelka:progress:mah-vody:panrid', JSON.stringify({ number: 35, position: 0.3 }));
        await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, viewer: { list: null, chapterNumber: null, position: null, teamRole: null,
                myRating: null, chapterLabel: null, relayAsked: false, subscribed: false } } },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
        });
        expect(await screen.findByRole('link', { name: 'Почати читати' })).toHaveAttribute('href', '/n/mah-vody/1');
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

    it('keeps the place after a look at an earlier chapter or one opened from the inbox', async () => {
        localStorage.setItem('novelka:progress:mah-vody:panrid', JSON.stringify({ number: 20, position: 0.3 }));
        await renderAt('/n/mah-vody/12', { 'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER } });
        await screen.findByRole('heading', { name: '12. Спокійне життя' });
        expect(JSON.parse(localStorage.getItem('novelka:progress:mah-vody:panrid')!)).toMatchObject({ number: 20 });
        vi.unstubAllGlobals();

        localStorage.clear();
        const saves: unknown[] = [];
        await renderAt('/n/mah-vody/12?look=true', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: CHAPTER },
            'PUT /api/progress/7': (body) => { saves.push(body); return { status: 204 }; },
        });
        expect((await screen.findAllByRole('heading', { name: '12. Спокійне життя' })).length).toBeGreaterThan(0);
        await new Promise((resolve) => setTimeout(resolve, 50));
        expect(saves).toEqual([]);
        expect(localStorage.getItem('novelka:progress:mah-vody:panrid')).toBeNull();
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

describe('own translation', () => {
    it('starts a team\'s own translation going on after the current one, without asking', async () => {
        const { calls, router } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: NOVEL },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }] },
            'POST /api/novels/mah-vody/own-translation': { status: 201, body: { editionId: 31, novelSlug: 'mah-vody' } },
            'GET /api/studio/editions/31': { status: 404, body: { detail: '—' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Ще' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Перекласти самому' }));
        expect(await screen.findByText(/з глави 45/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Почати переклад' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/31'));
        expect(calls.find((call) => call.path.endsWith('/own-translation'))?.body).toEqual({ team: 'mika', after: EDITION.editionId });
    });
});

describe('chapters 20 to a page', () => {
    it('opens the page with the chapter the reader stopped at and turns pages', async () => {
        const row = (number: number) => ({ number, title: `Глава ${number}`, publishedAt: '2026-09-20T10:00:00Z', label: null });
        let served = 0;
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, viewer: { list: 'reading', chapterNumber: 25, position: 0.3, teamRole: null, myRating: null, chapterLabel: null, relayAsked: false } } },
            // The page opened first is the second one (chapter 25), then the third.
            'GET /api/novels/mah-vody/chapters': () => {
                const page = ++served + 1;
                return { body: { items: Array.from({ length: 20 }, (_, i) => row((page - 1) * 20 + i + 1)).filter((r) => r.number <= 44), page, hasMore: page < 3 } };
            },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
        });
        expect(await screen.findByText('сторінка 2 з 3')).toBeInTheDocument();
        expect(await screen.findByText('тут зупинились')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Наступна →' }));
        expect(await screen.findByText('сторінка 3 з 3')).toBeInTheDocument();
        expect(calls.filter((call) => call.path.endsWith('/chapters')).map((call) => new URLSearchParams(call.query).get('page')))
            .toEqual(['2', '3']);
    });
});

describe('chapters by volumes', () => {
    it('opens the volume the reader is in, folds the others, and switches to all chapters', async () => {
        window.localStorage.clear();
        const row = (number: number) => ({ number, title: `Глава ${number}`, publishedAt: '2026-09-20T10:00:00Z', label: null });
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, viewer: { list: 'reading', chapterNumber: 25, position: 0.3, teamRole: null, myRating: null, chapterLabel: null, relayAsked: false } } },
            'GET /api/novels/mah-vody/contents': { body: [
                { firstNumber: 1, lastNumber: 20, title: 'Вичитано', chapters: 20, read: 20 },
                { firstNumber: 21, lastNumber: null, title: 'Без вичитки', chapters: 24, read: 4 },
            ] },
            'GET /api/novels/mah-vody/chapters': () => ({ body: { items: [row(21), row(25)], page: 1, hasMore: false } }),
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
        });
        const open = await screen.findByRole('button', { name: /Без вичитки/ });
        expect(open).toHaveAttribute('aria-expanded', 'true');
        expect(open).toHaveTextContent('з глави 21 · 24 глави · прочитано 4');
        expect(screen.getByRole('button', { name: /Вичитано/ })).toHaveAttribute('aria-expanded', 'false');
        expect(await screen.findByText('тут зупинились')).toBeInTheDocument();
        const query = () => calls.filter((call) => call.path.endsWith('/chapters')).map((call) => new URLSearchParams(call.query));
        expect(query().map((q) => `${q.get('from')}-${q.get('to')}`)).toEqual(['21-null']);

        await userEvent.click(screen.getByRole('button', { name: /Вичитано/ }));
        await waitFor(() => expect(query().map((q) => `${q.get('from')}-${q.get('to')}`)).toContain('1-20'));
        expect(screen.getByRole('button', { name: /Вичитано/ })).toHaveTextContent('прочитано всі');

        await userEvent.click(screen.getByRole('button', { name: 'Усі глави' }));
        await waitFor(() => expect(query().some((q) => q.get('from') === null)).toBe(true));
        expect(screen.queryByRole('button', { name: /Вичитано/ })).not.toBeInTheDocument();
    });
});

describe('new chapters bell', () => {
    it('subscribes a reader to the translation and tells them so', async () => {
        let subscribed = false;
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': () => ({ body: { ...NOVEL, viewer: { list: 'reading', chapterNumber: null, position: null, teamRole: null,
                myRating: null, chapterLabel: null, relayAsked: false, subscribed } } }),
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
            'PUT /api/library/7/subscription': () => { subscribed = true; return { status: 204 }; },
        });
        await screen.findByRole('button', { name: 'У бібліотеці: Читаю' });
        const bell = screen.getByRole('button', { name: 'Підписатися на нові глави' });
        expect(bell).toHaveAttribute('aria-pressed', 'false');
        await userEvent.click(bell);
        expect(await screen.findByRole('status')).toHaveTextContent('Сповіщатимемо про нові глави.');
        expect(calls.some((call) => call.method === 'PUT' && call.path === '/api/library/7/subscription')).toBe(true);
        expect(await screen.findByRole('button', { name: /Ви отримуєте сповіщення/ })).toHaveAttribute('aria-pressed', 'true');
    });
});

describe('reporting a translation', () => {
    it('sends the reason with the chapter from the reader', async () => {
        const { calls } = await renderAt('/n/mah-vody/12', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody/chapters/12': { body: { ...CHAPTER, number: 12, label: '11' } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
            'POST /api/reports': { status: 201, body: { received: true } },
        });
        await userEvent.click(await screen.findByRole('button', { name: /Поскаржитися на главу/ }));
        const dialog = await screen.findByRole('dialog', { name: 'Поскаржитися на главу 11' });
        await userEvent.type(within(dialog).getByLabelText('Що не так?'), 'Чужий переклад');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Надіслати скаргу' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/reports')?.body)
            .toEqual({ target: 'edition', targetId: 7, reason: 'Чужий переклад', chapter: 12 }));
        expect(await screen.findByRole('status')).toHaveTextContent('Скаргу надіслано');
    });
});

describe('the novel page’s «⋯»', () => {
    it('reports the translation from the menu and opens the discussion as a tab', async () => {
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: NOVEL },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments': { body: { items: [], total: 3, page: 1, hasMore: false } },
            'POST /api/reports': { status: 201, body: { received: true } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Ще' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Поскаржитися на переклад' }));
        const dialog = await screen.findByRole('dialog', { name: 'Поскаржитися на переклад' });
        await userEvent.type(within(dialog).getByLabelText('Що не так?'), 'Чужий переклад');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Надіслати скаргу' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/reports')?.body)
            .toMatchObject({ target: 'edition', targetId: 7, reason: 'Чужий переклад' }));
        expect(screen.getByRole('tab', { name: 'Глави · 44' })).toHaveAttribute('aria-selected', 'true');
        await userEvent.click(await screen.findByRole('tab', { name: 'Обговорення · 3' }));
        expect(screen.getByRole('tab', { name: 'Обговорення · 3' })).toHaveAttribute('aria-selected', 'true');
    });
});

describe('downloading an EPUB', () => {
    it('offers all chapters and the volumes, and tells why a download failed', async () => {
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, edition: { ...EDITION, downloadAllowed: true } } },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments': { body: { items: [], total: 0, page: 1, hasMore: false } },
            'GET /api/novels/mah-vody/volumes': { body: [{ firstNumber: 1, lastNumber: 20, title: 'Том 1', chapters: 20 }] },
            'GET /api/novels/mah-vody/epub': { status: 429, body: { detail: 'Забагато спроб. Спробуйте за хвилину.' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Ще' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Завантажити EPUB' }));
        const sheet = await screen.findByRole('dialog', { name: 'Завантажити EPUB' });
        expect(await within(sheet).findByRole('button', { name: /Том 1/ })).toBeInTheDocument();
        await userEvent.click(within(sheet).getByRole('button', { name: /Усі глави/ }));
        // The sheet stays open, so the toast is behind it for assistive tech; its text is there.
        expect(await screen.findByText('Забагато спроб. Спробуйте за хвилину.')).toBeInTheDocument();
        expect(calls.some((call) => call.path === '/api/novels/mah-vody/epub')).toBe(true);
    });

    it('is not offered when the team forbids it', async () => {
        await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, edition: { ...EDITION, downloadAllowed: false } } },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
            'GET /api/editions/7/comments': { body: { items: [], total: 0, page: 1, hasMore: false } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Ще' }));
        await screen.findByRole('button', { name: 'Поскаржитися на переклад' });
        expect(screen.queryByRole('button', { name: 'Завантажити EPUB' })).not.toBeInTheDocument();
    });
});

describe('catalog', () => {
    it('counts the novels, offers popular tags and hints at novels and tags while typing', async () => {
        const { router, calls } = await renderAt('/catalog', {
            'GET /api/catalog': { body: { items: [CARD], page: 1, hasMore: false, total: 1 } },
            'GET /api/tags/groups': { body: [
                { name: 'Жанр', tags: [{ name: 'Фентезі', slug: 'фентезі', novels: 1 }, { name: 'Жахи', slug: 'жахи', novels: 0 }] },
                { name: 'Світ і сюжет', tags: [{ name: 'Магія', slug: 'магія', novels: 1 }] },
            ] },
            'GET /api/search/hints': { body: { novels: [CARD], tags: [{ name: 'Магія', slug: 'магія', novels: 1 }] } },
        });

        await waitFor(() => expect(screen.getByRole('heading', { name: /Каталог/ })).toHaveTextContent('Каталог 1 новела'));
        // Tags are a shop's filter: groups of checkboxes, behind «Фільтри» on a phone.
        await userEvent.click(screen.getByRole('button', { name: 'Фільтри' }));
        expect(screen.getByRole('group', { name: 'Жанр' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: /Жахи/ })).toBeNull();
        await userEvent.click(screen.getByRole('checkbox', { name: /Магія/ }));
        await waitFor(() => expect(router.state.location.search).toEqual({ tags: ['магія'] }));
        expect(screen.getByRole('button', { name: 'Прибрати тег Магія' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Фільтри · 1' })).toBeInTheDocument();

        await userEvent.type(screen.getByRole('combobox', { name: 'Пошук новел' }), 'ма');
        const hints = await screen.findByRole('listbox', { name: 'Підказки' });
        expect(hints).toHaveTextContent('Магія');
        expect(hints).toHaveTextContent('Маг води');
        expect(calls.some((call) => call.path === '/api/search/hints' && call.query === '?q=%D0%BC%D0%B0')).toBe(true);

        await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}');
        await waitFor(() => expect(router.state.location.pathname).toBe('/n/mah-vody'));
    });
});

describe('the novel’s other names and the original', () => {
    it('shows the names under the title and how much of the original is translated', async () => {
        await renderAt('/n/mah-vody', {
            'GET /api/novels/mah-vody': { body: {
                ...NOVEL, edition: { ...EDITION, status: 'paused', pausedUntil: '2031-03-01' },
                facts: { titleOriginal: '水属性の魔法使い', titleEnglish: 'Water Magician', altTitles: ['Mizu Zokusei', 'Маг води'],
                    sourceStatus: 'ongoing', sourceChapterCount: 822 },
            } },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
        });
        expect(await screen.findByText(/44 глави · призупинено до 1 березня 2031/)).toBeInTheDocument();
        expect(screen.getByText('Перекладено 44 з 822 · оригінал виходить')).toBeInTheDocument();
        expect(screen.getByRole('progressbar', { name: 'Перекладено' })).toHaveAttribute('aria-valuenow', '44');
        // The other names and the original are on «Про новелу».
        await userEvent.click(screen.getByRole('tab', { name: 'Про новелу' }));
        expect(await screen.findByText('Water Magician · 水属性の魔法使い · Mizu Zokusei')).toBeInTheDocument();
        expect(screen.getByText('виходить · 822 глави')).toBeInTheDocument();
        // Google reads the title the app sets: the English name and «безкоштовно» live only there, not on the page.
        await waitFor(() => expect(document.title).toBe('Маг води (Water Magician) — читати українською безкоштовно | Новелка'));
        expect(screen.queryByText(/безкоштовно/)).not.toBeInTheDocument();
    });

    it('shows nothing extra when the team said nothing', async () => {
        await renderAt('/n/mah-vody', {
            'GET /api/novels/mah-vody': { body: NOVEL },
            'GET /api/novels/mah-vody/chapters': { body: { items: [], page: 1, hasMore: false } },
        });
        expect(await screen.findByText('44 глави · в роботі')).toBeInTheDocument();
        expect(screen.queryByText(/оригінал/)).not.toBeInTheDocument();
    });
});

describe('read marks', () => {
    const VIEWER = { list: 'reading', chapterNumber: 5, position: 0.3, teamRole: null, myRating: null, chapterLabel: null,
        relayAsked: false, subscribed: false };
    const ROWS = [1, 2, 3, 4, 5].map((number) => ({ number, title: `Глава ${number}`, publishedAt: '2026-09-20T10:00:00Z', label: null,
        read: number <= 2 }));

    it('shows skipped chapters and marks them read at once', async () => {
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, viewer: { ...VIEWER, skipped: 2, firstUnread: 3, firstUnreadLabel: null } } },
            'GET /api/novels/mah-vody/chapters': { body: { items: ROWS, page: 1, hasMore: false } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
            'PUT /api/reads/7': { status: 204 },
        });
        expect(await screen.findByText(/Пропущено 2 глави перед главою 5/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Читати з першої непрочитаної' })).toHaveAttribute('href', '/n/mah-vody/3');
        await userEvent.click(screen.getByRole('button', { name: 'Позначити прочитаними' }));
        await waitFor(() => expect(calls.find((call) => call.path === '/api/reads/7')?.body).toEqual({ from: 3, to: 4, read: true }));
    });

    it('marks one chapter, all up to it, and resets the progress', async () => {
        const { calls } = await renderAt('/n/mah-vody', {
            'GET /api/me': { body: ME },
            'GET /api/novels/mah-vody': { body: { ...NOVEL, viewer: { ...VIEWER, skipped: 0 } } },
            'GET /api/novels/mah-vody/chapters': { body: { items: ROWS, page: 1, hasMore: false } },
            'GET /api/editions/7/comments/count': { body: { count: 0 } },
            'PUT /api/reads/7': { status: 204 },
            'DELETE /api/progress/7': { status: 204 },
        });
        expect(await screen.findByRole('button', { name: '1. Глава 1: прочитано' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: '4. Глава 4: не прочитано' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Позначити прочитаною' }));
        await waitFor(() => expect(calls.filter((call) => call.path === '/api/reads/7').at(-1)?.body).toEqual({ from: 4, to: 4, read: true }));

        await userEvent.click(screen.getByRole('button', { name: '3. Глава 3: не прочитано' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Усі до цієї включно — прочитані' }));
        await waitFor(() => expect(calls.filter((call) => call.path === '/api/reads/7').at(-1)?.body).toEqual({ from: 1, to: 3, read: true }));

        await userEvent.click(screen.getByRole('button', { name: 'Ще' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Скинути прогрес читання' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Скинути прогрес?' })).getByRole('button', { name: 'Скинути' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'DELETE' && call.path === '/api/progress/7')).toBe(true));
    });

    it('shows no marks to a guest', async () => {
        await renderAt('/n/mah-vody', {
            'GET /api/novels/mah-vody': { body: NOVEL },
            'GET /api/novels/mah-vody/chapters': { body: { items: ROWS.map((row) => ({ ...row, read: null })), page: 1, hasMore: false } },
        });
        expect(await screen.findByText('1. Глава 1')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /прочитано/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Скинути прогрес читання' })).not.toBeInTheDocument();
    });
});
