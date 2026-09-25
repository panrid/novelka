import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: true, showShah: true,
};
const OVERVIEW = {
    configured: true, showShah: true, sourceChapters: 5, nextNumber: 1, publishedChapters: 0, lastAnalyzed: 0, nextToAnalyze: 1,
    balance: { shah: 208, usd: 7.5 }, usdPerShah: 0.036,
    quote: { kind: 'translate', from: 1, to: 3, chapters: 3, shah: 3, usd: 0.11, estimated: true, unanalyzed: 3 },
    jobs: [],
};
const FAILED_JOB = {
    id: 9, kind: 'translate', state: 'failed', from: 1, to: 3, done: 1, quoteShah: 3, spentUsd: 0.012, spentShah: 1,
    current: { number: 2, stage: 'translate', state: 'failed', error: 'Відповідь моделі загубилася дорогою.' },
    error: 'Відповідь моделі загубилася дорогою. Перевірте баланс і натисніть «Продовжити».', createdAt: '2026-09-25T08:00:00Z', finishedAt: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('autotranslate', () => {
    it('shows the price in шаги for the chapters asked and starts the job', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: OVERVIEW },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });

        expect(await screen.findByText(/Баланс:/)).toHaveTextContent('208 шагів');
        await userEvent.click(screen.getByRole('radio', { name: 'Переклад' }));
        const start = screen.getByRole('button', { name: 'Почати переклад' });
        expect(start).toBeDisabled();

        await userEvent.type(screen.getByLabelText('Перекласти з глави 1 до глави…'), '3');
        expect(await screen.findByText(/3 глави · орієнтовно/)).toHaveTextContent('3 шаги');
        expect(screen.getByText(/словник для них складеться під час перекладу/)).toBeInTheDocument();
        await userEvent.click(start);
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ to: 3, kind: 'translate' });
    });

    it('starts with analysis alone, at a quarter of the price', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, quote: { ...OVERVIEW.quote, kind: 'analyze', shah: 1, usd: 0.04, unanalyzed: 0 } } },
            'POST /api/studio/editions/4/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.type(await screen.findByLabelText('Аналізувати з глави 1 до глави…'), '3');
        expect(await screen.findByText(/Аналіз — чверть шагу/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Почати аналіз' }));
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ to: 3, kind: 'analyze' });
    });

    it('speaks dollars when the owner switched шаги off', async () => {
        await renderAt('/studio/4/translate', {
            'GET /api/me': { body: { ...OWNER, showShah: false } },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, showShah: false } },
        });
        expect(await screen.findByText(/Баланс:/)).toHaveTextContent('$7,50');
        await userEvent.click(screen.getByRole('radio', { name: 'Переклад' }));
        await userEvent.type(screen.getByLabelText('Перекласти з глави 1 до глави…'), '3');
        expect(await screen.findByText(/3 глави · орієнтовно/)).toHaveTextContent('$0,11');
    });

    it('explains a stopped job and lets the owner continue it', async () => {
        const { calls } = await renderAt('/studio/4/translate', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/4/autotranslate': { body: { ...OVERVIEW, quote: null, jobs: [FAILED_JOB] } },
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
