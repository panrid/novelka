import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const person = (role: string) => ({
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role, bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
});
const REPORT = {
    target: 'comment', targetId: 40, reports: 2, reasons: ['образи', 'спам'], firstAt: new Date().toISOString(),
    preview: { author: 'lysytsia', text: 'Грубий **коментар**', imageUrl: null, where: 'Маг води, глава 3', slug: 'mah-vody', chapter: 3, team: 'panrid', hidden: false },
    entries: [
        { nick: 'mika', at: new Date().toISOString(), reason: 'образи', chapter: null, chapterLabel: null },
        { nick: 'oleh', at: new Date().toISOString(), reason: 'спам', chapter: null, chapterLabel: null },
    ],
};

afterEach(() => vi.unstubAllGlobals());

describe('administration', () => {
    it('a moderator hides a reported comment with a reason', async () => {
        const { calls } = await renderAt('/admin/moderation', {
            'GET /api/me': { body: person('moderator') },
            'GET /api/admin/reports': { body: [REPORT] },
            'POST /api/admin/reports/comment/40': { status: 200 },
        });
        const bold = await screen.findByText('коментар');
        expect(bold.tagName).toMatch(/^(B|STRONG)$/);
        const item = bold.closest('article')!;
        const reports = within(item).getByRole('list', { name: 'Скарги' });
        expect(within(reports).getAllByRole('listitem').map((row) => row.textContent)).toEqual([
            expect.stringContaining('mika'), expect.stringContaining('oleh'),
        ]);
        expect(within(reports).getByText('«образи»')).toBeInTheDocument();
        expect(within(item).getByRole('link', { name: 'Відкрити' })).toHaveAttribute('href', '/n/mah-vody/3?t=panrid');
        expect(within(item).getByRole('link', { name: 'Маг води, глава 3' })).toHaveAttribute('href', '/n/mah-vody/3?t=panrid');
        await userEvent.click(within(item).getByRole('button', { name: 'Приховати' }));
        const dialog = await screen.findByRole('dialog', { name: 'Приховати' });
        await userEvent.type(within(dialog).getByLabelText('Причина'), 'образи');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Приховати' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ action: 'hide', reason: 'образи' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('shows who reported which chapter of a translation; only administrators may hide it', async () => {
        await renderAt('/admin/moderation', {
            'GET /api/me': { body: person('moderator') },
            'GET /api/admin/reports': { body: [{
                target: 'edition', targetId: 7, reports: 1, reasons: ['чужий переклад'], firstAt: new Date().toISOString(),
                preview: { author: 'panrid', text: 'Маг води', imageUrl: null, where: 'переклад', slug: 'mah-vody', chapter: null, team: 'panrid', hidden: false },
                entries: [{ nick: 'mika', at: new Date().toISOString(), reason: 'чужий переклад', chapter: 12, chapterLabel: '12' }],
            }] },
        });
        expect(await screen.findByRole('link', { name: 'глава 12' })).toHaveAttribute('href', '/n/mah-vody/12?t=panrid');
        expect(screen.getByText('«чужий переклад»')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Приховати' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Відхилити скаргу' })).toBeInTheDocument();
    });

    it('counts open reports next to «Адміністрування»', async () => {
        await renderAt('/me', {
            'GET /api/me': { body: person('moderator') },
            'GET /api/admin/overview': { body: { openReports: 3, activeJobs: 0, failedJobs: 0, role: 'moderator' } },
        });
        expect(await screen.findByRole('link', { name: 'Адміністрування · скарг: 3' })).toHaveAttribute('href', '/admin');
    });

    it('an administrator gives roles up to moderator, and the owner up to administrator', async () => {
        const people = [
            { nick: 'lysytsia', role: 'reader', email: null, createdAt: new Date().toISOString(), lastSeenAt: null },
            { nick: 'bohdan', role: 'admin', email: null, createdAt: new Date().toISOString(), lastSeenAt: null },
        ];
        const { calls } = await renderAt('/admin/users', {
            'GET /api/me': { body: person('admin') },
            'GET /api/admin/users': { body: { items: people, total: 2, page: 1, hasMore: false } },
            'PUT /api/admin/users/lysytsia/role': { status: 200 },
        });
        const select = await screen.findByRole('combobox', { name: 'Роль lysytsia' });
        expect(within(select).queryByRole('option', { name: 'Адміністратор' })).not.toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Роль bohdan' })).not.toBeInTheDocument();
        await userEvent.selectOptions(select, 'moderator');
        expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ role: 'moderator' });
    });

    it('the owner closes registration', async () => {
        const { calls } = await renderAt('/admin/settings', {
            'GET /api/me': { body: person('owner') },
            'GET /api/admin/settings': { body: { relayInactiveMonths: 3, registrationOpen: true, adultEnabled: true } },
            'PUT /api/admin/settings': (body) => ({ body }),
        });
        await userEvent.click(await screen.findByRole('switch', { name: 'Реєстрація відкрита' }));
        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        await vi.waitFor(() => expect(calls.find((call) => call.method === 'PUT')?.body)
            .toEqual({ relayInactiveMonths: 3, registrationOpen: false, adultEnabled: true }));
        expect(await screen.findByText('Збережено.')).toBeInTheDocument();
    });

    it('the journal says in words what was done', async () => {
        const at = new Date().toISOString();
        await renderAt('/admin/audit', {
            'GET /api/me': { body: person('owner') },
            'GET /api/admin/audit': { body: { total: 3, page: 1, hasMore: false, items: [
                { id: 3, actor: 'panrid', action: 'shahs_granted', targetType: 'account', targetId: 7, details: { shah: 10, nick: 'lysytsia', note: 'на пробу' }, createdAt: at },
                { id: 2, actor: 'panrid', action: 'autotranslate_settings', targetType: 'site', targetId: null, createdAt: at,
                    details: { before: { translate: { model: 'openai/gpt-4.1-mini' } }, after: { translate: { model: 'anthropic/claude-sonnet-5' } } } },
                { id: 1, actor: 'panrid', action: 'shah_price', targetType: 'site', targetId: null, details: { microUsdPerShah: 70000 }, createdAt: at },
            ] } },
        });
        expect(await screen.findByText(/нараховує 10 шагів lysytsia · «на пробу»/)).toBeInTheDocument();
        expect(screen.getByText(/змінює моделі й ціни автоперекладу · переклад: openai\/gpt-4.1-mini → anthropic\/claude-sonnet-5/)).toBeInTheDocument();
        expect(screen.getByText(/змінює ціну шагу для людей: \$0,07/)).toBeInTheDocument();
    });
});

