import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const NOTIFICATIONS = {
    unread: 2, hasMore: false, total: 3, page: 1, newest: 7,
    items: [
        { id: 7, kind: 'new_chapters', read: false, createdAt: new Date().toISOString(),
            payload: { slug: 'mah-vody', novelTitle: 'Маг води', teamHandle: 'panrid', first: 12, last: 14, firstLabel: '11', lastLabel: '13' } },
        { id: 6, kind: 'reply', read: false, createdAt: new Date().toISOString(),
            payload: { slug: 'mah-vody', novelTitle: 'Маг води', teamHandle: 'panrid', chapterNumber: 3, chapterLabel: '2.5',
                actorNick: 'lysytsia', excerpt: 'Бо так в оригіналі', commentId: 40, where: 'comment' } },
        { id: 5, kind: 'suggestions_submitted', read: true, createdAt: new Date().toISOString(),
            payload: { editionId: 9, slug: 'mah-vody', novelTitle: 'Маг води', teamHandle: 'panrid', actorNick: 'lysytsia', count: 3 } },
    ],
};
const CONVERSATION = {
    id: 5, kind: 'direct', title: 'lysytsia', avatarUrl: null, teamHandle: null, admin: false, muted: false,
    members: [{ nick: 'mavka', avatarUrl: null, role: 'member' }, { nick: 'lysytsia', avatarUrl: null, role: 'member' }],
    hasOlder: false, canWrite: true, cannotWrite: null,
    lines: [{ id: 30, kind: 'text', authorNick: 'lysytsia', authorAvatarUrl: null, body: 'Привіт, **@mavka**! ||кінець||',
        pictures: [], replyTo: null, replyExcerpt: null, createdAt: new Date().toISOString(), editedAt: null, deleted: false, mine: false }],
};

afterEach(() => vi.unstubAllGlobals());

describe('inbox', () => {
    it('lists what happened and marks it seen', async () => {
        const { calls } = await renderAt('/inbox', {
            'GET /api/me': { body: ME },
            'GET /api/notifications': { body: NOTIFICATIONS },
            'GET /api/notifications/unread': { body: { unread: 2 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'POST /api/notifications/read': { body: { unread: 0 } },
        });
        expect(await screen.findByText('Нові глави 11–13 · 3 глави')).toBeInTheDocument();
        expect(screen.getByText('Маг води')).toBeInTheDocument();
        const reply = screen.getByText('lysytsia відповідає на ваш коментар').closest('a')!;
        expect(reply).toHaveAttribute('href', '/n/mah-vody/3?t=panrid&look=true#c40');
        expect(within(reply).getByText(/глава 2\.5/)).toBeInTheDocument();
        expect(screen.getByText('3 нові правки').closest('a')).toHaveAttribute('href', '/studio/9');
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/notifications/read')?.body).toEqual({ upTo: 7 }));
    });

    it('pages notifications 20 at a time and keeps the page in the address', async () => {
        const { calls, router } = await renderAt('/inbox?page=2', {
            'GET /api/me': { body: ME },
            'GET /api/notifications': { body: { ...NOTIFICATIONS, total: 45, page: 2 } },
            'GET /api/notifications/unread': { body: { unread: 2 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'POST /api/notifications/read': { body: { unread: 0 } },
        });
        expect(await screen.findByRole('button', { name: /^Сторінка 2/, current: 'page' })).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/notifications')?.query).toContain('page=2');
        await userEvent.click(screen.getByRole('button', { name: 'Наступна сторінка' }));
        await vi.waitFor(() => expect(router.state.location.search).toEqual({ page: 3 }));
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/notifications' && call.query.includes('page=3'))).toBe(true));
    });

    it('puts notifications away, deletes the chosen ones and shows the archive', async () => {
        const { calls, router } = await renderAt('/inbox', {
            'GET /api/me': { body: ME },
            'GET /api/notifications': { body: { ...NOTIFICATIONS, archived: 4 } },
            'GET /api/notifications/unread': { body: { unread: 2 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'POST /api/notifications/read': { body: { unread: 0 } },
            'POST /api/notifications/archive': { body: { unread: 1 } },
            'POST /api/notifications/delete': { body: { unread: 1 } },
        });
        expect(await screen.findByText('Нові глави 11–13 · 3 глави')).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: /^Архів/ })).toHaveTextContent('Архів4');
        await userEvent.click(screen.getAllByRole('button', { name: 'В архів' })[0]!);
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/notifications/archive')?.body).toEqual({ ids: [7] }));

        await userEvent.click(screen.getByRole('button', { name: 'Вибрати' }));
        await userEvent.click(screen.getByText('lysytsia відповідає на ваш коментар'));
        expect(router.state.location.pathname).toBe('/inbox');
        await userEvent.click(screen.getByRole('button', { name: 'Видалити (1)' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Видалити' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/notifications/delete')?.body).toEqual({ ids: [6], archived: false }));

        await userEvent.click(screen.getByRole('tab', { name: /^Архів/ }));
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/notifications' && call.query.includes('archived=true'))).toBe(true));
        expect(router.state.location.search).toEqual({ archive: 1 });
    });

    it('shows a conversation with markup and sends a message', async () => {
        const { calls } = await renderAt('/inbox/messages/5', {
            'GET /api/me': { body: ME },
            'GET /api/conversations/5': { body: CONVERSATION },
            'GET /api/notifications/unread': { body: { unread: 0 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'POST /api/conversations/5/read': { status: 200 },
            'POST /api/conversations/5/messages': { status: 201, body: { id: 31 } },
        });
        expect(await screen.findByRole('link', { name: '@mavka' })).toHaveAttribute('href', '/u/mavka');
        expect(screen.getByRole('button', { name: /Спойлер/ })).toBeInTheDocument();
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/conversations/5/read')?.body).toEqual({ upTo: 30 }));

        await userEvent.click(screen.getByRole('button', { name: 'Відповісти' }));
        await userEvent.type(screen.getByLabelText('Повідомлення'), 'Привіт!');
        await userEvent.click(screen.getByRole('button', { name: 'Надіслати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/conversations/5/messages')?.body)
            .toEqual({ body: 'Привіт!', replyTo: 30, imageIds: [] }));
        expect(screen.getByLabelText('Повідомлення')).toHaveValue('');
    });

    it('lets guests read the chat and asks them to sign in to write', async () => {
        await renderAt('/inbox/chat', {
            'GET /api/chat': { body: [{ id: 1, authorNick: 'lysytsia', authorAvatarUrl: null, body: 'Всім привіт', createdAt: new Date().toISOString(),
                replyTo: null, replyExcerpt: null, mine: false }] },
        });
        expect(await screen.findByText('Всім привіт')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Увійдіть' })).toBeInTheDocument();
        expect(screen.queryByLabelText('Повідомлення в чат')).not.toBeInTheDocument();
    });
});
