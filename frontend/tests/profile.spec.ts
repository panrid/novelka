import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const reader = { id: 'reader', username: 'reader', role: 'READER' };

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf-test', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
});

test('registration asks for nickname and email, login accepts either', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    let registered: unknown;
    await page.route('**/api/auth/register', route => { registered = route.request().postDataJSON(); return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/login');
    await page.getByRole('button', { name: 'Створити обліковий запис' }).click();
    await page.getByLabel('Нік', { exact: true }).fill('newreader');
    await page.getByLabel('Email', { exact: true }).fill('new@example.test');
    await page.getByLabel('Пароль', { exact: true }).fill('long-enough-password');
    await page.getByRole('button', { name: 'Зареєструватися' }).click();
    await expect(page.getByLabel('Email або нік', { exact: true })).toHaveValue('new@example.test');
    expect(registered).toEqual({ username: 'newreader', email: 'new@example.test', password: 'long-enough-password' });
});

test('profile shows nickname cooldown and changes nickname and email', async ({ page }) => {
    let user = { ...reader };
    let profile = { ...reader, email: 'reader@example.test', nicknameChanges: 0, nicknameAvailableAt: null as string | null };
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user, registrationOpen: true } }));
    await page.route('**/api/profile', route => route.fulfill({ json: profile }));
    await page.route('**/api/profile/nickname', route => {
        const { nickname } = route.request().postDataJSON();
        user = { ...user, username: nickname };
        profile = { ...profile, username: nickname, nicknameChanges: 1, nicknameAvailableAt: '2030-01-01T12:00:00Z' };
        return route.fulfill({ json: profile });
    });
    await page.route('**/api/profile/email', route => {
        expect(route.request().postDataJSON()).toEqual({ email: 'fresh@example.test', password: 'current-password' });
        profile = { ...profile, email: 'fresh@example.test' };
        return route.fulfill({ json: profile });
    });
    await page.goto('/#/profile');
    await expect(page.getByText('Змінити нік можна зараз.')).toBeVisible();
    await page.getByLabel('Новий нік').fill('renamed');
    await page.getByRole('button', { name: 'Змінити нік' }).click();
    await expect(page.getByText(/Наступна зміна буде доступна/)).toBeVisible();
    await expect(page.getByLabel('Новий нік')).toBeDisabled();
    await expect(page.getByRole('link', { name: /renamed · Читач/ })).toHaveAttribute('href', '#/profile');
    await page.getByLabel('Новий email').fill('fresh@example.test');
    await page.getByLabel('Поточний пароль').fill('current-password');
    await page.getByRole('button', { name: 'Змінити email' }).click();
    await expect(page.locator('.profile-summary')).toContainText('fresh@example.test');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('administrators open nickname history on demand', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' } } }));
    await page.route('**/api/accounts?*', route => route.fulfill({ json: pageData([{ id: 'a1', username: 'reader-new', role: 'READER' }]) }));
    let loaded = 0;
    await page.route('**/api/accounts/a1/nicknames', route => { loaded++; return route.fulfill({ json: { items: [
        { previous_nickname: 'reader-old', new_nickname: 'reader-new', changed_at: '2026-09-20T10:00:00Z' }] } }); });
    await page.goto('/#/accounts');
    await expect(page.getByText('reader-new', { exact: true })).toBeVisible();
    expect(loaded).toBe(0);
    await page.getByText('Історія ніків').click();
    await expect(page.getByText('reader-old → reader-new')).toBeVisible();
});
