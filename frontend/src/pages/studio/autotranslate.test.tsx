import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: true, showShah: true,
};
const stage = (model: string) => ({ model, inputPerMillion: 0.4, outputPerMillion: 1.6, enabled: true });
const SETTINGS = { analyze: stage('openai/gpt-4.1-mini'), translate: stage('openai/gpt-4.1-mini'), proofread: stage('openai/gpt-4.1-mini'),
    segmentChars: 4000, microUsdPerShah: 36000, capFactor: 3 };
const OVERVIEW = {
    configured: true, showShah: true, sourceChapters: 50, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 20, nextToAnalyze: 21,
    averageChars: 6000, balance: { shah: 208, usd: 7.5 }, usdPerShah: 0.036, settings: SETTINGS, jobs: [], personal: false, reserved: 0,
    presets: [
        { id: 1, name: 'Копійка', summary: 'Найдешевше.', rating: 3.5, analyze: 'deepseek/deepseek-v4-pro', translate: 'deepseek/deepseek-v4-flash',
            proofread: null, analysisUsd: 0.002, chapterUsd: 0.0074 },
        { id: 5, name: 'Швидкий+', summary: 'Найкращий з дешевих.', rating: 4, analyze: 'anthropic/claude-sonnet-5.5', translate: 'x-ai/grok-4.3',
            proofread: 'deepseek/deepseek-v4-flash', analysisUsd: 0.027, chapterUsd: 0.089 },
    ],
};
const quote = (over: object = {}) => ({
    kind: 'translate', from: 1, to: 3, chapters: 3, skipped: 0, shah: 3, usd: 0.11, expectedUsd: 0.105, estimated: true, unanalyzed: 0,
    analyzeModel: SETTINGS.analyze, translateModel: SETTINGS.translate, proofreadModel: SETTINGS.proofread,
    steps: ['analyze', 'translate', 'proofread'], ...over,
});
const FAILED_JOB = {
    id: 9, kind: 'translate', state: 'failed', from: 1, to: 3, done: 1, quoteShah: 3, spentUsd: 0.012, spentShah: 1,
    current: { number: 2, stage: 'translate', state: 'failed', error: 'Відповідь моделі загубилася дорогою.', part: 0, parts: 0, progress: 0.1 },
    error: 'Відповідь моделі загубилася дорогою. Перевірте баланс і натисніть «Продовжити».', createdAt: '2026-09-25T08:00:00Z', finishedAt: null,
};

const EDITION4 = { editionId: 4, novelSlug: 'mah-vody', title: 'Маг води', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 3, ownNovel: false, teamHandle: 'panrid', teamName: 'panrid', role: 'owner',
    sourceChapters: 50, pendingSuggestions: 0, drafts: 0, newWords: 0, originalUrl: null };

afterEach(() => vi.unstubAllGlobals());

describe('glossary word in the chapters', () => {
    it('offers to change the old form in the translated chapters after an edit', async () => {
        const { calls } = await renderAt('/studio/4/glossary', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/glossary': { body: { items: [{ id: 2, ukrainian: 'Рьо', kind: 'character', gender: 'male', note: null, chapter: 1, manual: false, status: 'approved' }],
                total: 1, page: 1, hasMore: false, chapters: [1], labels: { 1: '0' }, counts: { new: 0, approved: 1, rejected: 0 } } },
            'PUT /api/studio/editions/4/glossary/2': { status: 200 },
            'GET /api/studio/editions/4/glossary/2/occurrences': { body: { form: 'Рьо', total: 5, chapters: [{ number: 1, label: '0', title: '', count: 5, snippets: [] }] } },
            'POST /api/studio/editions/4/glossary/2/rewrite': { body: { paragraphs: 4, chapters: 1 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: /^Рьо/ }));
        const field = screen.getByLabelText('Українською');
        await userEvent.clear(field);
        await userEvent.type(field, 'Ріо');
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        expect(await screen.findByText(/«Рьо» трапляється 5 раз\(и\) у 1 главах/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Надіслати правками на перевірку' }));
        expect(await screen.findByText(/Правок на перевірку: 4/)).toBeInTheDocument();
        expect(calls.find((call) => call.path.endsWith('/rewrite'))?.body).toEqual({ from: 'Рьо', apply: false, ai: false });
        expect(calls.find((call) => call.path.endsWith('/occurrences') && call.query.includes('form='))).toBeDefined();
    });
});

