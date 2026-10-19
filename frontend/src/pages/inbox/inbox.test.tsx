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
        const chapters = (await screen.findByText('Нові глави 11–13 · 3 глави')).closest('a')!;
        expect(within(chapters).getByText('Маг води')).toBeInTheDocument();
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
        expect(screen.getByRole('button', { name: /^Архів/ })).toHaveTextContent('Архів4');
        await userEvent.click(screen.getAllByRole('button', { name: 'В архів' })[0]!);
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/notifications/archive')?.body).toEqual({ ids: [7] }));

        await userEvent.click(screen.getByRole('button', { name: 'Вибрати' }));
        await userEvent.click(screen.getByText('lysytsia відповідає на ваш коментар'));
        expect(router.state.location.pathname).toBe('/inbox');
        await userEvent.click(screen.getByRole('button', { name: 'Видалити (1)' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Видалити' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/notifications/delete')?.body).toEqual({ ids: [6], archived: false }));

        await userEvent.click(screen.getByRole('button', { name: /^Архів/ }));
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/notifications' && call.query.includes('archived=true'))).toBe(true));
        expect(router.state.location.search).toEqual({ archive: 1 });
        expect(await screen.findByRole('heading', { name: 'Архів' })).toBeInTheDocument();
    });

    it('filters by kind and marks everything read', async () => {
        const { calls, router } = await renderAt('/inbox', {
            'GET /api/me': { body: ME },
            'GET /api/notifications': { body: { ...NOTIFICATIONS, groups: {
                all: { total: 3, unread: 2 }, chapters: { total: 1, unread: 1 }, edits: { total: 1, unread: 0 },
                replies: { total: 1, unread: 1 }, studio: { total: 0, unread: 0 }, system: { total: 0, unread: 0 },
            } } },
            'GET /api/notifications/unread': { body: { unread: 2 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'POST /api/notifications/read': { body: { unread: 0 } },
        });
        await screen.findByText('Нові глави 11–13 · 3 глави');
        const filters = screen.getByRole('navigation', { name: 'Що показувати' });
        expect(within(filters).getByRole('button', { name: /^Усі/ })).toHaveTextContent('2 нові');
        expect(within(filters).getByRole('button', { name: /^Правки/ })).toHaveTextContent('Правки1');
        expect(within(filters).getByRole('link', { name: 'Що й куди надсилати' })).toHaveAttribute('href', '/me/settings/notifications');

        await userEvent.click(within(filters).getByRole('button', { name: /^Правки/ }));
        await vi.waitFor(() => expect(router.state.location.search).toEqual({ group: 'edits' }));
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/notifications' && call.query.includes('group=edits'))).toBe(true));
        expect(within(filters).getByRole('button', { name: /^Правки/ })).toHaveAttribute('aria-pressed', 'true');

        await userEvent.click(screen.getByRole('button', { name: 'Усе прочитано' }));
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/notifications/read' && JSON.stringify(call.body) === '{"upTo":null}'))
            .toBe(true));
    });

    it('shows conversations beside the open one and finds them by name', async () => {
        await renderAt('/inbox/messages', {
            'GET /api/me': { body: ME },
            'GET /api/notifications/unread': { body: { unread: 0 } },
            'GET /api/conversations': { body: { unread: 2, items: [
                { id: 5, kind: 'direct', title: 'lysytsia', avatarUrl: null, teamHandle: null, otherNick: 'lysytsia',
                    lastText: 'Привіт!', lastAuthorNick: 'lysytsia', lastAt: new Date().toISOString(), unread: 2, muted: false },
                { id: 6, kind: 'team', title: 'Команда', avatarUrl: null, teamHandle: 'panrid', otherNick: null,
                    lastText: 'Опублікував.', lastAuthorNick: 'mavka', lastAt: new Date().toISOString(), unread: 0, muted: false },
            ] } },
        });
        const list = await screen.findByRole('complementary', { name: 'Розмови' });
        expect(await within(list).findByRole('link', { name: /lysytsia/ })).toHaveAttribute('href', '/inbox/messages/5');
        expect(within(list).getByText('Ви: Опублікував.')).toBeInTheDocument();
        expect(screen.getByText('Виберіть розмову ліворуч.')).toBeInTheDocument();
        const messages = screen.getByRole('link', { name: /^Повідомлення/ });
        expect(messages).toHaveAttribute('aria-current', 'page');
        await vi.waitFor(() => expect(messages).toHaveTextContent('Повідомлення · 2'));

        await userEvent.type(within(list).getByLabelText('Пошук розмов'), 'panr');
        expect(within(list).queryByRole('link', { name: /lysytsia/ })).not.toBeInTheDocument();
        expect(within(list).getByRole('link', { name: /\$panrid/ })).toBeInTheDocument();
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

    it('splits the chat into channels, shows who writes and who is here, and sends to the open channel', async () => {
        const CHANNELS = [
            { slug: 'general', name: 'загальний', about: 'Про все, що стосується читання й сайту', unread: 0 },
            { slug: 'translation', name: 'переклад-і-словники', about: 'Перекладачам: імена, терміни, як краще', unread: 3 },
        ];
        const { calls, router } = await renderAt('/inbox/chat', {
            'GET /api/me': { body: ME },
            'GET /api/notifications/unread': { body: { unread: 0 } },
            'GET /api/conversations': { body: { items: [], unread: 0 } },
            'GET /api/chat/channels': { body: CHANNELS },
            'GET /api/chat/online': { body: { count: 2, people: [{ nick: 'panrid', avatarUrl: null }, { nick: 'mavka', avatarUrl: null }] } },
            'GET /api/chat': { body: [{ id: 9, authorNick: 'panrid', authorAvatarUrl: null, authorRole: 'owner', body: '@mavka так, із глави 11.',
                createdAt: new Date().toISOString(), replyTo: null, replyExcerpt: null, mine: false, mentionsMe: true }] },
            'POST /api/chat/channels/general/read': { status: 204 },
            'POST /api/chat/channels/translation/read': { status: 204 },
            'POST /api/chat': { status: 201, body: { id: 10 } },
        });
        expect(await screen.findByRole('heading', { name: '# загальний' })).toBeInTheDocument();
        expect(await screen.findByText('власник сайту')).toBeInTheDocument();
        await vi.waitFor(() => expect(calls.find((call) => call.path === '/api/chat/channels/general/read')?.body).toEqual({ upTo: 9 }));
        const here = screen.getByRole('complementary', { name: 'Зараз на сайті' });
        expect(await within(here).findByText('Зараз на сайті · 2')).toBeInTheDocument();
        expect(within(here).getByRole('link', { name: /mavka/ })).toHaveAttribute('href', '/u/mavka');

        const channels = screen.getByRole('navigation', { name: 'Канали' });
        expect(await within(channels).findByRole('button', { name: /переклад-і-словники/ })).toHaveTextContent('3');
        await userEvent.click(within(channels).getByRole('button', { name: /переклад-і-словники/ }));
        await vi.waitFor(() => expect(router.state.location.search).toEqual({ channel: 'translation' }));
        expect(await screen.findByRole('heading', { name: '# переклад-і-словники' })).toBeInTheDocument();
        await vi.waitFor(() => expect(calls.some((call) => call.path === '/api/chat' && call.query.includes('channel=translation'))).toBe(true));

        await userEvent.type(screen.getByLabelText('Повідомлення в чат'), 'Підземелля');
        await userEvent.click(screen.getByRole('button', { name: 'Надіслати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.method === 'POST' && call.path === '/api/chat')?.body)
            .toEqual({ body: 'Підземелля', replyTo: null, channel: 'translation' }));
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
