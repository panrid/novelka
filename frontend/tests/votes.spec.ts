import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const novel = { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [],
    firstChapter: 1, resumeChapter: null };

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
});

test('reader votes up, changes and removes the vote on a novel', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'r', username: 'reader', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: { ...novel, rating: { score: 4, mine: 0 } } }));
    const sent: number[] = [];
    let mine = 0;
    await page.route('**/api/votes/novel/n1', route => {
        const value = route.request().postDataJSON().value;
        sent.push(value);
        mine = value;
        return route.fulfill({ json: { score: 4 + mine, mine } });
    });
    await page.goto('/#/novels/n1');
    const control = page.getByRole('group', { name: 'Рейтинг новели' });
    await expect(control.getByLabel('Рейтинг 4')).toHaveText('+4');
    await control.getByRole('button', { name: 'Подобається', exact: true }).click();
    await expect(control.getByRole('button', { name: 'Прибрати голос «подобається»' })).toHaveAttribute('aria-pressed', 'true');
    await expect(control.getByLabel('Рейтинг 5')).toBeVisible();
    await control.getByRole('button', { name: 'Не подобається' }).click();
    await expect(control.getByLabel('Рейтинг 3')).toBeVisible();
    await control.getByRole('button', { name: 'Прибрати голос «не подобається»' }).click();
    await expect(control.getByLabel('Рейтинг 4')).toBeVisible();
    expect(sent).toEqual([1, -1, 0]);
});

test('anonymous readers see the rating but cannot vote', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/novels/n1', route => route.fulfill({ json: { ...novel, rating: { score: -2, mine: 0 } } }));
    await page.goto('/#/novels/n1');
    const control = page.getByRole('group', { name: 'Рейтинг новели' });
    await expect(control.getByLabel('Рейтинг -2')).toHaveText('-2');
    await expect(control.getByRole('button', { name: 'Подобається', exact: true })).toBeDisabled();
    await expect(control.getByRole('button', { name: 'Подобається', exact: true })).toHaveAttribute('title', 'Увійдіть, щоб голосувати');
});
