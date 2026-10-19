import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

/** Етап 20, крок 4: «Мої переклади», «Хід перекладу» наживо and «Словник» as the new design draws them. */

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, ai: true,
};
const SHAHS = { available: 9, reserved: 2, usdPerShah: 0.07, hasMore: false, total: 0, page: 1, running: [], history: [] };
const JOB = {
    id: 9, kind: 'translate', state: 'running', from: 13, to: 15, done: 0, quoteShah: 6, spentUsd: 0.01, spentShah: 1,
    current: { number: 13, stage: 'translate', state: 'running', error: null, part: 1, parts: 2, progress: 0.5 },
    error: null, createdAt: '2026-10-19T08:00:00Z', finishedAt: null, personal: true, chargedShah: 0,
};
const EDITION = {
    editionId: 4, novelSlug: 'dohliadach-maiaka', title: 'Доглядач маяка', coverUrl: null, kind: 'mixed', status: 'ongoing', chapterCount: 12,
    teamHandle: 'mika', teamName: 'mika', role: 'owner', drafts: 1, pendingSuggestions: 3, newWords: 0, jobState: 'running', jobChapter: 13,
    originals: 64, sourceLanguage: 'en', lastChapter: 12,
};
const OVERVIEW = {
    editionId: 4, novelSlug: 'dohliadach-maiaka', title: 'Доглядач маяка', author: '', description: [], tags: [], kind: 'mixed', status: 'ongoing',
    adult: false, coverUrl: null, chapterCount: 12, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner', sourceChapters: null,
    pendingSuggestions: 3, drafts: 1, newWords: 0, originalUrl: null, originals: 64,
};

afterEach(() => vi.unstubAllGlobals());

describe('«Мої переклади»', () => {
    it('shows how far each translation has got, what is being translated now and what waits', async () => {
        await renderAt('/studio', {
            'GET /api/me': { body: ME },
            'GET /api/me/shahs': { body: SHAHS },
            'GET /api/studio': { body: [EDITION] },
            'GET /api/studio/autotranslate/processes': { body: { items: [{ editionId: 4, title: 'Доглядач маяка', slug: 'dohliadach-maiaka', job: JOB }], total: 1, page: 1, hasMore: false } },
        });
        expect(await screen.findByRole('heading', { name: 'Мої переклади' })).toBeInTheDocument();
        expect(await screen.findByText('Перекладено 12 з 64 глав · з англійської')).toBeInTheDocument();
        expect(screen.getByText('1 чернетка')).toBeInTheDocument();
        expect(screen.getByText('3 правки')).toBeInTheDocument();
        expect(screen.getByText('перекладається · гл. 13')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Глава 12' })).toHaveAttribute('href', '/studio/4/chapters/12');
        expect(screen.getByRole('link', { name: 'Перекласти далі' })).toHaveAttribute('href', '/studio/4/translate');
        expect(await screen.findByRole('link', { name: /Доглядач маяка · глава 13/ })).toHaveAttribute('href', '/studio/4/translate/jobs/9');
        expect(screen.getByRole('link', { name: /Правки читачів/ })).toHaveAttribute('href', '/studio/4/suggestions');

        // The Studio's own header: its menu, the balance and «+ Новий переклад».
        const menu = screen.getByRole('navigation', { name: 'Меню Студії' });
        expect(await within(menu).findByRole('link', { name: 'Хід перекладу · 1' })).toHaveAttribute('href', '/studio/processes');
        expect(screen.getByRole('link', { name: '9 шагів на рахунку' })).toHaveAttribute('href', '/me/shahs');
        expect(screen.getByRole('link', { name: '+ Новий переклад' })).toHaveAttribute('href', '/studio/new');
    });

    it('offers nothing with AI to a person it is hidden from', async () => {
        await renderAt('/studio', {
            'GET /api/me': { body: { ...ME, ai: false } },
            'GET /api/studio': { body: [{ ...EDITION, jobState: null, jobChapter: null, newWords: 5 }] },
        });
        expect(await screen.findByText('Перекладено 12 з 64 глав · з англійської')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Перекласти далі' })).not.toBeInTheDocument();
        expect(screen.queryByText('Перекладається зараз')).not.toBeInTheDocument();
        expect(screen.queryByText(/нових слів/)).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /Хід перекладу/ })).not.toBeInTheDocument();
        expect(screen.queryByText(/на рахунку/)).not.toBeInTheDocument();
    });
});

