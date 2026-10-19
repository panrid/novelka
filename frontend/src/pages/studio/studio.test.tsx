import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
/** Autotranslate is the site owner's while етап 19 is being made, and of those the owner let in. */
const OWNER = { ...ME, role: 'owner', ai: true };
const LET_IN = { ...ME, autotranslate: true, ai: true };
/** Let in, but with no шаги while the site owner keeps AI hidden from such people (етап 19, п. 7). */
const HIDDEN = { ...ME, autotranslate: true, ai: false };
const EDITOR = {
    number: 3, title: 'Нічний ринок', revisionId: 41, published: true, draft: null, role: 'translator', mayAddPictures: true,
    previous: 2, next: null,
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Ліхтарі спалахнули.', marks: [] }], imageId: null, imageUrl: null }],
};

const COUNTS = { sourceChapters: null, pendingSuggestions: 0, drafts: 0, newWords: 0, originalUrl: null, originals: 0 };
const NO_CHAPTERS = { items: [], total: 0, page: 1, hasMore: false };

afterEach(() => vi.unstubAllGlobals());

describe('chapter editor', () => {
    it('keeps a draft on its own and publishes against the version it opened', async () => {
        const calls: { path: string; body: unknown }[] = [];
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'PUT /api/studio/editions/7/chapters/3/draft': (body) => { calls.push({ path: 'draft', body }); return { status: 204 }; },
            'POST /api/studio/editions/7/chapters/3/publish': (body) => { calls.push({ path: 'publish', body }); return { body: { revisionId: 42 } }; },
        });

        const title = await screen.findByLabelText('Назва глави');
        await userEvent.clear(title);
        await userEvent.type(title, 'Ринок уночі');

        await waitFor(() => expect(screen.getByText('чернетку збережено')).toBeInTheDocument(), { timeout: 3000 });
        expect(calls.find((call) => call.path === 'draft')?.body).toMatchObject({ title: 'Ринок уночі', baseRevisionId: 41 });

        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        expect(await screen.findByText('Опубліковано. Читачі вже бачать нову версію.')).toBeInTheDocument();
        expect(calls.find((call) => call.path === 'publish')?.body).toMatchObject({
            title: 'Ринок уночі', baseRevisionId: 41,
            blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Ліхтарі спалахнули.', marks: [] }] }],
        });
    });

    it('shows the team the original of the paragraph next to its translation', async () => {
        localStorage.removeItem('novelka:editor-original');
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: true } },
            'GET /api/studio/editions/7/chapters/3/original': { body: { title: '第3話', blocks: [{ id: 'b1', type: 'paragraph', text: '灯りがともった。' }] } },
        });

        await userEvent.click(await screen.findByRole('button', { name: 'Оригінал' }));
        expect(await screen.findByText(/Поставте курсор в абзац/)).toBeInTheDocument();
        // jsdom does not place a caret on click: put it into the paragraph as the browser would.
        const paragraph = screen.getByText('Ліхтарі спалахнули.');
        screen.getByRole('textbox', { name: 'Текст глави' }).focus();
        const caret = document.createRange();
        caret.setStart(paragraph.firstChild!, 3);
        caret.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(caret);
        document.dispatchEvent(new Event('selectionchange'));
        expect(await screen.findByText('灯りがともった。')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Оригінал' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('puts the original beside each paragraph on one grid on a wide screen, and a switch hides it', async () => {
        localStorage.removeItem('novelka:editor-original');
        localStorage.removeItem('novelka:workspace-panels');
        vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('1024'), addEventListener: () => {}, removeEventListener: () => {} }));
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: true } },
            'GET /api/studio/editions/7/chapters/3/original': { body: { title: '第3話', blocks: [
                { id: 'b1', type: 'paragraph', text: '灯りがともった。' },
                { id: 'b2', type: 'paragraph', text: '市場が目を覚ました。' },
            ] } },
            'GET /api/studio/editions/7/chapters': { body: { items: [
                { number: 2, title: 'Ліхтарник', published: true, hasMyDraft: false, updatedAt: '2026-10-01T10:00:00Z', label: null, pending: 0 },
                { number: 3, title: 'Нічний ринок', published: true, hasMyDraft: false, updatedAt: '2026-10-02T10:00:00Z', label: null, pending: 2 },
            ], total: 2, page: 1, hasMore: false } },
        });
        // On a computer the original is open from the start, paragraph by paragraph next to the translation.
        const text = await screen.findByRole('textbox', { name: 'Текст глави' });
        expect(await within(text).findByText('灯りがともった。')).toBeInTheDocument();
        expect(screen.getByText('第3話')).toBeInTheDocument();
        expect(within(text).getByRole('button', { name: 'Абзац 1' })).toBeInTheDocument();
        // A paragraph of the original nobody translated yet has a row of its own.
        expect(within(text).getByText('市場が目を覚ました。')).toBeInTheDocument();
        expect(within(text).getByRole('button', { name: /Ще не перекладено/ })).toBeInTheDocument();
        expect(screen.queryByRole('complementary', { name: 'Оригінал абзацу' })).not.toBeInTheDocument();

        const chapters = screen.getByRole('complementary', { name: 'Глави' });
        expect(within(chapters).getByRole('link', { name: /Нічний ринок/ })).toHaveAttribute('aria-current', 'page');
        expect(within(chapters).getByRole('link', { name: /Ліхтарник/ })).toHaveAttribute('href', '/studio/7/chapters/2');
        const counts = screen.getByRole('complementary', { name: 'Словник глави' });
        expect(within(counts).getByText('Неперекладені абзаци').nextSibling).toHaveTextContent('1');

        const switches = screen.getByRole('group', { name: 'Що показувати' });
        await userEvent.click(within(switches).getByRole('button', { name: 'Оригінал' }));
        expect(within(switches).getByRole('button', { name: 'Оригінал' })).toHaveAttribute('aria-pressed', 'false');
        expect(within(text).queryByText('市場が目を覚ました。')).not.toBeInTheDocument();
        await userEvent.click(within(switches).getByRole('button', { name: 'Глави' }));
        expect(screen.queryByRole('complementary', { name: 'Глави' })).not.toBeInTheDocument();
        expect(localStorage.getItem('novelka:editor-original')).toBe('0');
    });

    it('shows the translation «як у книжці» with the state of each paragraph and marks one checked', async () => {
        let checked: { blockId: string; text: string }[] = [];
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, blocks: [
                ...EDITOR.blocks,
                { id: 'b2', type: 'paragraph', content: [{ text: 'Ринок прокинувся.', marks: [] }], imageId: null, imageUrl: null },
            ] } },
            'GET /api/studio/editions/7/chapters/3/paragraphs': () => ({ body: {
                checked, machine: [{ blockId: 'b1', text: 'Ліхтарі спалахнули.' }], offers: ['b2'],
            } }),
            'PUT /api/studio/editions/7/chapters/3/paragraphs/b1/check': (body) => {
                checked = [{ blockId: 'b1', text: (body as { text: string }).text }];
                return { status: 204 };
            },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Як у книжці' }));
        expect(await screen.findByRole('button', { name: 'Абзац 1: машинний' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Абзац 2: є пропозиція' })).toBeInTheDocument();
        expect(screen.getByText('Перевірено 0 з 2 абзаців')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Абзац 1: машинний' }));
        await userEvent.click(screen.getByRole('button', { name: 'Позначити перевіреним' }));
        expect(await screen.findByRole('button', { name: 'Абзац 1: перевірено' })).toBeInTheDocument();
        expect(screen.getByText('Перевірено 1 з 2 абзаців')).toBeInTheDocument();
        expect(calls.find((call) => call.method === 'PUT' && call.path.endsWith('/paragraphs/b1/check'))?.body).toEqual({ text: 'Ліхтарі спалахнули.' });

        await userEvent.click(screen.getByRole('button', { name: 'Назад до двох колонок' }));
        expect(await screen.findByRole('textbox', { name: 'Текст глави' })).toHaveTextContent('Ринок прокинувся.');
    });

    it('has no original to show for a chapter written by hand', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: false } },
        });
        await screen.findByLabelText('Назва глави');
        expect(screen.queryByRole('button', { name: 'Оригінал' })).not.toBeInTheDocument();
    });

    it('explains when a colleague published first and keeps the text', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/publish': {
                status: 409, body: { detail: 'Поки ви редагували, главу оновив хтось інший.', reason: 'chapter-changed' },
            },
        });

        expect(await screen.findByRole('button', { name: 'Опублікувати' })).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Назва глави'), '!');
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('главу оновив хтось інший');
        expect(screen.getByRole('button', { name: 'Відкрити главу знову' })).toBeInTheDocument();
        expect(screen.getByLabelText('Назва глави')).toHaveValue('Нічний ринок!');
    });
});

