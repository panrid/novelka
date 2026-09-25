import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const person = (role: string) => ({
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role, bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
});
const REPORT = {
    target: 'comment', targetId: 40, reports: 2, reasons: ['образи', 'спам'], firstAt: new Date().toISOString(),
    preview: { author: 'lysytsia', text: 'Грубий коментар', imageUrl: null, where: 'Маг води', slug: 'mah-vody', chapter: 3, team: 'panrid', hidden: false },
};

afterEach(() => vi.unstubAllGlobals());

describe('administration', () => {
    it('a moderator hides a reported comment with a reason', async () => {
        vi.spyOn(window, 'prompt').mockReturnValue('образи');
        const { calls } = await renderAt('/admin/moderation', {
            'GET /api/me': { body: person('moderator') },
            'GET /api/admin/reports': { body: [REPORT] },
            'POST /api/admin/reports/comment/40': { status: 200 },
        });
        const item = (await screen.findByText('Грубий коментар')).closest('article')!;
        expect(within(item).getByText(/Скарг: 2/)).toHaveTextContent('«образи», «спам»');
        expect(within(item).getByRole('link', { name: 'Маг води, глава 3' })).toHaveAttribute('href', '/n/mah-vody/3?t=panrid');
        await userEvent.click(within(item).getByRole('button', { name: 'Приховати' }));
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ action: 'hide', reason: 'образи' });
    });

    it('an administrator gives roles up to moderator, and the owner up to administrator', async () => {
        const people = [
            { nick: 'lysytsia', role: 'reader', email: null, createdAt: new Date().toISOString(), lastSeenAt: null },
            { nick: 'bohdan', role: 'admin', email: null, createdAt: new Date().toISOString(), lastSeenAt: null },
        ];
        const { calls } = await renderAt('/admin/users', {
            'GET /api/me': { body: person('admin') },
            'GET /api/admin/users': { body: people },
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
});