describe('a character\'s gender changed', () => {
    it('lists where they are named and lets the model make the words agree', async () => {
        const { calls } = await renderAt('/studio/4/glossary', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/glossary': { body: { items: [{ id: 2, ukrainian: 'Сашко', kind: 'character', gender: 'male', note: null, chapter: 1, manual: false, status: 'approved' }],
                total: 1, page: 1, hasMore: false, chapters: [1], labels: { 1: '1' }, counts: { new: 0, approved: 1, rejected: 0 } } },
            'PUT /api/studio/editions/4/glossary/2': { status: 200 },
            'GET /api/studio/editions/4/glossary/2/occurrences': { body: { form: 'Сашко', total: 2, chapters: [{ number: 1, label: '1', title: 'Сніг', count: 2, snippets: ['Сашко пішов…'] }] } },
            'GET /api/studio/editions/4': { body: EDITION4 },
            'POST /api/studio/editions/4/glossary/2/regender': { body: { paragraphs: 1, chapters: 1 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: /^Сашко/ }));
        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Рід' }), 'female');
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        const sheet = await screen.findByRole('dialog', { name: 'Рід: жіночий' });
        expect(await within(sheet).findByRole('link', { name: 'гл. 1' })).toHaveAttribute('href', '/studio/4/chapters/1');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Виправити ШІ правками на перевірку' }));
        expect(await within(sheet).findByText('Правок на перевірку: 1.')).toBeInTheDocument();
        expect(calls.find((call) => call.path.endsWith('/regender'))?.body).toEqual({ apply: false });
    });
});

