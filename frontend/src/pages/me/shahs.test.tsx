import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const person = (role: string) => ({
    id: 2, nick: role === 'owner' ? 'panrid' : 'lysytsia', email: null, emailVerified: true, role, bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
});
const MINE = {
    available: 9, reserved: 2, usdPerShah: 0.07, hasMore: false,
    running: [{ amount: 2, what: 'Автопереклад «Маг води», глави 3–5', createdAt: new Date().toISOString() }],
    history: [
        { kind: 'charge', amount: 1, what: 'Ілюстрація', createdAt: new Date().toISOString() },
        { kind: 'grant', amount: 12, what: 'на пробу', createdAt: new Date().toISOString() },
    ],
};
const stage = (model: string) => ({ model, inputPerMillion: 0.4, outputPerMillion: 1.6, enabled: true });
const SETTINGS = { analyze: stage('openai/gpt-4.1-mini'), translate: stage('openai/gpt-4.1-mini'), proofread: stage('openai/gpt-4.1-mini'),
    segmentChars: 4000, microUsdPerShah: 70000, capFactor: 1 };

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
            'GET /api/admin/users': { body: [{ nick: 'lysytsia', role: 'reader', email: 'l@example.com', createdAt: new Date().toISOString(), lastSeenAt: null }] },
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
            'GET /api/me': { body: person('reader') },
            'GET /api/me/shahs': { body: MINE },
            'GET /api/studio/editions/4/autotranslate': { body: {
                configured: true, showShah: true, sourceChapters: 50, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 0, nextToAnalyze: 1,
                averageChars: 6000, balance: { shah: 9, usd: 0.63 }, usdPerShah: 0.07, settings: SETTINGS, jobs: [], personal: true, reserved: 2,
            } },
            'POST /api/studio/editions/4/autotranslate/quote': { body: {
                kind: 'analyze', from: 1, to: 3, chapters: 3, skipped: 0, shah: 1, usd: 0.07, expectedUsd: 0.012, estimated: true, unanalyzed: 0,
                analyzeModel: SETTINGS.analyze, translateModel: SETTINGS.translate, proofreadModel: SETTINGS.proofread, reserveShah: 2,
            } },
        });
        expect(await screen.findByText(/У вас/)).toHaveTextContent('У вас 9 шагів, ще 2 у резерві запусків.');
        await userEvent.click(screen.getByRole('button', { name: /Розширені налаштування/ }));
        expect(screen.queryByRole('combobox', { name: 'Модель аналізу' })).not.toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('Аналізувати з глави 1 до глави…'), '3');
        expect(await screen.findByText(/≈ 1 шаг/, {}, { timeout: 2000 })).toBeInTheDocument();
        expect(screen.getByText(/заблокуємо 2 шаги, решту повернемо/)).toBeInTheDocument();
    });

    it('opens autotranslation in «Нова публікація» only with шаги', async () => {
        await renderAt('/studio/new', {
            'GET /api/me': { body: person('reader') },
            'GET /api/me/shahs': { body: { ...MINE, available: 0, reserved: 0 } },
            'GET /api/me/teams': { body: [] },
        });
        expect(await screen.findByText('Запускається за шаги. Їх нараховує власник сайту.')).toBeInTheDocument();
    });
});

describe('signing out', () => {
    it('forgets where this browser was reading, so the next guest is not offered it', async () => {
        localStorage.setItem('novelka:progress:mah-vody:panrid', JSON.stringify({ number: 5, position: 0.4, label: '4' }));
        localStorage.setItem('novelka:theme', 'dark');
        const { calls } = await renderAt('/me', {
            'GET /api/me': { body: person('reader') },
            'GET /api/me/shahs': { body: { ...MINE, available: 0, reserved: 0, running: [], history: [] } },
            'POST /api/auth/logout': { status: 204 },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Вийти' }));

        await waitFor(() => expect(calls.some((call) => call.path === '/api/auth/logout')).toBe(true));
        await waitFor(() => expect(localStorage.getItem('novelka:progress:mah-vody:panrid')).toBeNull());
        expect(localStorage.getItem('novelka:theme')).toBe('dark');
    });
});
