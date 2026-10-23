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
    state: 'active', owner: 'mika', translation: null,
};
const TRANSLATIONS = [
    { editionId: 41, novel: 'mother-of-learning', team: 'mika', title: 'Мати навчання', teamName: 'mika' },
    { editionId: 42, novel: 'magi-craft-meister', team: 'kvitka', title: 'Майстер магічних ремесел', teamName: 'Квітка' },
];

afterEach(() => vi.unstubAllGlobals());

describe('«Налаштування → API»', () => {
    it('makes a token with the chosen rights and shows it once', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: { tokens: [], offered: OFFERED, days: DAYS, translations: [] } },
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
        // Without a translation of one's own there is nothing to limit it to.
        expect(screen.queryByRole('radio', { name: 'Лише один' })).not.toBeInTheDocument();

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

    it('limits a token to one translation and shows the limit in the list', async () => {
        const limited = { ...TOKEN, id: 7, name: 'Агент', scopes: ['studio'], translation: TRANSLATIONS[1] };
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: { tokens: [limited], offered: OFFERED, days: DAYS, translations: TRANSLATIONS } },
            'POST /api/me/api-tokens': { status: 201, body: { token: limited, secret: 'nvk_ONEtranslation' } },
        });
        const row = within(await screen.findByRole('list', { name: 'Мої токени' })).getByRole('listitem', { name: 'Агент' });
        expect(row).toHaveTextContent('Лише переклад «Майстер магічних ремесел» · Квітка');

        await userEvent.type(screen.getByLabelText(/Назва/), 'Агент');
        await userEvent.click(screen.getByRole('switch', { name: 'Моя бібліотека' }));
        await userEvent.click(screen.getByRole('radio', { name: 'Лише один' }));
        // The library is not about a translation: a limited token cannot have it.
        expect(screen.queryByRole('switch', { name: 'Моя бібліотека' })).not.toBeInTheDocument();
        await userEvent.selectOptions(screen.getByRole('combobox', { name: /^Переклад/ }), '42');
        await userEvent.click(screen.getByRole('switch', { name: 'Студія' }));
        await userEvent.click(screen.getByRole('button', { name: 'Створити токен' }));

        await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body)
            .toEqual({ name: 'Агент', scopes: ['read', 'studio'], days: 90, edition: 42 }));
        expect(await screen.findByLabelText('Новий токен')).toHaveTextContent('nvk_ONEtranslation');
    });

    it('lists tokens with their last use and revokes one', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: { tokens: [TOKEN], offered: OFFERED, days: DAYS, translations: [] } },
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
            'GET /api/me/api-tokens': { body: { tokens: [], offered: [...OFFERED, 'admin'], days: DAYS, translations: [] } },
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