describe('new publication', () => {
    it('creates an own translation and opens it in the Studio', async () => {
        const { router, calls } = await renderAt('/studio/new', {
            'GET /api/me': { body: ME },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }] },
            'POST /api/studio/editions': { status: 201, body: { editionId: 12, novelSlug: 'sto-nochei' } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'sto-nochei', title: 'Сто ночей', author: '', description: [], tags: [], kind: 'human', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: true, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS } },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
            'GET /api/tags/groups': { body: [
                { name: 'Жанр', tags: [{ name: 'Фентезі', slug: 'фентезі', novels: 3 }, { name: 'Жахи', slug: 'жахи', novels: 0 }] },
                { name: 'Настрій', tags: [{ name: 'Затишне', slug: 'затишне', novels: 1 }] },
            ] },
        });

        await userEvent.click(await screen.findByRole('radio', { name: /Перекладаю сам/ }));
        await userEvent.type(screen.getByLabelText('Назва українською'), 'Сто ночей');
        await userEvent.type(screen.getByLabelText('Назва в оригіналі · за бажанням'), 'One Hundred Nights');
        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Мова оригіналу' }), 'ko');
        // Tags are picked from the list, never typed; they wait behind «Опис і теги».
        await userEvent.click(screen.getByRole('button', { name: /Опис і теги/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Фентезі' }));
        await userEvent.click(screen.getByRole('button', { name: 'Затишне' }));
        expect(screen.getByRole('button', { name: 'Затишне' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'Далі: перша глава →' })).toBeDisabled();
        await userEvent.click(screen.getByRole('switch', { name: /^Я маю право перекладати цей текст і викладати переклад/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Далі: перша глава →' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12'));
        expect(calls.find((call) => call.path === '/api/studio/editions')?.body).toMatchObject({
            kind: 'human', title: 'Сто ночей', tags: ['Фентезі', 'Затишне'], team: 'mika', rightsConfirmed: true,
            titleOriginal: 'One Hundred Nights', language: 'ko',
        });
        expect(await screen.findByRole('button', { name: 'Нова глава' })).toBeInTheDocument();
    });
});

