import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

afterEach(() => vi.unstubAllGlobals());

describe('«Налаштування» in one place', () => {
    it('keeps privacy in «Профіль»: each switch saves as soon as it is flipped', async () => {
        const { calls } = await renderAt('/me/settings', {
            'GET /api/me': { body: ME },
            'GET /api/me/presence': { body: { showOnline: true } },
            'GET /api/me/blocks': { body: [] },
            'PUT /api/me/presence': (body) => ({ body }),
            'PATCH /api/me': (body) => ({ body: { ...ME, ...(body as object) } }),
        });
        expect(await screen.findByRole('heading', { name: 'Профіль' })).toBeInTheDocument();
        expect(screen.getByRole('navigation', { name: 'Розділи налаштувань' })).toHaveTextContent('Вхід і безпека');

        await userEvent.click(screen.getByRole('switch', { name: 'Показувати іншим, що я читаю' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ showReading: false }));
        await userEvent.click(await screen.findByRole('switch', { name: 'Показувати, що я на сайті' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ showOnline: false }));
        expect(screen.getByRole('switch', { name: 'Показувати, що я на сайті' })).not.toBeChecked();
    });

    it('asks about 18+ in «Читання», where the old «Приватність» address leads', async () => {
        const { calls, router } = await renderAt('/me/settings/privacy', {
            'GET /api/me': { body: ME },
            'PATCH /api/me': (body) => ({ body: { ...ME, ...(body as object) } }),
        });
        await waitFor(() => expect(router.state.location.pathname).toBe('/me/settings/reader'));
        await userEvent.click(await screen.findByRole('switch', { name: 'Мені є 18 років' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ adultConfirmed: true }));
        expect(screen.getByRole('switch', { name: 'Мені є 18 років' })).toBeChecked();
    });

    it('shows «Шаги» with the shags switch to the site owner only', async () => {
        const SHAHS = { available: 7, reserved: 2, usdPerShah: 0.05, running: [], history: [], hasMore: false, total: 0, page: 1 };
        await renderAt('/me/settings/shahs', { 'GET /api/me': { body: ME }, 'GET /api/me/shahs': { body: SHAHS } });
        expect(await screen.findByText('На рахунку')).toBeInTheDocument();
        expect(screen.getByText('7')).toBeInTheDocument();
        expect(screen.queryByRole('switch', { name: 'Показувати мені суми в шагах' })).not.toBeInTheDocument();
    });

    it('lets guests choose only the look', async () => {
        await renderAt('/me/settings/appearance');
        const sections = await screen.findByRole('navigation', { name: 'Розділи налаштувань' });
        expect(sections).toHaveTextContent('Вигляд сайту');
        expect(sections).toHaveTextContent('Читання');
        expect(sections).not.toHaveTextContent('Профіль');
    });
});
