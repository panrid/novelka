import { expect, test, type Page } from '@playwright/test';
import { pageData } from './pageData';

const novel = { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [],
    firstChapter: 1, resumeChapter: null, rating: { score: 0, mine: 0 } };

async function session(page: Page, role: string) {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'me', role }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
}

test('moderator hides a comment with a reason; readers can still reveal it', async ({ page }) => {
    await session(page, 'MODERATOR');
    const item = { id: 5, author_id: 'other', author: 'spoiler-fan', body: 'У кінці всі виживуть', created_at: '2026-09-24T10:00:00Z',
        edited_at: null, rating: { score: 0, mine: 0 }, can_edit: false, can_delete: false, can_moderate: true, hidden: false, hidden_reason: '' };
    let hideRequest: unknown;
    await page.route('**/api/novels/n1', route => route.fulfill({ json: novel }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/novels/n1/comments**', route => route.fulfill({ json: { items: [item], nextCursor: 0 } }));
    await page.route('**/api/comments/5/hide', route => {
        hideRequest = route.request().postDataJSON();
        Object.assign(item, { hidden: true, hidden_reason: hideRequest && (hideRequest as { reason: string }).reason });
        return route.fulfill({ json: { message: 'ok' } });
    });
    await page.route('**/api/comments/5/unhide', route => { Object.assign(item, { hidden: false, hidden_reason: '' }); return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/novels/n1');
    const section = page.getByRole('region', { name: 'Обговорення новели' });
    await expect(section.getByRole('button', { name: 'Видалити' })).toHaveCount(0);
    await section.getByRole('button', { name: 'Приховати повідомлення spoiler-fan' }).click();
    await section.getByLabel('Причина (побачать усі)').fill('Спойлер');
    await section.getByRole('button', { name: 'Приховати', exact: true }).click();
    await expect.poll(() => hideRequest).toEqual({ reason: 'Спойлер' });
    await expect(section.getByText('Приховано модератором: Спойлер')).toBeVisible();
    await expect(section.getByText('У кінці всі виживуть')).toHaveCount(0);
    await section.getByRole('button', { name: 'Показати' }).click();
    await expect(section.getByText('У кінці всі виживуть')).toBeVisible();
    await section.getByRole('button', { name: 'Повернути повідомлення spoiler-fan' }).click();
    await expect(section.getByText('Приховано модератором')).toHaveCount(0);
});

test('chat replaces a message hidden by a moderator while the page is open', async ({ page }) => {
    await session(page, 'READER');
    await page.clock.install();
    const message = { id: 40, author_id: 'other', author: 'anna', body: 'Купуйте ключі дешево', created_at: '2026-09-24T10:00:00Z',
        can_delete: false, can_moderate: false, hidden: false, hidden_reason: '' };
    let moderated: typeof message[] = [];
    await page.route('**/api/chat', route => route.fulfill({ json: { items: [message], nextCursor: 0 } }));
    await page.route('**/api/chat/updates?*', route => route.fulfill({ json: { items: [], deleted: [], moderated } }));
    await page.goto('/#/chat');
    await expect(page.getByText('Купуйте ключі дешево')).toBeVisible();
    await expect(page.getByRole('button', { name: /Приховати повідомлення/ })).toHaveCount(0);
    moderated = [{ ...message, hidden: true, hidden_reason: 'Реклама' }];
    await page.clock.runFor(4500);
    await expect(page.getByText('Приховано модератором: Реклама')).toBeVisible();
    await expect(page.getByText('Купуйте ключі дешево')).toHaveCount(0);
    await page.getByRole('button', { name: 'Показати' }).click();
    await expect(page.getByText('Купуйте ключі дешево')).toBeVisible();
});

test('translator sees why their novel is hidden; an administrator restores it in the workshop', async ({ page }) => {
    await session(page, 'ADMIN');
    let hidden = true;
    await page.route('**/api/novels/n1', route => route.fulfill({ json: { ...novel, hidden, hiddenReason: 'Порушення правил' } }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/novels/n1/comments**', route => route.fulfill({ json: { items: [], nextCursor: 0 } }));
    await page.goto('/#/novels/n1');
    await expect(page.getByRole('status').filter({ hasText: 'Новелу приховано адміністратором.' })).toContainText('Причина: Порушення правил.');

    await page.route('**/api/manage/novels?*', route => route.fulfill({ json: { items: [{ id: 'n1', title: 'Водяний маг', hidden }] } }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/balance', route => route.fulfill({ json: { available: 0, toppedUp: 0, reserved: 0, spent: 0, unlimited: false } }));
    await page.route('**/api/manage/n1', route => route.fulfill({ json: {
        novel: { id: 'n1', title: '物語', titleUk: 'Водяний маг', author: 'Автор', authorUk: null, descriptionUk: null, chapterCount: 10, url: 'x' },
        aliases: [], importedChapters: 3, glossary: { revision: 1, entries: [] }, proposals: [], tags: [], aiTranslated: false,
        hidden, hiddenReason: hidden ? 'Порушення правил' : '',
    } }));
    await page.route('**/api/manage/n1/unhide', route => { hidden = false; return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/manage?novel=n1');
    await expect(page.getByRole('combobox', { name: 'Новела' })).toHaveText(/приховано/);
    await page.getByRole('navigation', { name: 'Керування перекладами' }).getByRole('button', { name: 'Дані новели' }).click();
    const visibility = page.getByRole('region', { name: 'Новелу приховано' });
    await expect(visibility).toContainText('Причина: Порушення правил.');
    await visibility.getByRole('button', { name: 'Повернути в каталог' }).click();
    await expect(page.getByRole('region', { name: 'Видимість новели' }).getByRole('button', { name: 'Приховати новелу' })).toBeVisible();
});