describe('machine translation', () => {
    it('leads the site owner to the autotranslation, and hides it from a translator with шаги (етап 19)', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: OWNER },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS } },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });

        expect(await screen.findByRole('link', { name: 'Автопереклад' })).toHaveAttribute('href', '/studio/12/translate');
        expect(screen.getByRole('link', { name: 'Словник' })).toBeInTheDocument();
    });

    it('leads a translator the site owner let in to the autotranslation', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: LET_IN },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS } },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        expect(await screen.findByRole('link', { name: 'Автопереклад' })).toHaveAttribute('href', '/studio/12/translate');
    });

    it('is not offered to a translator with шаги while етап 19 is being made', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: ME },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS } },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Автопереклад' })).not.toBeInTheDocument();
    });
});

describe('AI hidden from people without шаги (етап 19, п. 7)', () => {
    const MACHINE = { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS };

    it('leaves a person let in only the original pasted by hand', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: HIDDEN },
            'GET /api/me/shahs': { body: { available: 0, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: MACHINE },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        expect(await screen.findByRole('link', { name: 'Оригінал' })).toHaveAttribute('href', '/studio/12/originals');
        expect(screen.queryByRole('link', { name: 'Автопереклад' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Словник' })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Нова глава' }));
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Відкрити робоче місце' })).toBeInTheDocument();
    });

    it('offers no autotranslation in a new publication', async () => {
        await renderAt('/studio/new', {
            'GET /api/me': { body: HIDDEN },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }] },
        });
        expect(await screen.findByRole('radio', { name: /Перекладаю сам/ })).toBeInTheDocument();
        // The original is pasted all the same, only without AI.
        expect(screen.getByRole('radio', { name: /Вставлю оригінал/ })).toBeChecked();
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.queryByText(/ШІ/)).not.toBeInTheDocument();
    });

    it('shows it all to the same person once they have шаги', async () => {
        await renderAt('/studio/new', {
            'GET /api/me': { body: LET_IN },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }] },
        });
        expect(await screen.findByRole('radio', { name: /Автопереклад із сайту/ })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: /Вставлю оригінал — перекладе ШІ/ })).toBeInTheDocument();
    });
});

