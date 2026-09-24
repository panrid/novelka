import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const reader = { id: 'r', username: 'reader', role: 'READER' };
const novel = { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [],
    firstChapter: 1, resumeChapter: null, rating: { score: 0, mine: 0 } };

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
});

test('reader puts a novel on a shelf and removes it from the novel page', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: reader, registrationOpen: true } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: { ...novel, libraryStatus: null } }));
    const sent: string[] = [];
    await page.route('**/api/library/n1', route => {
        const status = route.request().postDataJSON().status;
        sent.push(status);
        return route.fulfill({ json: { status: status || null } });
    });
    await page.goto('/#/novels/n1');
    const shelf = page.getByRole('combobox', { name: 'Моя бібліотека' });
    await expect(shelf).toHaveText(/Не в бібліотеці/);
    await shelf.click();
    await page.getByRole('option', { name: 'В планах' }).click();
    await expect(shelf).toHaveText(/В планах/);
    await shelf.click();
    await page.getByRole('option', { name: 'Не в бібліотеці' }).click();
    await expect(shelf).toHaveText(/Не в бібліотеці/);
    await expect.poll(() => sent).toEqual(['planned', '']);
});

test('guests do not see the library control or link', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: novel }));
    await page.goto('/#/novels/n1');
    await expect(page.getByRole('heading', { name: 'Водяний маг' })).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Моя бібліотека' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Бібліотека' })).toHaveCount(0);
});

test('library shows shelves with counts and moves a novel between them', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: reader, registrationOpen: true } }));
    const shelves: Record<string, string> = { n1: 'reading', n2: 'planned' };
    const titles: Record<string, string> = { n1: 'Водяний маг', n2: 'Тінь міста' };
    const requests: URLSearchParams[] = [];
    await page.route('**/api/library/counts', route => {
        const counts: Record<string, number> = { reading: 0, planned: 0, completed: 0, on_hold: 0, dropped: 0 };
        Object.values(shelves).forEach(status => counts[status]++);
        return route.fulfill({ json: counts });
    });
    await page.route('**/api/library?**', route => {
        const params = new URL(route.request().url()).searchParams;
        requests.push(params);
        const items = Object.entries(shelves).filter(([, status]) => status === params.get('status')).map(([id, status]) => ({
            id, title: titles[id], author: 'Автор', chapterCount: 10, readyChapters: 3, status, updatedAt: '2026-09-24T10:00:00Z' }));
        return route.fulfill({ json: pageData(items) });
    });
    await page.route('**/api/library/n1', route => {
        shelves.n1 = route.request().postDataJSON().status;
        return route.fulfill({ json: { status: shelves.n1 } });
    });
    await page.goto('/#/');
    await page.getByRole('link', { name: 'Бібліотека' }).first().click();
    await expect(page.getByRole('heading', { name: 'Моя бібліотека' })).toBeVisible();
    const tabs = page.getByRole('group', { name: 'Списки бібліотеки' });
    await expect(tabs.getByRole('button', { name: 'Читаю 1' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('heading', { name: 'Водяний маг' })).toBeVisible();
    await page.getByRole('combobox', { name: 'Список для «Водяний маг»' }).click();
    await page.getByRole('option', { name: 'Завершено' }).click();
    await expect(page.getByText('У списку «Читаю» поки порожньо')).toBeVisible();
    await expect(tabs.getByRole('button', { name: 'Завершено 1' })).toBeVisible();
    await tabs.getByRole('button', { name: 'В планах 1' }).click();
    await expect(page).toHaveURL(/status=planned/);
    await expect(page.getByRole('heading', { name: 'Тінь міста' })).toBeVisible();
    expect(requests.at(-1)?.get('sort')).toBe('updated');
});
