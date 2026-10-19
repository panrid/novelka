import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const person = (role: string) => ({
    id: 2, nick: role === 'owner' ? 'panrid' : 'lysytsia', email: null, emailVerified: true, role, bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
});
const MINE = {
    available: 9, reserved: 2, usdPerShah: 0.07, hasMore: false, total: 2, page: 1,
    running: [{ amount: 2, what: 'Автопереклад «Маг води», глави 3–5', createdAt: new Date().toISOString() }],
    history: [
        { kind: 'charge', amount: 1, what: 'Ілюстрація', createdAt: new Date().toISOString() },
        { kind: 'grant', amount: 12, what: 'на пробу', createdAt: new Date().toISOString() },
    ],
};
const stage = (model: string) => ({ model, inputPerMillion: 0.4, outputPerMillion: 1.6, enabled: true });
const SETTINGS = { analyze: stage('openai/gpt-4.1-mini'), translate: stage('openai/gpt-4.1-mini'), proofread: stage('openai/gpt-4.1-mini'),
    segmentChars: 4000, microUsdPerShah: 70000, capFactor: 1 };

const EDITION4 = { editionId: 4, novelSlug: 'mah-vody', title: 'Маг води', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 3, ownNovel: false, teamHandle: 'panrid', teamName: 'panrid', role: 'owner',
    sourceChapters: 50, pendingSuggestions: 0, drafts: 0, newWords: 0, originalUrl: null };

afterEach(() => vi.unstubAllGlobals());