describe('without шаги', () => {
    it('hides autotranslation and its glossary everywhere', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: ME },
            'GET /api/me/shahs': { body: { available: 0, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS } },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Відкрити робоче місце' })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Автопереклад' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Словник' })).not.toBeInTheDocument();
    });
});

describe('studio side menu', () => {
    it('lists the translations, and the open one has its sections as tabs', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: OWNER },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio': { body: [
                { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', coverUrl: null, kind: 'machine', status: 'ongoing', chapterCount: 2,
                    teamHandle: 'mika', teamName: 'mika', role: 'owner', drafts: 0, pendingSuggestions: 3, newWords: 0, jobState: null, jobChapter: null },
                { editionId: 14, novelSlug: 'sto-nochei', title: 'Сто ночей', coverUrl: null, kind: 'human', status: 'ongoing', chapterCount: 1,
                    teamHandle: 'mika', teamName: 'mika', role: 'owner', drafts: 0, pendingSuggestions: 0, newWords: 0, jobState: 'running', jobChapter: 2 },
            ] },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS, pendingSuggestions: 3, newWords: 4 } },
            'GET /api/studio/editions/12/autotranslate': { body: { jobs: [] } },
        });
        const rail = await screen.findByRole('navigation', { name: 'Студія' });
        expect(await within(rail).findByRole('link', { name: /Сто ночей/ })).toHaveAttribute('href', '/studio/14');
        expect(within(rail).getByRole('link', { name: /Лиходійка/ })).toHaveTextContent('3');
        const tabs = screen.getByRole('navigation', { name: 'Розділи перекладу' });
        expect(within(tabs).getByRole('link', { name: 'Словник4' })).toHaveAttribute('href', '/studio/12/glossary');
        expect(within(tabs).getByRole('link', { name: 'Глави' })).toHaveAttribute('href', '/studio/12/chapters');
        expect(within(tabs).getByRole('link', { name: 'Огляд' })).toHaveAttribute('aria-current', 'page');
        expect(screen.getByRole('link', { name: /Правки на перевірку/ })).toHaveAttribute('href', '/studio/12/suggestions');
    });
});

