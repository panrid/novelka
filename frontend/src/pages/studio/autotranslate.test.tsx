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
    averageChars: 6000, balance: { shah: 208, usd: 7.5 }, usdPerShah: 0.036, settings: SETTINGS, jobs: [],
};
const quote = (over: object = {}) => ({
    kind: 'translate', from: 1, to: 3, chapters: 3, skipped: 0, shah: 3, usd: 0.11, expectedUsd: 0.105, estimated: true, unanalyzed: 0,
    analyzeModel: SETTINGS.analyze, translateModel: SETTINGS.translate, proofreadModel: SETTINGS.proofread, ...over,
});
const FAILED_JOB = {
    id: 9, kind: 'translate', state: 'failed', from: 1, to: 3, done: 1, quoteShah: 3, spentUsd: 0.012, spentShah: 1,
    current: { number: 2, stage: 'translate', state: 'failed', error: 'Відповідь моделі загубилася дорогою.' },
    error: 'Відповідь моделі загубилася дорогою. Перевірте баланс і натисніть «Продовжити».', createdAt: '2026-09-25T08:00:00Z', finishedAt: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('autotranslate', () => {
    it('waits until the number is typed, then shows the price and starts', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'POST /api/studio/editions/4/autotranslate/quote': (body) => ({ body: quote({ to: (body as { to: number }).to }) }),
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('radio', { name: 'Переклад' }));
        await userEvent.type(screen.getByLabelText('Перекласти з глави 1 до глави…'), '30');
        expect(await screen.findByText(/орієнтовно/, {}, { timeout: 2000 })).toHaveTextContent('3 шаги');
        const quotes = calls.filter((call) => call.path.endsWith('/quote'));
        expect(quotes.map((call) => call.body)).toEqual([{ kind: 'translate', to: 30 }]);
        expect(screen.getByText(/Очікувана собівартість/)).toHaveTextContent('$0,105');

        await userEvent.click(screen.getByRole('button', { name: 'Почати переклад' }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body).toEqual({ kind: 'translate', to: 30 });
    });

    it('says under the field that earlier chapters are done, without asking the server', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
        });
        const field = await screen.findByLabelText('Аналізувати з глави 21 до глави…');
        await userEvent.type(field, '3');
        await vi.waitFor(() => expect(screen.getByText(/Глави до 20 уже проаналізовано/)).toBeInTheDocument(), { timeout: 2000 });
        expect(calls.some((call) => call.path.endsWith('/quote'))).toBe(false);
        await userEvent.type(field, '0');
        await vi.waitFor(() => expect(screen.queryByText(/Глави до 20/)).not.toBeInTheDocument(), { timeout: 2000 });
    });

    it('redoes chapters with another model from the advanced settings', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'GET /api/studio/autotranslate/models': { body: [{ id: 'fake/better', name: 'Better', inputPerMillion: 2, outputPerMillion: 8, chapterUsd: 0.21, rating: 'recommended' }] },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote({ kind: 'analyze', from: 1, to: 20, chapters: 20, shah: 5 }) },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('button', { name: /Розширені налаштування/ }));
        await userEvent.type(screen.getByLabelText('З глави'), '1');
        await userEvent.click(screen.getByRole('switch', { name: 'Зробити заново вже опрацьовані глави' }));
        const picker = screen.getByRole('combobox', { name: 'Модель аналізу' });
        await userEvent.clear(picker);
        await userEvent.type(picker, 'better');
        const option = await screen.findByRole('option', { name: /fake\/better/ }, { timeout: 2000 });
        expect(option).toHaveTextContent('≈ $0,210 за главу');
        expect(option).toHaveTextContent('рекомендована');
        // Only recommended models until the box is cleared.
        expect(calls.filter((call) => call.path.includes('/models')).every((call) => call.query.includes('show=recommended'))).toBe(true);
        expect(screen.getByRole('checkbox', { name: 'Показати й слабкі' })).toBeDisabled();
        await userEvent.click(option);
        await userEvent.type(screen.getByLabelText('Аналізувати з глави 1 до глави…'), '20');
        await screen.findByText(/20 глав/, {}, { timeout: 2000 });
        await userEvent.click(screen.getByRole('button', { name: 'Почати аналіз' }));
        expect(calls.find((call) => call.path.endsWith('/jobs'))?.body)
            .toEqual({ kind: 'analyze', to: 20, from: 1, redo: true, models: { analyze: 'fake/better' } });

        await userEvent.click(screen.getByRole('checkbox', { name: 'Лише рекомендовані моделі' }));
        await userEvent.click(screen.getByRole('checkbox', { name: 'Показати й слабкі' }));
        await userEvent.click(screen.getByRole('combobox', { name: 'Модель аналізу' }));
        await vi.waitFor(() => expect(calls.some((call) => call.query.includes('show=weak'))).toBe(true), { timeout: 2000 });
    });

    it('speaks dollars when the owner switched шаги off', async () => {
        await renderAt('/studio/4/translate', {
            'GET /api/me': { body: { ...OWNER, showShah: false } },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, showShah: false } },
            'POST /api/studio/editions/4/autotranslate/quote': { body: quote({ kind: 'analyze', from: 21, to: 23 }) },
        });
        expect(await screen.findByText(/Баланс:/)).toHaveTextContent('$7,50');
        await userEvent.type(screen.getByLabelText('Аналізувати з глави 21 до глави…'), '23');
        expect(await screen.findByText(/3 глави · орієнтовно/, {}, { timeout: 2000 })).toHaveTextContent('$0,11');
    });

    it('explains a stopped job and lets the owner continue it', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, jobs: [FAILED_JOB] } },
            'POST /api/studio/editions/4/autotranslate/jobs/9/resume': { status: 204 },
        });
        const card = (await screen.findByRole('progressbar')).parentElement!;
        expect(within(card).getByText('зупинено')).toBeInTheDocument();
        expect(within(card).getByRole('alert')).toHaveTextContent('загубилася');
        expect(screen.queryByRole('button', { name: 'Почати переклад' })).not.toBeInTheDocument();
        await userEvent.click(within(card).getByRole('button', { name: 'Продовжити' }));
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/jobs/9/resume'))).toBe(true);
    });

    it('offers the Syosetu path in a new publication to the site owner', async () => {
        const { router } = await renderAt('/studio/new', {
            'GET /api/me': { body: OWNER },
            'GET /api/me/teams': { body: [{ handle: 'mavka', name: 'Мавка', role: 'owner' }] },
            'POST /api/studio/autotranslate/prepare': { body: { editionId: 12, novelSlug: 'likhtarnyk' } },
            'GET /api/studio/editions/12/autotranslate': { body: OVERVIEW },
        });
        await userEvent.click(await screen.findByRole('button', { name: /Автопереклад із Syosetu/ }));
        await userEvent.type(screen.getByLabelText('Посилання на новелу'), 'https://ncode.syosetu.com/n0022gd/');
        await userEvent.click(screen.getByRole('button', { name: 'Підготувати' }));
        expect(await screen.findByRole('heading', { name: 'Автопереклад' })).toBeInTheDocument();
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
});
