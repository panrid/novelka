import { expect, test, type Page } from '@playwright/test';
import { pageData } from './pageData';

const novel = { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [],
    firstChapter: 1, resumeChapter: null, rating: { score: 0, mine: 0 } };
const linaId = '11111111-2222-3333-4444-555555555555';

async function session(page: Page) {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'me', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/users/search?*', route => route.fulfill({ json: { items: [{ id: linaId, username: 'lina' }] } }));
}

test('comments show mentions and quotes as text, reply to a comment and suggest people after @', async ({ page }) => {
    await session(page);
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    const comment = { id: 7, author_id: 'other', author: 'anna', created_at: '2026-09-24T10:00:00Z', edited_at: null, rating: { score: 0, mine: 0 },
        can_edit: false, can_delete: false, body: `> старий уривок\nДякую, <@${linaId}>! <b>не html</b>` };
    const reply = { ...comment, id: 8, author: 'bohdan', body: 'Згоден', reply_to: 7, reply_author: 'anna', reply_body: 'Дякую за переклад' };
    let posted: unknown;
    await page.route('**/api/novels/n1', route => route.fulfill({ json: novel }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/novels/n1/comments**', route => {
        if (route.request().method() === 'POST') { posted = route.request().postDataJSON(); return route.fulfill({ json: { id: 9 } }); }
        return route.fulfill({ json: { items: [reply, comment], nextCursor: 0, names: { [linaId]: 'lina' } } });
    });
    await page.goto('/#/novels/n1');
    const section = page.getByRole('region', { name: 'Обговорення новели' });
    await expect(section.locator('.mention')).toHaveText('@lina');
    await expect(section.locator('#comment-7 blockquote')).toHaveText('старий уривок');
    await expect(section.locator('#comment-7 b')).toHaveCount(0);
    await expect(section.getByRole('button', { name: 'Перейти до повідомлення anna' })).toContainText('Дякую за переклад');

    await section.getByRole('button', { name: 'Цитувати anna' }).click();
    const field = section.getByLabel('Ваш коментар');
    await expect(field).toHaveValue('> anna:\n> > старий уривок\n> Дякую, lina! <b>не html</b>\n\n');
    await field.fill('');
    await section.getByRole('button', { name: 'Відповісти anna' }).click();
    await expect(section.getByText('Відповідь для anna')).toBeVisible();
    await field.pressSequentially('Привіт, @li');
    await page.getByRole('option', { name: '@lina' }).click();
    await expect(field).toHaveValue('Привіт, @lina ');
    await field.pressSequentially('як справи?');
    await section.getByRole('button', { name: 'Опублікувати коментар' }).click();
    await expect.poll(() => posted).toEqual({ chapter: 0, body: 'Привіт, @lina як справи?', replyTo: 7 });
});

test('mention notifications lead to the discussion or the chat', async ({ page }) => {
    await session(page);
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [
        { id: 2, kind: 'reply', actor: 'anna', chat_id: 5, comment_id: null, novel_id: null, novel_title: null, chapter: null, task_id: null,
            entry_count: null, created_at: '2026-09-24T10:00:00Z', read: false },
        { id: 1, kind: 'mention', actor: 'bohdan', comment_id: 3, chat_id: null, novel_id: 'n1', novel_title: 'Водяний маг', chapter: 2,
            task_id: null, entry_count: null, created_at: '2026-09-24T09:00:00Z', read: false },
    ], unread: 2, latestId: 2, nextCursor: 0 } }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/tags?*', route => route.fulfill({ json: { items: [] } }));
    await page.goto('/#/');
    await page.getByRole('button', { name: /Сповіщення/ }).first().click();
    await expect(page.getByRole('link', { name: /anna відповідає вам в чаті/ })).toHaveAttribute('href', '#/chat');
    await expect(page.getByRole('link', { name: /bohdan згадує вас в обговоренні/ })).toHaveAttribute('href', '#/novels/n1/chapters/2');
});
