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

describe('sign-in with Google', () => {
    it('offers Google when the site has it, and says what went wrong on the way back', async () => {
        await renderAt('/login?next=%2Fn%2Fmah-vody&google_error=Google%20%D0%BD%D0%B5%20%D0%B2%D1%96%D0%B4%D0%BF%D0%BE%D0%B2%D1%96%D0%B2.', {
            'GET /api/auth/providers': { body: { google: true } },
        });
        expect(await screen.findByRole('link', { name: 'Увійти через Google' })).toHaveAttribute('href', '/api/auth/google?next=%2Fn%2Fmah-vody');
        expect(screen.getByText('Google не відповів.')).toBeInTheDocument();
    });

    it('hides Google while the site has no Google client', async () => {
        await renderAt('/register', { 'GET /api/auth/providers': { body: { google: false } } });
        expect(await screen.findByRole('heading', { name: 'Реєстрація' })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /через Google/ })).not.toBeInTheDocument();
    });

    it('lets a newcomer from Google pick a nick and goes where they were heading', async () => {
        const { calls, router } = await renderAt('/login/google', {
            'GET /api/auth/google/newcomer': { body: { email: 'marta@gmail.com', nick: 'marta', next: '/library' } },
            'POST /api/auth/google/newcomer': { body: { ...ME, nick: 'marta_k', google: true, hasPassword: false } },
        });
        const field = await screen.findByLabelText('Нік');
        expect(field).toHaveValue('marta');
        expect(screen.getByText('marta@gmail.com')).toBeInTheDocument();
        await userEvent.clear(field);
        await userEvent.type(field, 'marta_k');
        await userEvent.click(screen.getByRole('button', { name: 'Створити акаунт' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/library'));
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ nick: 'marta_k' });
    });

    it('asks a Google-only account to set a password before untying Google', async () => {
        const { calls } = await renderAt('/me/settings', {
            'GET /api/me': { body: { ...ME, google: true, hasPassword: false } },
            'GET /api/auth/providers': { body: { google: true } },
            'POST /api/auth/password-reset': { status: 202 },
        });
        expect(await screen.findByText(/Відв’язати можна, коли задасте пароль/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Відв’язати Google' })).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Поточний пароль')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Задати пароль через лист' }));
        expect(await screen.findByText(/Надіслали лист на mika@example.com/)).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/auth/password-reset')?.body).toEqual({ email: 'mika@example.com' });
    });

    it('ties Google from the settings', async () => {
        await renderAt('/me/settings?google=linked', {
            'GET /api/me': { body: { ...ME, google: true, hasPassword: true } },
            'GET /api/auth/providers': { body: { google: true } },
        });
        expect(await screen.findByText(/Google прив’язано/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Відв’язати Google' })).toBeInTheDocument();
    });

    it('offers to tie Google to an account without it', async () => {
        await renderAt('/me/settings', {
            'GET /api/me': { body: { ...ME, google: false, hasPassword: true } },
            'GET /api/auth/providers': { body: { google: true } },
        });
        expect(await screen.findByRole('link', { name: 'Прив’язати Google' })).toHaveAttribute('href', '/api/auth/google?next=%2Fme%2Fsettings&link=true');
    });
});

describe('Telegram in «Сповіщення»', () => {
    const STATUS = { available: true, linked: false, username: null, botUsername: 'novelka_bot', notifyInbox: true, notifyChapters: true, notifyMessages: true };

    it('hands out the one-time link and waits for «Start»', async () => {
        const { calls } = await renderAt('/me/settings/notifications', {
            'GET /api/me': { body: { ...ME, google: false, hasPassword: true } },
            'GET /api/me/telegram': { body: STATUS },
            'POST /api/me/telegram/link': { body: { url: 'https://t.me/novelka_bot?start=abc' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Прив’язати Telegram' }));
        expect(await screen.findByRole('link', { name: 'Відкрити Telegram' })).toHaveAttribute('href', 'https://t.me/novelka_bot?start=abc');
        expect(calls.filter((call) => call.path === '/api/me/telegram/link')).toHaveLength(1);
    });

    it('lets a tied chat choose what comes there', async () => {
        const { calls } = await renderAt('/me/settings/notifications', {
            'GET /api/me': { body: { ...ME, google: false, hasPassword: true } },
            'GET /api/me/telegram': { body: { ...STATUS, linked: true, username: 'mika_tg' } },
            'PATCH /api/me/telegram': { body: { ...STATUS, linked: true, username: 'mika_tg', notifyChapters: false } },
        });
        expect(await screen.findByText(/@mika_tg/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('switch', { name: 'Нові глави з підписок' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ notifyChapters: false }));
        expect(screen.getByRole('switch', { name: 'Нові глави з підписок' })).not.toBeChecked();
    });

    it('stays hidden while the site has no bot', async () => {
        const { calls } = await renderAt('/me/settings/notifications', {
            'GET /api/me': { body: { ...ME, google: false, hasPassword: true } },
            'GET /api/me/telegram': { body: { ...STATUS, available: false } },
        });
        expect(await screen.findByRole('heading', { name: 'Сповіщення' })).toBeInTheDocument();
        await waitFor(() => expect(calls.some((call) => call.path === '/api/me/telegram')).toBe(true));
        expect(screen.queryByRole('heading', { name: 'Telegram' })).not.toBeInTheDocument();
    });
});
