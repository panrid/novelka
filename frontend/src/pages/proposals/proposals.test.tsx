import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const PROPOSAL = {
    id: 5, title: 'Доглядач маяка', author: 'Сакура Юкі', description: ['Перший абзац.'], chapters: 12, adult: false, site: 'Syosetu',
    proposedBy: 'oleh', createdAt: '2026-10-05T10:00:00Z', state: 'open', votes: 3, voted: false, mine: false, taken: null,
};
const page = (items: unknown[]) => ({ items, total: items.length, page: 1, hasMore: false });

afterEach(() => vi.unstubAllGlobals());

describe('what to translate', () => {
    it('lets a reader propose a novel and vote for others', async () => {
        let voted = false;
        const { calls } = await renderAt('/proposals', {
            'GET /api/me': { body: ME },
            'GET /api/proposals': () => ({ body: page([{ ...PROPOSAL, voted, votes: voted ? 4 : 3 }]) }),
            'POST /api/proposals': { status: 201, body: { id: 6, created: true } },
            'POST /api/proposals/5/vote': () => { voted = true; return { status: 200 }; },
        });

        expect(await screen.findByRole('heading', { name: 'Доглядач маяка' })).toBeInTheDocument();
        expect(screen.getByText(/Syosetu · 12 глав/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Голосувати за «Доглядач маяка»' }));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Забрати голос за «Доглядач маяка»' })).toHaveTextContent('4'));

        await userEvent.type(screen.getByLabelText('Посилання на новелу'), 'https://ncode.syosetu.com/n1234ab/');
        await userEvent.click(screen.getByRole('button', { name: 'Запропонувати' }));
        expect(await screen.findByText('Новелу додано, ваш голос уже за неї.')).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/proposals' && call.method === 'POST')?.body)
            .toEqual({ url: 'https://ncode.syosetu.com/n1234ab/' });
    });

    it('takes a proposal to translate with the chosen team', async () => {
        const { calls, router } = await renderAt('/proposals', {
            'GET /api/me': { body: ME },
            'GET /api/proposals': { body: page([PROPOSAL]) },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }, { handle: 'svitlo', name: 'Світло', role: 'translator' }] },
            'POST /api/proposals/5/take': { body: { editionId: 12, novelSlug: 'dohliadach-mayaka' } },
            'GET /api/studio/editions/12/autotranslate': { status: 404, body: { message: '—' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Беру перекладати' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.selectOptions(within(dialog).getByRole('combobox'), 'svitlo');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Беру перекладати' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12/translate'));
        expect(calls.find((call) => call.path === '/api/proposals/5/take')?.body).toEqual({ team: 'svitlo' });
    });

    it('shows guests the list and asks them to sign in to vote', async () => {
        await renderAt('/proposals', {
            'GET /api/me': { status: 204 },
            'GET /api/proposals': { body: page([PROPOSAL]) },
        });
        expect(await screen.findByRole('heading', { name: 'Доглядач маяка' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Голосувати за «Доглядач маяка»' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Беру перекладати' })).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Увійдіть' })).toBeInTheDocument();
    });
});
