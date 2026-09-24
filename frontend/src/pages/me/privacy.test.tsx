import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

afterEach(() => vi.unstubAllGlobals());

describe('privacy', () => {
    it('saves a switch as soon as it is flipped', async () => {
        const { calls } = await renderAt('/me/settings/privacy', {
            'GET /api/me': { body: ME },
            'PATCH /api/me': (body) => ({ body: { ...ME, ...(body as object) } }),
        });

        await userEvent.click(await screen.findByRole('switch', { name: 'Мені є 18 років' }));

        await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ adultConfirmed: true }));
        expect(screen.getByRole('switch', { name: 'Мені є 18 років' })).toBeChecked();
    });

    it('shows the shags switch to the site owner only', async () => {
        await renderAt('/me/settings/privacy', { 'GET /api/me': { body: ME } });

        await screen.findByRole('switch', { name: 'Мені є 18 років' });
        expect(screen.queryByRole('switch', { name: 'Показувати мені суми в шагах' })).not.toBeInTheDocument();
    });
});
