import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const novel = { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [],
    firstChapter: 1, resumeChapter: null, rating: { score: 0, mine: 0 } };
const comment = (id: number, author: string, body: string, own = false) => ({
    id, author_id: own ? 'me' : 'other', author, body, created_at: '2026-09-24T10:00:00Z', edited_at: null,
    rating: { score: 0, mine: 0 }, can_edit: own, can_delete: own,
});

test('reader discusses a novel: posts, edits, deletes own comment and loads older ones', async ({ page }) => {
    let items = [comment(30, 'other-reader', 'Перший коментар')];
    const older = Array.from({ length: 3 }, (_, index) => comment(10 - index, 'old-reader', 'Старий ' + index));
    let posted: unknown;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'me', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: novel }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/novels/n1/comments**', route => {
        if (route.request().method() === 'POST') {
            posted = route.request().postDataJSON();
            items = [comment(31, 'me', (posted as { body: string }).body, true), ...items];
            return route.fulfill({ json: { id: 31 } });
        }
        const before = new URL(route.request().url()).searchParams.get('before');
        return route.fulfill({ json: before && before !== '0' ? { items: older, nextCursor: 0 } : { items, nextCursor: 11 } });
    });
    await page.route('**/api/comments/31', route => {
        if (route.request().method() === 'DELETE') { items = items.filter(item => item.id !== 31); return route.fulfill({ json: { message: 'ok' } }); }
        items = items.map(item => item.id === 31 ? { ...item, body: route.request().postDataJSON().body, edited_at: '2026-09-24T11:00:00Z' } : item);
        return route.fulfill({ json: { message: 'ok' } });
    });
    await page.goto('/#/novels/n1');
    const section = page.getByRole('region', { name: 'Обговорення новели' });
    await expect(section.getByText('Перший коментар')).toBeVisible();
    await expect(section.getByRole('button', { name: 'Редагувати' })).toHaveCount(0);
    await section.getByLabel('Ваш коментар').fill('Мій відгук');
    await section.getByRole('button', { name: 'Опублікувати коментар' }).click();
    await expect.poll(() => posted).toEqual({ chapter: 0, body: 'Мій відгук' });
    await expect(section.locator('.comment-body', { hasText: 'Мій відгук' })).toBeVisible();
    await section.getByRole('button', { name: 'Редагувати' }).click();
    await section.getByLabel('Текст коментаря').fill('Мій відгук, уточнено');
    await section.getByRole('button', { name: 'Зберегти' }).click();
    await expect(section.locator('.comment-body', { hasText: 'Мій відгук, уточнено' })).toBeVisible();
    await expect(section.getByText('змінено')).toBeVisible();
    await section.getByRole('button', { name: 'Показати старіші' }).click();
    await expect(section.getByText('Старий 2')).toBeVisible();
    await expect(section.getByRole('button', { name: 'Показати старіші' })).toHaveCount(0);
    page.once('dialog', dialog => dialog.accept());
    await section.getByRole('button', { name: 'Видалити' }).click();
    await expect(section.locator('.comment-body', { hasText: 'Мій відгук, уточнено' })).toHaveCount(0);
});

test('anonymous visitors read comments and are invited to sign in', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: novel }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/novels/n1/comments**', route => route.fulfill({ json: { items: [comment(1, 'reader', 'Гарно')], nextCursor: 0 } }));
    await page.goto('/#/novels/n1');
    const section = page.getByRole('region', { name: 'Обговорення новели' });
    await expect(section.getByText('Гарно')).toBeVisible();
    await expect(section.getByLabel('Ваш коментар')).toHaveCount(0);
    await expect(section.getByRole('link', { name: 'Увійдіть' })).toBeVisible();
});