describe('шаги without payments', () => {
    it('shows a person what is left, what runs hold and what came and went', async () => {
        await renderAt('/me/shahs', {
            'GET /api/me': { body: person('reader') },
            'GET /api/me/shahs': { body: MINE },
        });
        expect(await screen.findByText('9 шагів')).toBeInTheDocument();
        expect(screen.getByText(/Ще 2 шаги у резерві/)).toBeInTheDocument();
        expect(screen.getByText('Автопереклад «Маг води», глави 3–5')).toBeInTheDocument();
        expect(screen.getByText('+12')).toBeInTheDocument();
        expect(screen.getByText(/«на пробу»/)).toBeInTheDocument();
        expect(screen.getByText('−1')).toBeInTheDocument();
    });

    it('lets the site owner grant шаги from the users list', async () => {
        const { calls } = await renderAt('/admin/users', {
            'GET /api/me': { body: person('owner') },
            'GET /api/admin/users': { body: { total: 1, page: 1, hasMore: false,
                items: [{ nick: 'lysytsia', role: 'reader', email: 'l@example.com', createdAt: new Date().toISOString(), lastSeenAt: null }] } },
            'POST /api/admin/users/lysytsia/shahs': { status: 201, body: { available: 10 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нарахувати шаги lysytsia' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Скільки шагів'), '10');
        await userEvent.type(within(dialog).getByLabelText('Примітка'), 'на пробу');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Нарахувати' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ shah: 10, note: 'на пробу' }));
        expect(await screen.findByText('lysytsia: нараховано 10 шагів, тепер 10 шагів.')).toBeInTheDocument();
    });

    it('a person runs autotranslation for шаги at the site’s models', async () => {
        await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: person('reader') },
            'GET /api/me/shahs': { body: MINE },
            'GET /api/studio/editions/4/autotranslate': { body: {
                configured: true, showShah: true, sourceChapters: 50, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 0, nextToAnalyze: 1,
                averageChars: 6000, balance: { shah: 9, usd: 0.63 }, usdPerShah: 0.07, settings: SETTINGS, jobs: [], personal: true, reserved: 2, presets: [],
            } },
            'POST /api/studio/editions/4/autotranslate/quote': { body: {
                kind: 'analyze', from: 1, to: 3, chapters: 3, skipped: 0, shah: 1, usd: 0.07, expectedUsd: 0.012, estimated: true, unanalyzed: 0,
                analyzeModel: SETTINGS.analyze, translateModel: SETTINGS.translate, proofreadModel: SETTINGS.proofread, reserveShah: 2,
                steps: ['analyze'],
            } },
        });
        expect(await screen.findByText(/У вас/)).toHaveTextContent('У вас 9 шагів, ще 2 у резерві запусків.');
        expect(screen.queryByRole('button', { name: /Обрати модель/ })).not.toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('По главу'), '3');
        expect(await screen.findByText(/≈ 1 шаг/, {}, { timeout: 2000 })).toBeInTheDocument();
        expect(screen.getByText(/заблокуємо 2 шаги, решту повернемо/)).toBeInTheDocument();
    });

    it('a person changes a step’s model and saves the set as their own (етап 19)', async () => {
        const preset = (id: number, quality: string, translate: string, chapterUsd: number) => ({
            id, name: `набір ${id}`, summary: `Опис ${id}.`, rating: 4, analyze: 'deepseek/deepseek-v4-pro', translate, proofread: 'deepseek/deepseek-v4-flash',
            analysisUsd: 0.01, chapterUsd, quality,
        });
        let saved: unknown[] = [];
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: { ...person('reader'), ai: true } },
            'GET /api/me/shahs': { body: MINE },
            'GET /api/studio/editions/4/autotranslate': () => ({ body: {
                configured: true, showShah: true, sourceChapters: 50, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 0, nextToAnalyze: 1,
                averageChars: 6000, balance: { shah: 9, usd: 0.63 }, usdPerShah: 0.07, settings: SETTINGS, jobs: [], personal: true, reserved: 2,
                presets: [preset(9, 'best', 'anthropic/claude-opus-5.5', 0.5), preset(8, 'high', 'anthropic/claude-sonnet-5.5', 0.2)],
                remembered: null, mySets: saved,
                peopleModels: ['anthropic/claude-opus-5.5', 'anthropic/claude-sonnet-5.5', 'deepseek/deepseek-v4-flash', 'deepseek/deepseek-v4-pro'],
            } }),
            'POST /api/studio/autotranslate/my-sets': (body) => {
                saved = [{ id: 31, name: 'Мій', basedOn: 8, analyze: 'deepseek/deepseek-v4-pro', translate: 'anthropic/claude-sonnet-5.5',
                    proofread: 'anthropic/claude-opus-5.5', chapterUsd: 0.35 }];
                return { status: 201, body: { id: 31, sent: body } };
            },
        });
        await screen.findByRole('group', { name: 'Якість' });
        await userEvent.click(screen.getByRole('button', { name: 'Змінити моделі етапів' }));
        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Модель: вичитка' }), 'anthropic/claude-opus-5.5');
        expect(screen.getByText(/Свій набір · змінено з «Висока»: модель вичитки/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти як мій набір' }));
        const dialog = await screen.findByRole('dialog', { name: 'Зберегти як мій набір' });
        await userEvent.type(within(dialog).getByLabelText('Назва набору'), 'Мій');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Зберегти' }));
        await waitFor(() => expect(calls.find((call) => call.path === '/api/studio/autotranslate/my-sets')?.body).toEqual({
            name: 'Мій', basedOn: 8,
            models: { analyze: 'deepseek/deepseek-v4-pro', translate: 'anthropic/claude-sonnet-5.5', proofread: 'anthropic/claude-opus-5.5', proofreadEnabled: true },
        }));
        expect(await within(screen.getByRole('group', { name: 'Якість' })).findByRole('button', { name: /Мій мій/ })).toHaveAttribute('aria-pressed', 'true');
    });

    it('a person picks a quality priced in шаги and remembers it for the next runs (етап 19)', async () => {
        const preset = (id: number, quality: string, translate: string, chapterUsd: number) => ({
            id, name: `набір ${id}`, summary: `Опис ${id}.`, rating: 4, analyze: translate, translate, proofread: null,
            analysisUsd: 0.01, chapterUsd, quality,
        });
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/studio/editions/4': { body: EDITION4 },
            'GET /api/me': { body: { ...person('reader'), ai: true } },
            'GET /api/me/shahs': { body: MINE },
            'GET /api/studio/editions/4/autotranslate': { body: {
                configured: true, showShah: true, sourceChapters: 50, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 0, nextToAnalyze: 1,
                averageChars: 6000, balance: { shah: 9, usd: 0.63 }, usdPerShah: 0.07, settings: SETTINGS, jobs: [], personal: true, reserved: 2,
                presets: [preset(9, 'best', 'anthropic/claude-opus-5.5', 0.5), preset(8, 'high', 'anthropic/claude-sonnet-5.5', 0.2),
                    preset(4, 'draft', 'x-ai/grok-4.3', 0.05)],
                remembered: null,
            } },
            'POST /api/studio/editions/4/autotranslate/quote': { body: {
                kind: 'translate', from: 1, to: 3, chapters: 3, skipped: 0, shah: 9, usd: 0.63, expectedUsd: 0.6, estimated: true, unanalyzed: 3,
                analyzeModel: SETTINGS.analyze, translateModel: SETTINGS.translate, proofreadModel: SETTINGS.proofread, reserveShah: 9,
                steps: ['analyze', 'translate', 'proofread'],
            } },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: { id: 1 } },
            'PUT /api/studio/autotranslate/choice': { status: 204 },
        });
        const qualities = await screen.findByRole('group', { name: 'Якість' });
        expect(within(qualities).getByRole('button', { name: /Висока/ })).toHaveAttribute('aria-pressed', 'true');
        expect(within(qualities).getByRole('button', { name: /Найкраща · ≈ 8 шагів за главу/ })).toHaveTextContent('Claude Opus 5.5');
        expect(within(qualities).getByRole('button', { name: /Швидка чернетка · ≈ 1 шаг за главу/ })).toBeInTheDocument();
        await userEvent.click(within(qualities).getByRole('button', { name: /Найкраща/ }));
        await userEvent.click(screen.getByRole('switch', { name: "Запам'ятати для наступних" }));
        await userEvent.type(screen.getByLabelText('По главу'), '3');
        // The price comes once typing stops; until then the button waits.
        const run = screen.getByRole('button', { name: /^Запустити/ });
        await waitFor(() => expect(run).toBeEnabled(), { timeout: 2000 });
        await userEvent.click(run);
        await waitFor(() => expect(calls.find((call) => call.path === '/api/studio/autotranslate/choice')?.body)
            .toEqual({ preset: 9, steps: ['analyze', 'translate', 'proofread'], publish: true }));
        expect(calls.find((call) => call.path === '/api/studio/editions/4/autotranslate/jobs')?.body).toMatchObject({ preset: 9 });
    });

    it('shows AI in «Нова публікація» only to people who see it', async () => {
        await renderAt('/studio/new', {
            'GET /api/me': { body: { ...person('reader'), ai: false } },
            'GET /api/me/shahs': { body: { ...MINE, available: 0, reserved: 0 } },
            'GET /api/me/teams': { body: [] },
        });
        expect(await screen.findByText('Свій переклад')).toBeInTheDocument();
        expect(screen.queryByText('Вставлю оригінал — перекладе ШІ')).not.toBeInTheDocument();
        expect(screen.queryByText('Автопереклад із сайту')).not.toBeInTheDocument();
    });

    it('offers «Перекласти з ШІ» to people with шаги, and the original’s site only on request (етап 19)', async () => {
        let asked = false;
        const { calls } = await renderAt('/studio/new', {
            'GET /api/me': () => ({ body: { ...person('reader'), ai: true, autotranslateRequested: asked } }),
            'GET /api/me/shahs': { body: { ...MINE, available: 5, reserved: 2 } },
            'GET /api/me/teams': { body: [] },
            'POST /api/autotranslate/request': () => { asked = true; return { status: 204 }; },
        });
        expect(await screen.findByText('Вставлю оригінал — перекладе ШІ')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /Автопереклад із сайту/ }));
        expect(screen.queryByLabelText('Посилання на новелу')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Попросити доступ' }));
        expect(await screen.findByText(/Запит надіслано/)).toBeInTheDocument();
        expect(calls.some((call) => call.method === 'POST' && call.path === '/api/autotranslate/request')).toBe(true);
    });
});