describe('«Хід перекладу» наживо', () => {
    const CHAPTER = {
        number: 13, stage: 'translate', state: 'running', error: null, language: 'en', title: 'Chapter 13: The Keeper’s Log',
        translatedTitle: 'Журнал доглядача', proofreading: true,
        paragraphs: [
            { id: 'p1', type: 'paragraph', original: 'The log lay exactly where the previous keeper had left it.', translation: 'Журнал лежав там, де його лишив попередній доглядач.', revised: false },
            { id: 'p2', type: 'paragraph', original: 'Lina hesitated for a long time before she unwrapped it.', translation: null, revised: false },
        ],
        words: [
            { id: 1, ukrainian: 'Ліна', original: 'Lina', kind: 'character', gender: 'female', status: 'approved' },
            { id: 2, ukrainian: 'маяк Сірого Рогу', original: 'Greyhorn Light', kind: 'place', gender: null, status: 'new' },
        ],
        fixes: [],
    };
    const STEPS = [{ number: 13, stage: 'translate', state: 'running' }, { number: 14, stage: 'fetch', state: 'pending' }];

    it('shows the original beside the translation saved so far and the paragraph at work', async () => {
        const { calls } = await renderAt('/studio/4/translate/jobs/9', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/4': { body: OVERVIEW },
            'GET /api/studio/editions/4/autotranslate/jobs/9/live': { body: { job: JOB, steps: STEPS, chapter: CHAPTER } },
            'POST /api/studio/editions/4/autotranslate/jobs/9/cancel': { status: 204 },
        });
        expect(await screen.findByRole('heading', { name: 'Глава 13 · Журнал доглядача' })).toBeInTheDocument();
        const stages = screen.getByRole('list', { name: 'Етапи глави' });
        expect(within(stages).getByText('Переклад').closest('li')).toHaveAttribute('aria-current', 'step');
        expect(within(stages).getByText('Вичитка')).toBeInTheDocument();
        expect(screen.getByText('переклад · частина 2 з 2')).toBeInTheDocument();
        expect(screen.getByText('Журнал лежав там, де його лишив попередній доглядач.')).toBeInTheDocument();
        // The paragraph at work is lit; it has no translation yet.
        expect(screen.getByText('Lina hesitated for a long time before she unwrapped it.').closest('[aria-current]'))
            .toHaveAttribute('aria-current', 'true');
        const words = screen.getByRole('complementary', { name: 'Словник і вичитка' });
        expect(within(words).getByText('маяк Сірого Рогу').parentElement).toHaveTextContent('нове');
        expect(within(words).getByText('З’явиться після вичитки.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Подробиці по кроках' })).toHaveAttribute('href', '/studio/4/translate/jobs/9/log');

        await userEvent.click(screen.getByRole('button', { name: /Глава 14/ }));
        await vi.waitFor(() => expect(calls.some((call) => call.path.endsWith('/jobs/9/live') && call.query.includes('chapter=14'))).toBe(true));

        await userEvent.click(screen.getByRole('button', { name: 'Скасувати' }));
        const dialog = await screen.findByRole('dialog', { name: 'Скасувати переклад?' });
        await userEvent.click(within(dialog).getByRole('button', { name: 'Скасувати переклад' }));
        await vi.waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/jobs/9/cancel'))).toBe(true));
    });

    it('ends a finished chapter with what proofreading changed and the way to the workspace', async () => {
        const done = {
            ...CHAPTER, stage: 'done', state: 'done',
            paragraphs: CHAPTER.paragraphs.map((paragraph) => ({ ...paragraph, translation: paragraph.translation ?? 'Ліна довго не наважувалась.', revised: true })),
            fixes: [{ id: 'p2', before: 'довго не наважувалась', after: 'довго вагалася' }],
        };
        await renderAt('/studio/4/translate/jobs/9', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/4': { body: OVERVIEW },
            'GET /api/studio/editions/4/autotranslate/jobs/9/live': { body: {
                job: { ...JOB, state: 'done', done: 3, current: null, finishedAt: '2026-10-19T08:10:00Z', chargedShah: 5 },
                steps: [{ number: 13, stage: 'done', state: 'done' }], chapter: done,
            } },
        });
        expect(await screen.findByText('Глава 13 готова.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Відкрити в робочому місці' })).toHaveAttribute('href', '/studio/4/chapters/13');
        expect(screen.getByText('довго не наважувалась').tagName).toBe('DEL');
        expect(screen.getByText('довго вагалася').tagName).toBe('INS');
        expect(screen.getByText('Абзац 2')).toBeInTheDocument();
        expect(screen.getByText('Списано 5 шагів')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Скасувати' })).not.toBeInTheDocument();
    });
});