describe('autotranslate', () => {
    it('waits until the number is typed, then shows the price and starts', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'POST /api/studio/editions/4/autotranslate/quote': (body) => ({ body: quote({ to: (body as { to: number }).to }) }),
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.type(await screen.findByLabelText('По главу'), '30');
        expect(await screen.findByText(/орієнтовно/, {}, { timeout: 2000 })).toHaveTextContent('3 шаги');
        const quotes = calls.filter((call) => call.path.endsWith('/quote'));
        expect(quotes.map((call) => call.body)).toEqual([{ kind: 'translate', steps: ['analyze', 'translate', 'proofread'], to: 30 }]);
        expect(screen.getByText(/Очікувана собівартість/)).toHaveTextContent('$0,105');

        await userEvent.click(screen.getByRole('button', { name: /^Запустити/ }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body).toEqual({ kind: 'translate', steps: ['analyze', 'translate', 'proofread'], to: 30 });
    });

    it('picks a ready set of models in one tap and shows each step\'s model with its price', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'GET /api/studio/autotranslate/models': { body: [
                { id: 'x-ai/grok-4.3', name: 'Grok 4.3', inputPerMillion: 1.25, outputPerMillion: 2.5, chapterUsd: 0.063, rating: 'usual', measuredChapters: 83 },
                { id: 'openai/gpt-4.1-mini', name: 'GPT-4.1 mini', inputPerMillion: 0.4, outputPerMillion: 1.6, chapterUsd: 0.012, rating: 'recommended', measuredChapters: null },
            ] },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote() },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        expect(await screen.findByRole('button', { name: 'Як на сайті' })).toHaveAttribute('aria-pressed', 'true');
        expect(await screen.findByText(/за главу, оцінка/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Швидкий+' }));
        expect(screen.getByText(/Найкращий з дешевих/)).toHaveTextContent('Оцінка якості — 4 з 5');
        expect(await screen.findByText('grok-4.3')).toBeInTheDocument();
        expect(await screen.findByText(/виміряно на 83/)).toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('По главу'), '3');
        await screen.findByText(/набір «Швидкий\+»/, {}, { timeout: 2000 });
        await userEvent.click(screen.getByRole('button', { name: /^Запустити/ }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body)
            .toEqual({ kind: 'translate', steps: ['analyze', 'translate', 'proofread'], to: 3, preset: 5 });
    });

    it('proofreads chapters already published, alone', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote({ kind: 'proofread', from: 1, to: 3, steps: ['proofread'] }) },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('button', { name: /^Аналіз\s*словник/ }));
        await userEvent.click(screen.getByRole('button', { name: /^Переклад\s*з оригіналу/ }));
        expect(screen.getByRole('button', { name: /^Вичитка\s*редагує/ })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByText(/Вичитка вже перекладених глав/)).toBeInTheDocument();
        expect(screen.queryByRole('switch', { name: /Зробити заново/ })).not.toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('По главу'), '3');
        await screen.findByText(/Очікувана собівартість/, {}, { timeout: 2000 });
        await userEvent.click(screen.getByRole('button', { name: 'Запустити: вичитка' }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body).toEqual({ kind: 'proofread', steps: ['proofread'], to: 3 });
    });

    it('fills the bar part by part while a long chapter is translated and turns once per refresh', async () => {
        const running = { ...FAILED_JOB, id: 10, state: 'running', from: 51, to: 60, done: 0, error: null,
            current: { number: 51, stage: 'translate', state: 'running', error: null, part: 2, parts: 7, progress: 0.27 } };
        await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, jobs: [running] } },
        });
        const bar = await screen.findByRole('progressbar');
        expect(bar).toHaveAttribute('aria-valuenow', '3');
        expect(screen.getByText(/глава 51: переклад, частина 3 з 7/)).toBeInTheDocument();
        expect(screen.getByRole('img', { name: 'Оновлюється' })).toBeInTheDocument();
    });

    it('says under the field that earlier chapters are done, without asking the server', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
        });
        await userEvent.click(await screen.findByRole('button', { name: /^Переклад\s*з оригіналу/ }));
        await userEvent.click(screen.getByRole('button', { name: /^Вичитка\s*редагує/ }));
        const field = screen.getByLabelText('По главу');
        await userEvent.type(field, '3');
        await vi.waitFor(() => expect(screen.getByText(/Глави до 20 уже проаналізовано/)).toBeInTheDocument(), { timeout: 2000 });
        expect(calls.some((call) => call.path.endsWith('/quote'))).toBe(false);
        await userEvent.type(field, '0');
        await vi.waitFor(() => expect(screen.queryByText(/Глави до 20/)).not.toBeInTheDocument(), { timeout: 2000 });
    });

    it('redoes chapters with another model from the advanced settings', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'GET /api/studio/autotranslate/models': { body: [{ id: 'fake/better', name: 'Better', inputPerMillion: 2, outputPerMillion: 8, chapterUsd: 0.21, rating: 'recommended' }] },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote({ kind: 'analyze', from: 1, to: 20, chapters: 20, shah: 5 }) },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('button', { name: /^Переклад\s*з оригіналу/ }));
        await userEvent.click(screen.getByRole('button', { name: /^Вичитка\s*редагує/ }));
        await userEvent.type(screen.getByLabelText('З глави'), '1');
        await userEvent.click(screen.getByRole('switch', { name: 'Зробити заново вже опрацьовані глави' }));
        await userEvent.click(screen.getByRole('button', { name: 'Аналіз: змінити модель' }));
        const picker = screen.getByRole('combobox', { name: 'Модель: аналіз' });
        await userEvent.clear(picker);
        await userEvent.type(picker, 'better');
        const option = await screen.findByRole('option', { name: /fake\/better/ }, { timeout: 2000 });
        expect(option).toHaveTextContent('≈ $0,210 за главу');
        expect(option).toHaveTextContent('рекомендована');
        // The list hides only weak models unless asked.
        expect(calls.filter((call) => call.path.includes('/models') && call.query.includes('q=better')).every((call) => call.query.includes('show=usual'))).toBe(true);
        await userEvent.click(option);
        await userEvent.type(screen.getByLabelText('По главу'), '20');
        await screen.findByText(/20 глав/, {}, { timeout: 2000 });
        await userEvent.click(screen.getByRole('button', { name: /^Запустити/ }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body)
            .toEqual({ kind: 'analyze', steps: ['analyze'], to: 20, from: 1, redo: true, models: { analyze: 'fake/better' } });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Показувати й слабкі моделі' }));
        await userEvent.click(screen.getByRole('button', { name: 'Аналіз: змінити модель' }));
        await userEvent.type(screen.getByRole('combobox', { name: 'Модель: аналіз' }), 'x');
        await vi.waitFor(() => expect(calls.some((call) => call.query.includes('show=weak') && !call.query.includes('fake'))).toBe(true), { timeout: 2000 });
    });

    it('speaks dollars when the owner switched шаги off', async () => {
        await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: { ...OWNER, showShah: false } },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, showShah: false } },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote({ kind: 'analyze', from: 21, to: 23 }) },
        });
        expect(await screen.findByText(/Баланс:/)).toHaveTextContent('$7,50');
        await userEvent.click(screen.getByRole('button', { name: /^Переклад\s*з оригіналу/ }));
        await userEvent.click(screen.getByRole('button', { name: /^Вичитка\s*редагує/ }));
        await userEvent.type(screen.getByLabelText('По главу'), '23');
        expect(await screen.findByText(/3 глави · орієнтовно/, {}, { timeout: 2000 })).toHaveTextContent('$0,11');
    });

    it('explains a stopped job and lets the owner continue it', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, jobs: [FAILED_JOB] } },
            'POST /api/studio/editions/4/autotranslate/jobs/9/resume': { status: 204 },
        });
        const card = (await screen.findByRole('progressbar')).closest<HTMLElement>('[aria-live]')!;
        expect(within(card).getByText('зупинено')).toBeInTheDocument();
        expect(within(card).getByRole('alert')).toHaveTextContent('загубилася');
        expect(screen.queryByRole('button', { name: /^Запустити/ })).not.toBeInTheDocument();
        await userEvent.click(within(card).getByRole('button', { name: 'Продовжити' }));
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/jobs/9/resume'))).toBe(true);
        expect(within(card).getByRole('link', { name: /Журнал запуску/ })).toHaveAttribute('href', '/studio/4/translate/jobs/9');
    });

    it('asks before cancelling a run and restores the last one cancelled', async () => {
        const running = { ...FAILED_JOB, state: 'running', error: null,
            current: { number: 2, stage: 'translate', state: 'running', error: null, part: 1, parts: 4, progress: 0.3 } };
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, jobs: [running] } },
            'POST /api/studio/editions/4/autotranslate/jobs/9/cancel': { status: 204 },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Скасувати' }));
        const dialog = await screen.findByRole('dialog', { name: 'Скасувати переклад?' });
        expect(calls.some((call) => call.path.endsWith('/cancel'))).toBe(false);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Скасувати переклад' }));
        await vi.waitFor(() => expect(calls.some((call) => call.path.endsWith('/jobs/9/cancel'))).toBe(true));
    });

    it('offers to take up the novel\'s last cancelled run again', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, jobs: [{ ...FAILED_JOB, state: 'cancelled', error: null }] } },
            'POST /api/studio/editions/4/autotranslate/jobs/9/resume': { status: 204 },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Відновити' }));
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/jobs/9/resume'))).toBe(true);
    });

    it('offers the Syosetu path in a new publication to the site owner', async () => {
        const { router } = await renderAt('/studio/new', {
            'GET /api/me': { body: OWNER },
            'GET /api/me/teams': { body: [{ handle: 'mavka', name: 'Мавка', role: 'owner' }] },
            'POST /api/studio/autotranslate/prepare': { body: { editionId: 12, novelSlug: 'likhtarnyk' } },
            'GET /api/studio/editions/12/autotranslate': { body: OVERVIEW },
            'GET /api/studio/editions/12': { body: { ...EDITION4, editionId: 12, novelSlug: 'likhtarnyk' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: /Автопереклад із Syosetu/ }));
        await userEvent.type(screen.getByLabelText('Посилання на новелу'), 'https://ncode.syosetu.com/n0022gd/');
        await userEvent.click(screen.getByRole('button', { name: 'Підготувати' }));
        expect(await screen.findByRole('link', { name: 'Автопереклад' })).toHaveAttribute('aria-current', 'page');
        expect(router.state.location.pathname).toBe('/studio/12/translate');
    });
});

