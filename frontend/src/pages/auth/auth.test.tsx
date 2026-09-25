import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

afterEach(() => vi.unstubAllGlobals());

describe('sign-in', () => {
    it('shows the server message for a wrong password', async () => {
        await renderAt('/login', {
            'POST /api/auth/login': { status: 401, body: { detail: 'Неправильний нік, пошта або пароль.' } },
        });

        await userEvent.type(screen.getByLabelText('Нік або пошта'), 'mika');
        await userEvent.type(screen.getByLabelText('Пароль'), 'не той пароль');
        await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Неправильний нік, пошта або пароль.');
    });

    it('offers to resend the letter when the email is not confirmed yet', async () => {
        const { calls } = await renderAt('/login', {
            'POST /api/auth/login': { status: 403, body: { detail: 'Спершу підтвердьте пошту.', reason: 'email-not-verified' } },
            'POST /api/auth/verify/resend': { status: 202 },
        });

        await userEvent.type(screen.getByLabelText('Нік або пошта'), 'mika@example.com');
        await userEvent.type(screen.getByLabelText('Пароль'), 'довгий пароль 42');
        await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Надіслати лист ще раз' }));

        expect(await screen.findByText(/Надіслали ще раз/)).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/auth/verify/resend')?.body).toEqual({ email: 'mika@example.com' });
    });

    it('returns to the page the guest came from', async () => {
        const { router } = await renderAt('/me/settings', { 'POST /api/auth/login': { body: ME } });

        expect(router.state.location.pathname).toBe('/login');
        await userEvent.type(screen.getByLabelText('Нік або пошта'), 'mika');
        await userEvent.type(screen.getByLabelText('Пароль'), 'довгий пароль 42');
        await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/me/settings'));
        expect(await screen.findByRole('heading', { name: 'Налаштування' })).toBeInTheDocument();
    });
});

describe('registration', () => {
    it('asks to check the mailbox after signing up', async () => {
        await renderAt('/register', { 'POST /api/auth/register': { status: 202 } });

        await userEvent.type(screen.getByLabelText('Нік'), 'mika');
        await userEvent.type(screen.getByLabelText('Пошта'), 'mika@example.com');
        await userEvent.type(screen.getByLabelText('Пароль'), 'довгий пароль 42');
        await userEvent.click(screen.getByRole('button', { name: 'Зареєструватися' }));

        expect(await screen.findByRole('heading', { name: 'Перевірте пошту' })).toBeInTheDocument();
        expect(screen.getByText('mika@example.com')).toBeInTheDocument();
    });
});

describe('links from letters', () => {
    it('spends a confirmation link once and welcomes the new member', async () => {
        const { router, calls } = await renderAt('/verify?token=abc', { 'POST /api/auth/verify': { body: ME } });

        await waitFor(() => expect(router.state.location.pathname).toBe('/welcome'));
        expect(await screen.findByRole('heading', { name: 'Вітаємо, mika!' })).toBeInTheDocument();
        expect(calls.filter((call) => call.path === '/api/auth/verify')).toHaveLength(1);
    });

    it('explains a used link', async () => {
        await renderAt('/verify?token=old', {
            'POST /api/auth/verify': { status: 400, body: { detail: 'Посилання вже не діє.' } },
        });

        expect(await screen.findByRole('alert')).toHaveTextContent('Посилання вже не діє.');
    });
});

describe('public profile', () => {
    it('moves an old nick to the current address', async () => {
        const { router } = await renderAt('/u/old_nick', {
            'GET /api/users/old_nick': { body: { nick: 'Мавка', avatarUrl: null, bio: 'Привіт', memberSince: '2026-03-01' } },
            'GET /api/users/Мавка': { body: { nick: 'Мавка', avatarUrl: null, bio: 'Привіт', memberSince: '2026-03-01' } },
        });

        await waitFor(() => expect(decodeURIComponent(router.state.location.pathname)).toBe('/u/Мавка'));
        expect(await screen.findByRole('heading', { name: 'Мавка' })).toBeInTheDocument();
        expect(screen.getByText('на Новелці з березня 2026')).toBeInTheDocument();
    });

    it('shows what the person reads and lets you block them', async () => {
        const card = {
            editionId: 3, novelSlug: 'mah-vody', teamHandle: 'panrid', teamName: 'panrid', title: 'Маг води', author: 'Автор', coverUrl: null,
            kind: 'machine', status: 'ongoing', adult: false, chapterCount: 2, tags: [], lastPublishedAt: null,
        };
        const { calls } = await renderAt('/u/Мавка', {
            'GET /api/me': { body: ME },
            'GET /api/users/Мавка': { body: { nick: 'Мавка', avatarUrl: null, bio: '', memberSince: '2026-03-01' } },
            'GET /api/users/Мавка/activity': { body: { reading: [card], acceptedSuggestions: 3 } },
            'GET /api/me/blocks': { body: [] },
            'PUT /api/me/blocks/Мавка': { status: 204 },
        });

        expect(await screen.findByRole('heading', { name: 'Читає зараз' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Маг води' })).toHaveAttribute('href', '/n/mah-vody?t=panrid');
        expect(screen.getByText('3 правки прийнято')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Заблокувати' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'PUT' && decodeURIComponent(call.path) === '/api/me/blocks/Мавка')).toBe(true));
    });
});