describe('analytics', () => {
    const ROW = { model: 'openai/gpt-4.1-mini', stage: 'translate', calls: 10, failed: 1, tokensIn: 5000, tokensOut: 4000, usd: 0.04,
        secondsAverage: 20, secondsP90: 40, usdPerMillionTokens: 0.8, outputPerInput: 0.8 };
    const TRANSLATION = (translate: string, paragraphs: number) => ({
        translate, proofread: 'openai/gpt-4.1-mini', chapters: 10, editedChapters: 3, editedShare: 0.3, editorRevisions: 1, suggestions: 6,
        acceptedSuggestions: 4, rejectedSuggestions: 1, suggestionsPerChapter: 0.6, acceptedPerChapter: 0.4, paragraphsChanged: paragraphs,
        wordsChanged: 0.02, retriesPerChapter: 0.1, splitsPerChapter: 0, missingPerChapter: 0, failedSteps: 0, usdPerChapter: 0.05,
        secondsPerChapter: 60 });
    const REPORT_DATA = {
        period: { days: 30, from: '2026-09-06', bucket: 'day' },
        spend: { usd: 7.5, previousUsd: 5, calls: 300, failedCalls: 2, uncertainCalls: 0, tokensIn: 1_500_000, tokensOut: 900_000,
            usdPerCall: 0.025, estimateRatio: 0.8, chapters: 60, usdPerChapter: 0.12 },
        spendSeries: [{ start: '2026-10-05', usdByStage: { translate: 1, proofread: 0.5 }, calls: 20 }],
        stages: [{ stage: 'translate', usd: 5, calls: 200, share: 0.66, tokensIn: 1, tokensOut: 1 }],
        models: [ROW], combos: [], novels: [],
        funding: { siteUsd: 7, peopleUsd: 0.5, outsideRunsUsd: 0.1, chargedShah: 10, chargedUsd: 0.5 },
        translation: [TRANSLATION('anthropic/claude-opus-5', 0.04), TRANSLATION('openai/gpt-4.1-mini', 0.21)],
        proofread: [], analysis: [], reasons: [],
        site: { newAccounts: 3, activeAccounts: 9, readers: 7, chaptersPublished: 60, machineChapters: 58, comments: 4, suggestions: 6,
            acceptedSuggestions: 4, rejectedSuggestions: 1, libraryAdds: 5, series: [], top: [] },
    };

    it('shows the owner the money by step and how often people correct each model', async () => {
        const { calls } = await renderAt('/admin/analytics', {
            'GET /api/me': { body: person('owner') },
            'GET /api/admin/analytics': { body: REPORT_DATA },
        });
        expect(await screen.findByText('$7,50')).toBeInTheDocument();
        expect(screen.getByText('+50% до попереднього періоду')).toBeInTheDocument();
        expect(screen.getByRole('img', { name: 'Витрати за кроками' })).toBeInTheDocument();
        expect(screen.getAllByText('claude-opus-5 + gpt-4.1-mini').length).toBeGreaterThan(0);
        expect(screen.getAllByText('21%').length).toBeGreaterThan(0);

        await userEvent.click(screen.getByRole('radio', { name: '7 днів' }));
        await waitFor(() => expect(calls.some((call) => call.path === '/api/admin/analytics' && call.query === '?days=7')).toBe(true));
    });
});
