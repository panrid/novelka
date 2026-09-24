import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const novels = [{ id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [] }];

test('translator picks editors by nickname, removes them and opens review to everyone', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' }, registrationOpen: true, canReview: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData(novels) }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/manage/n1', route => route.fulfill({ json: {
        novel: { id: 'n1', title: '物語', titleUk: 'Водяний маг', author: 'Автор', authorUk: null, descriptionUk: null, chapterCount: 10 },
        aliases: [], importedChapters: 3, glossary: { revision: 1, entries: [] }, proposals: [], tags: [], aiTranslated: true,
    } }));
    const state = { owner: { id: 'owner', username: 'owner' }, openReview: false, editors: [] as { id: string; username: string; granted_at: string }[] };
    await page.route('**/api/manage/n1/editors', route => {
        if (route.request().method() === 'POST') {
            expect(route.request().postDataJSON()).toEqual({ accountId: 'u2' });
            state.editors = [{ id: 'u2', username: 'lina', granted_at: '2026-09-24T10:00:00Z' }];
        }
        return route.fulfill({ json: state });
    });
    await page.route('**/api/manage/n1/editors/u2', route => { state.editors = []; return route.fulfill({ json: state }); });
    await page.route('**/api/manage/n1/review-access', route => {
        state.openReview = route.request().postDataJSON().open;
        return route.fulfill({ json: state });
    });
    await page.route('**/api/users/search?*', route => route.fulfill({ json: { items: [{ id: 'owner', username: 'owner' }, { id: 'u2', username: 'lina' }] } }));

    await page.goto('/#/manage?novel=n1');
    await page.getByRole('navigation', { name: 'Керування перекладами' }).getByRole('button', { name: 'Дані новели' }).click();
    await page.getByRole('navigation', { name: 'Керування новелою' }).getByRole('button', { name: 'Редактори' }).click();
    await expect(page.getByText('Редакторів ще немає.')).toBeVisible();
    await page.getByLabel('Додати редактора').scrollIntoViewIfNeeded();
    await page.getByLabel('Додати редактора').fill('li');
    await expect(page.getByRole('option')).toHaveCount(1);
    await page.getByRole('option', { name: 'lina' }).click();
    await expect(page.getByText('lina тепер вирішує щодо правок.')).toBeVisible();
    await page.getByRole('button', { name: 'Прибрати редактора lina' }).click();
    await expect(page.getByText('Редакторів ще немає.')).toBeVisible();
    await page.getByLabel('Усі користувачі, включно з майбутніми').click();
    await expect(page.getByLabel('Усі користувачі, включно з майбутніми')).toBeChecked();
    await expect(page.getByText('Тепер правки можуть перевіряти всі користувачі.')).toBeVisible();
    await expect(page.getByLabel('Додати редактора')).toHaveCount(0);
});

test('queue tab appears only for accounts that review some novel', async ({ page }) => {
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/corrections?**', route => route.fulfill({ json: pageData([]) }));
    let canReview = false;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'u', username: 'reader', role: 'READER' }, registrationOpen: true, canReview } }));
    await page.goto('/#/corrections');
    await expect(page.getByRole('heading', { name: 'Редакторські правки' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Черга редактора' })).toHaveCount(0);
    canReview = true;
    await page.reload();
    await expect(page.getByRole('button', { name: 'Черга редактора' })).toBeVisible();
});