describe('glossary', () => {
    const page = (items: object[], counts = { new: 2, approved: 1, rejected: 0 }) => ({
        items, total: items.length, page: 1, hasMore: false, chapters: [1, 2], labels: { 1: 'Пролог', 2: '1' }, counts,
    });
    const entry = (id: number, ukrainian: string, status = 'new') => ({
        id, ukrainian, kind: 'character', gender: 'male', note: null, chapter: 1, manual: false, status,
    });

    it('filters by chapter and approves the selected entries', async () => {
        const { calls } = await renderAt('/studio/4/glossary', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/glossary': { body: page([entry(1, 'Абель'), entry(2, 'Рьо')]) },
            'POST /api/studio/editions/4/glossary/status': { body: { changed: 1 } },
        });
        expect(await screen.findByRole('button', { name: 'Нові · 2' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('option', { name: 'з «Пролог»' })).toBeInTheDocument();
        expect(screen.getByRole('option', { name: 'з глави 1' })).toBeInTheDocument();
        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Глава' }), '1');
        await vi.waitFor(() => expect(calls.some((call) => call.path.endsWith('/glossary') && call.method === 'GET')).toBe(true));
        await userEvent.click(screen.getByRole('button', { name: 'Виділити' }));
        await userEvent.click(screen.getByRole('checkbox', { name: 'Виділити Рьо' }));
        await userEvent.click(screen.getByRole('button', { name: 'Затвердити (1)' }));
        expect(calls.find((call) => call.path.endsWith('/glossary/status'))?.body).toEqual({ ids: [2], status: 'approved' });
        await userEvent.click(screen.getByRole('button', { name: 'Скасувати' }));
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });

    it('rejects one entry right on its row and shows the original behind a button', async () => {
        const { calls } = await renderAt('/studio/4/glossary', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/glossary': { body: page([entry(2, 'Рьо')]) },
            'POST /api/studio/editions/4/glossary/status': { body: { changed: 1 } },
            'GET /api/studio/editions/4/glossary/2/occurrences': { body: { form: 'Рьо', total: 3, chapters: [
                { number: 1, label: '0', title: 'Пролог', count: 2, snippets: ['…Рьо прокинувся…'] },
                { number: 3, label: '2', title: 'Ліс', count: 1, snippets: [] },
            ] } },
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/studio/editions/4/glossary/2/original': { body: {
                language: 'ja', original: 'リョウ', reading: 'りょう', aliases: [], others: [{ language: 'en', original: 'Ryo' }],
                sourceChapter: 1, snippet: '…リョウは目を開けた…',
                chapter: { slug: 'mah-vody', team: 'panrid', number: 1, label: '0' },
            } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Відхилити Рьо' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/glossary/status'))?.body).toEqual({ ids: [2], status: 'rejected' }));

        await userEvent.click(screen.getByRole('button', { name: /^Рьо/ }));
        await userEvent.click(screen.getByRole('button', { name: /Оригінал/ }));
        expect(await screen.findByText('リョウ')).toBeInTheDocument();
        expect(screen.getByText('…リョウは目を開けた…')).toBeInTheDocument();
        expect(screen.getByText('Ryo').parentElement).toHaveTextContent('Англійською: Ryo');
        // Every chapter that uses the word, each opening it at the word.
        const first = await screen.findByRole('link', { name: /Глава 0 · Пролог — 2/ });
        expect(first.getAttribute('href')).toContain('find=');
        expect(screen.getByText('У тексті: 3 раз(и) у 2 главах')).toBeInTheDocument();
    });
});
