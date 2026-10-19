import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const OFFERED = ['read', 'library', 'suggest', 'studio', 'translate'];
const DAYS = [7, 30, 90, 365];
const TOKEN = {
    id: 5, name: 'Читалка', hint: 'nvk_Ab3dE1…', scopes: ['read', 'library'], createdAt: '2026-10-01T10:00:00Z',
    expiresAt: '2026-12-30T10:00:00Z', lastUsedAt: '2026-10-18T09:30:00Z', lastUsedIp: '203.0.113.7', revokedAt: null,
    state: 'active', owner: 'mika',
};

afterEach(() => vi.unstubAllGlobals());

describe('«Налаштування → API»', () => {
    it('makes a token with the chosen rights and shows it once', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: { tokens: [], offered: OFFERED, days: DAYS } },
            'POST /api/me/api-tokens': (body) => ({
                status: 201,
                body: { token: { ...TOKEN, id: 9, name: (body as { name: string }).name, scopes: (body as { scopes: string[] }).scopes }, secret: 'nvk_SECRETvalue123' },
            }),
        });
        expect(await screen.findByRole('heading', { name: 'API' })).toBeInTheDocument();
        expect(screen.getByRole('navigation', { name: 'Розділи налаштувань' })).toHaveTextContent('API');
        expect(await screen.findByText('Токенів ще немає.')).toBeInTheDocument();
        // «Адміністрування» is not offered to a reader.
        expect(screen.queryByRole('switch', { name: 'Адміністрування' })).not.toBeInTheDocument();

        await userEvent.type(screen.getByLabelText(/Назва/), 'Агент перекладу');
        await userEvent.click(screen.getByRole('switch', { name: 'Студія' }));
        await userEvent.click(screen.getByRole('radio', { name: '30 днів' }));
        await userEvent.click(screen.getByRole('button', { name: 'Створити токен' }));

        await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body)
            .toEqual({ name: 'Агент перекладу', scopes: ['read', 'studio'], days: 30 }));
        expect(await screen.findByLabelText('Новий токен')).toHaveTextContent('nvk_SECRETvalue123');
        expect(screen.getByText(/більше його не покаже ніхто/)).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Готово, скопіював' }));
        expect(screen.queryByText('nvk_SECRETvalue123')).not.toBeInTheDocument();
    });

    it('lists tokens with their last use and revokes one', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: { tokens: [TOKEN], offered: OFFERED, days: DAYS } },
            'DELETE /api/me/api-tokens/5': { status: 204 },
        });
        const list = await screen.findByRole('list', { name: 'Мої токени' });
        const row = within(list).getByRole('listitem', { name: 'Читалка' });
        expect(row).toHaveTextContent('nvk_Ab3dE1…');
        expect(row).toHaveTextContent('Читання, Моя бібліотека');
        expect(row).toHaveTextContent('203.0.113.7');
        await userEvent.click(within(row).getByRole('button', { name: 'Відкликати' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'DELETE' && call.path === '/api/me/api-tokens/5')).toBe(true));
        expect(screen.queryByRole('heading', { name: 'Усі токени сайту' })).not.toBeInTheDocument();
    });

    it('lets the site owner see and revoke everyone’s tokens', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: { ...ME, role: 'owner' } },
            'GET /api/me/api-tokens': { body: { tokens: [], offered: [...OFFERED, 'admin'], days: DAYS } },
            'GET /api/admin/api-tokens': { body: [{ ...TOKEN, id: 12, name: 'Бот', owner: 'sora' }] },
            'DELETE /api/admin/api-tokens/12': { status: 204 },
        });
        expect(await screen.findByRole('switch', { name: 'Адміністрування' })).toBeInTheDocument();
        const all = await screen.findByRole('list', { name: 'Усі токени сайту' });
        const row = within(all).getByRole('listitem', { name: 'Бот' });
        expect(row).toHaveTextContent('sora');
        await userEvent.click(within(row).getByRole('button', { name: 'Відкликати' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'DELETE' && call.path === '/api/admin/api-tokens/12')).toBe(true));
    });
});