describe('new chapter', () => {
    const EDITION = (kind: string) => ({ editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind, status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS });
    const base = (kind: string) => ({
        'GET /api/me': { body: OWNER },
        'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
        'GET /api/studio/editions/12': { body: EDITION(kind) },
        'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
        'GET /api/studio/editions/12/contributions': { body: [] },
    });

    it('offers the autotranslation of the next chapters first, with their price', async () => {
        const { calls, router } = await renderAt('/studio/12', {
            ...base('machine'),
            'GET /api/studio/editions/12/autotranslate': { body: {
                configured: true, showShah: true, sourceChapters: 10, nextNumber: 3, publishedChapters: 2, lastAnalyzed: 2, nextToAnalyze: 3,
                averageChars: 5000, balance: null, usdPerShah: 0.07, jobs: [], personal: true, reserved: 0, presets: [],
                settings: Object.fromEntries(['analyze', 'translate', 'proofread'].map((stage) => [stage, { model: 'a/b', inputPerMillion: 1, outputPerMillion: 1, enabled: true }])),
            } },
            'POST /api/studio/editions/12/autotranslate/quote': { body: {
                kind: 'translate', from: 3, to: 5, chapters: 3, skipped: 0, shah: 3, usd: 0.2, expectedUsd: 0.1, estimated: true, unanalyzed: 3,
            } },
            'POST /api/studio/editions/12/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.getByRole('radio', { name: /Автопереклад/ })).toHaveAttribute('aria-checked', 'true');
        await userEvent.type(await screen.findByLabelText('Перекласти з глави 3 до глави…'), '5');
        await waitFor(() => expect(calls.some((call) => call.path.endsWith('/quote') && (call.body as { to: number }).to === 5)).toBe(true));
        await userEvent.click(await screen.findByRole('button', { name: 'Перекласти глави 3–5' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12/translate'));
        expect(calls.find((call) => call.path.endsWith('/autotranslate/jobs'))?.body).toEqual({ kind: 'translate', to: 5 });
    });

    it('opens the editor when the chapter is written by hand', async () => {
        const { router } = await renderAt('/studio/12', {
            ...base('human'),
            'POST /api/studio/editions/12/chapters': { status: 201, body: { number: 3 } },
            'GET /api/studio/editions/12/chapters/3': { body: { ...EDITOR, number: 3 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: /з файлу/ })).toHaveAttribute('href', '/studio/12/import');
        await userEvent.click(screen.getByRole('button', { name: 'Відкрити робоче місце' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12/chapters/3'));
    });
});

describe('team page', () => {
    const TEAM = {
        handle: 'kitsune', name: 'Кіцуне', personal: false,
        members: [{ nick: 'mika', avatarUrl: null, role: 'owner' }, { nick: 'oleh', avatarUrl: null, role: 'editor' }],
        editions: [], viewerRole: 'owner',
    };

    it('lets the owner manage members, and shows only names to others', async () => {
        await renderAt('/team/kitsune', { 'GET /api/me': { body: ME }, 'GET /api/teams/kitsune': { body: TEAM } });
        expect(await screen.findByLabelText('Роль oleh')).toHaveValue('editor');
        expect(screen.getByLabelText('Додати людину за ніком')).toBeInTheDocument();
    });

    it('hides the controls from visitors', async () => {
        await renderAt('/team/kitsune', { 'GET /api/teams/kitsune': { body: { ...TEAM, viewerRole: null } } });
        expect(await screen.findByText('редактор')).toBeInTheDocument();
        expect(screen.queryByLabelText('Додати людину за ніком')).not.toBeInTheDocument();
    });
});

describe('the novel’s data', () => {
    const FACTS = { titleOriginal: null, titleEnglish: null, altTitles: [], sourceStatus: null, sourceChapterCount: null };
    const ABOUT = { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'human',
        status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika',
        role: 'owner', ...COUNTS, pausedUntil: null, source: 'manual', facts: FACTS };

    it('saves the other names, the original’s state and until when the translation rests', async () => {
        const { calls } = await renderAt('/studio/12/about', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12': { body: ABOUT },
            'GET /api/tags/groups': { body: [] },
            'PATCH /api/studio/editions/12': { body: ABOUT },
        });
        await userEvent.type(await screen.findByLabelText('Англійська назва'), 'The Villainess');
        await userEvent.type(screen.getByLabelText('Назва оригіналу'), '悪役令嬢');
        await userEvent.type(screen.getByLabelText('Альтернативні назви'), 'Akuyaku{Enter}{Enter}Лиходійка-2');
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Стан оригіналу' })).getByRole('radio', { name: 'Виходить' }));
        await userEvent.type(screen.getByLabelText('Глав в оригіналі'), '822');
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Стан перекладу' })).getByRole('radio', { name: 'Призупинено' }));
        await userEvent.type(screen.getByLabelText('Призупинено до'), '2026-12-01');
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toMatchObject({
            status: 'paused', pausedUntil: '2026-12-01',
            facts: { titleEnglish: 'The Villainess', titleOriginal: '悪役令嬢', altTitles: ['Akuyaku', 'Лиходійка-2'],
                sourceStatus: 'ongoing', sourceChapterCount: 822 },
        }));
    });
});

describe('the original of a translation (етап 19)', () => {
    const EDITION = { editionId: 12, novelSlug: 'mayak', title: 'Доглядач маяка', author: '', description: [], tags: [], kind: 'human', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', ...COUNTS };
    const EMPTY = { items: [], total: 0, page: 1, nextNumber: 1, languages: {} };

    it('takes one chapter in its own fields: the title and the text', async () => {
        let saved = false;
        const { calls } = await renderAt('/studio/12/originals', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/12': { body: EDITION },
            'GET /api/studio/editions/12/originals': () => ({ body: saved
                ? { items: [{ number: 1, title: 'Chapter 1: Good Morning Brother', chars: 30, language: 'en', translated: false, updatedAt: '2026-10-16T10:00:00Z' }], total: 1, page: 1, nextNumber: 2, languages: { en: 1 } }
                : EMPTY }),
            'GET /api/studio/editions/12/originals/1': { body: { number: 1, title: 'Chapter 1: Good Morning Brother', language: 'en', blocks: [
                { id: 'o1', type: 'paragraph', content: [{ text: 'Zorian woke up.', marks: [] }] },
                { id: 'o2', type: 'paragraph', content: [{ text: '“Morning!” Kirielle shouted.', marks: [] }] },
            ] } },
            'POST /api/studio/editions/12/originals/separate': () => { saved = true; return { status: 201, body: { saved: 1, from: 1, to: 1 } }; },
        });
        expect(await screen.findByRole('link', { name: 'Оригінал' })).toHaveAttribute('aria-current', 'page');
        expect(await screen.findByRole('heading', { name: 'Нова глава 1' })).toBeInTheDocument();
        // Several chapters in one field and files are gone (новий дизайн, рішення 8).
        expect(screen.queryByRole('radio', { name: 'Кілька глав' })).not.toBeInTheDocument();
        await userEvent.type(screen.getByRole('textbox', { name: 'Заголовок' }), 'Chapter 1: Good Morning Brother');
        await userEvent.click(screen.getByRole('textbox', { name: 'Текст глави' }));
        await userEvent.paste('Zorian woke up.\n“Morning!” Kirielle shouted.');
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        expect(await screen.findByText(/Збережено главу 1\./)).toBeInTheDocument();
        expect(screen.getAllByRole('link', { name: 'Перекласти →' })[0]).toHaveAttribute('href', '/studio/12/translate');
        expect(calls.find((call) => call.method === 'POST' && call.path === '/api/studio/editions/12/originals/separate')!.body).toEqual({
            chapters: [{ title: 'Chapter 1: Good Morning Brother', text: 'Zorian woke up.\n“Morning!” Kirielle shouted.' }], first: 1, language: 'en',
        });
        // The saved chapter stays open in the form and is marked in the list beside it.
        expect(await screen.findByRole('heading', { name: 'Оригінал глави 1' })).toBeInTheDocument();
        const list = screen.getByRole('complementary', { name: 'Глави оригіналу' });
        expect(await within(list).findByRole('button', { name: /Good Morning Brother/ })).toHaveAttribute('aria-current', 'true');
        expect(within(list).getByText('є оригінал · англ.')).toBeInTheDocument();
    });

    it('opens another chapter from the list and goes on to the next one after saving', async () => {
        const rows = [
            { number: 1, title: 'Chapter 1', chars: 9, language: 'en', translated: true, updatedAt: '2026-10-16T10:00:00Z' },
            { number: 2, title: 'Chapter 2', chars: 9, language: 'en', translated: false, updatedAt: '2026-10-16T10:00:00Z' },
        ];
        const { calls } = await renderAt('/studio/12/originals', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/12': { body: { ...EDITION, chapterCount: 5 } },
            'GET /api/studio/editions/12/originals': { body: { items: rows, total: 2, page: 1, nextNumber: 3, languages: { en: 2 } } },
            'GET /api/studio/editions/12/originals/1': { body: { number: 1, title: 'Chapter 1', language: 'en', blocks: [
                { id: 'o1', type: 'paragraph', content: [{ text: 'One.', marks: [] }] },
                { id: 'o2', type: 'separator', content: [] },
                { id: 'o3', type: 'paragraph', content: [{ text: 'Still one.', marks: [] }] },
            ] } },
            'POST /api/studio/editions/12/originals/separate': { status: 201, body: { saved: 1, from: 1, to: 1 } },
        });
        const list = await screen.findByRole('complementary', { name: 'Глави оригіналу' });
        expect(within(list).getByText('оригінал є в 2 з 5')).toBeInTheDocument();
        expect(within(list).getByText('перекладено · англ.')).toBeInTheDocument();
        expect(await screen.findByRole('heading', { name: 'Нова глава 3' })).toBeInTheDocument();

        await userEvent.click(within(list).getByRole('button', { name: /Chapter 1/ }));
        expect(await screen.findByRole('heading', { name: 'Оригінал глави 1' })).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Текст глави' })).toHaveValue('One.\n* * *\nStill one.');
        expect(screen.getByRole('button', { name: 'Зберегти' })).toBeDisabled();
        await userEvent.type(screen.getByRole('textbox', { name: 'Текст глави' }), '!');
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти й до наступної' }));
        expect(await screen.findByRole('heading', { name: 'Нова глава 3' })).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Текст глави' })).toHaveValue('');
        expect(calls.find((call) => call.method === 'POST' && call.path === '/api/studio/editions/12/originals/separate')!.body).toEqual({
            chapters: [{ title: 'Chapter 1', text: 'One.\n* * *\nStill one.!' }], first: 1, language: 'en',
        });
    });

    it('is offered to everyone in the team who translates: pasting is not AI', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: ME },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/12': { body: EDITION },
            'GET /api/studio/editions/12/chapters': { body: NO_CHAPTERS },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        expect(await screen.findByRole('link', { name: 'Оригінал' })).toHaveAttribute('href', '/studio/12/originals');
    });
});

describe('deleting', () => {
    const OVERVIEW = { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'human',
        status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika',
        role: 'owner', ...COUNTS };

    it('sends a code first and deletes the translation only with it, into the bin', async () => {
        const { calls } = await renderAt('/studio/12/settings', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12': { body: OVERVIEW },
            'GET /api/studio/editions/12/contributions': { body: [] },
            'POST /api/studio/editions/12/delete-code': { body: { email: 'm***@example.com', minutes: 15 } },
            'POST /api/studio/editions/12/delete': { body: '' },
            'GET /api/studio/trash': { body: [{ editionId: 12, title: 'Лиходійка', teamName: 'mika', deletedAt: '2026-10-18T10:00:00Z',
                erasedAt: '2026-11-17T10:00:00Z' }] },
            'GET /api/studio': { body: [] },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Видалити переклад' }));
        await userEvent.click(screen.getByRole('button', { name: 'Надіслати код на пошту й у Telegram' }));
        expect(await screen.findByText(/Код надіслано на m\*\*\*@example\.com/)).toBeInTheDocument();
        const dialog = screen.getByRole('dialog');
        expect(within(dialog).getByRole('button', { name: 'Видалити переклад' })).toBeDisabled();
        await userEvent.type(within(dialog).getByLabelText('Код'), '123456');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Видалити переклад' }));
        await waitFor(() => expect(calls.find((call) => call.path.endsWith('/delete'))?.body).toEqual({ code: '123456' }));
        expect(await screen.findByRole('button', { name: 'Відновити' })).toBeInTheDocument();
    });

    it('puts a chapter in the bin after «are you sure» and brings it back', async () => {
        const { calls } = await renderAt('/studio/12/chapters', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12': { body: OVERVIEW },
            'GET /api/studio/editions/12/chapters': { body: { items: [{ number: 2, label: null, title: 'Друга', published: true, hasMyDraft: false,
                pending: 0, updatedAt: '2026-10-18T10:00:00Z' }], total: 1, page: 1, hasMore: false } },
            'DELETE /api/studio/editions/12/chapters/2': { body: '' },
            'GET /api/studio/editions/12/chapters/trash': { body: [{ number: 1, label: null, title: 'Перша', deletedAt: '2026-10-18T10:00:00Z' }] },
            'POST /api/studio/editions/12/chapters/1/restore': { body: '' },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Видалити главу 2' }));
        expect(await screen.findByText(/номер лишиться порожнім/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Видалити' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'DELETE' && call.path.endsWith('/chapters/2'))).toBe(true));
        expect(screen.getByRole('heading', { name: 'Кошик · 1' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Повернути' }));
        await waitFor(() => expect(calls.some((call) => call.path.endsWith('/chapters/1/restore'))).toBe(true));
    });
});