describe('«Словник» table and card', () => {
    const entry = (over: object = {}) => ({
        id: 2, ukrainian: 'Ліна', kind: 'character', gender: 'female', note: null, chapter: 1, manual: false, status: 'new', facts: 0,
        looseForms: false, main: null, others: [], inStory: null, original: 'Lina', portraitUrl: null, ...over,
    });
    const page = (items: object[]) => ({
        items, total: items.length, page: 1, hasMore: false, chapters: [1], labels: { 1: '1' }, counts: { new: 1, approved: 4, rejected: 0 },
    });

    it('filters by kind, approves the new entries at once and gives an entry a portrait from «Мої картинки»', async () => {
        const { calls } = await renderAt('/studio/4/glossary', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/4': { body: OVERVIEW },
            'GET /api/studio/editions/4/glossary': { body: page([entry()]) },
            'GET /api/studio/editions/4/glossary/2/facts': { body: [] },
            'POST /api/studio/editions/4/glossary/approve-new': { body: { changed: 1 } },
            'PUT /api/studio/editions/4/glossary/2/portrait': { status: 204 },
            'GET /api/me/pictures': { body: { items: [{
                id: 31, url: '/media/ryo.jpg', fullUrl: '/media/ryo-full.jpg', width: 600, height: 800, fileName: 'lina.png', drawn: false,
                prompt: null, fragment: null, sourceUrl: null, createdAt: '2026-10-19T08:00:00Z', uses: [],
            }], total: 1, page: 1 } },
        });
        const table = await screen.findByRole('table');
        expect(within(table).getByText('Lina')).toBeInTheDocument();
        expect(within(table).getByText('нове')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Усі · 5' })).toBeInTheDocument();

        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Вид' }), 'place');
        await vi.waitFor(() => expect(calls.some((call) => call.path.endsWith('/glossary') && call.query.includes('kind=place'))).toBe(true));

        await userEvent.click(screen.getByRole('button', { name: 'Погодити нові' }));
        const dialog = await screen.findByRole('dialog', { name: 'Погодити всі нові записи?' });
        await userEvent.click(within(dialog).getByRole('button', { name: 'Погодити нові' }));
        await vi.waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/glossary/approve-new'))).toBe(true));

        await userEvent.click(screen.getByRole('button', { name: /^Ліна/ }));
        const card = await screen.findByRole('complementary', { name: 'Запис словника: Ліна' });
        expect(within(card).getByRole('img', { name: 'Портрет (ще немає)' })).toBeInTheDocument();
        await userEvent.click(within(card).getByRole('button', { name: 'З моїх картинок' }));
        const pictures = await screen.findByRole('dialog', { name: 'Мої картинки' });
        await userEvent.click(await within(pictures).findByRole('button', { name: 'lina.png' }));
        await userEvent.click(within(pictures).getByRole('button', { name: 'Вставити' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/glossary/2/portrait'))?.body).toEqual({ imageId: 31 }));
    });
});
