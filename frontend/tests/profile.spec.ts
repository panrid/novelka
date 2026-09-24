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

test('administrators open nickname history on demand in the user panel', async ({ page }) => {
    const account = { id: 'a1', username: 'reader-new', email: 'r@example.test', role: 'READER', created_at: '2026-09-01T10:00:00Z' };
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' } } }));
    await page.route('**/api/accounts?*', route => route.fulfill({ json: pageData([account]) }));
    await page.route('**/api/accounts/a1', route => route.fulfill({ json: account }));
    let loaded = 0;
    await page.route('**/api/accounts/a1/nicknames', route => { loaded++; return route.fulfill({ json: { items: [
        { previous_nickname: 'reader-old', new_nickname: 'reader-new', changed_at: '2026-09-20T10:00:00Z' }] } }); });
    await page.goto('/#/accounts');
    await expect(page).toHaveURL(/settings\?section=users/);
    await expect(page.getByRole('button', { name: 'reader-new' })).toBeVisible();
    expect(loaded).toBe(0);
    await page.getByRole('button', { name: 'reader-new' }).click();
    await expect(page.getByText('reader-old → reader-new')).toBeVisible();
});

test('user panel explains roles, saves allowed changes and blocks protected accounts', async ({ page }) => {
    const accounts = [
        { id: 'a1', username: 'reader', email: 'reader@example.test', role: 'READER', created_at: '2026-09-01T10:00:00Z' },
        { id: 'a2', username: 'other-admin', email: 'admin@example.test', role: 'ADMIN', created_at: '2026-09-02T10:00:00Z' },
    ];
    let changed: unknown;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'me', role: 'ADMIN' } } }));
    await page.route('**/api/accounts?*', route => {
        const q = new URL(route.request().url()).searchParams.get('q') || '';
        return route.fulfill({ json: pageData(accounts.filter(item => item.username.includes(q) || item.email.includes(q))) });
    });
    for (const account of accounts) {
        await page.route('**/api/accounts/' + account.id, route => route.fulfill({ json: account }));
        await page.route('**/api/accounts/' + account.id + '/nicknames', route => route.fulfill({ json: { items: [] } }));
    }
    await page.route('**/api/accounts/a1/role', route => {
        changed = route.request().postDataJSON(); accounts[0] = { ...accounts[0], role: 'EDITOR' };
        return route.fulfill({ json: accounts[0] });
    });
    await page.goto('/#/settings');
    const nav = page.getByRole('navigation', { name: 'Розділи налаштувань' });
    await expect(nav.getByRole('link')).toHaveText(['Користувачі та ролі', 'Вигляд']);
    await page.getByRole('searchbox', { name: 'Нік або email' }).fill('admin@');
    await expect(page.getByRole('button', { name: 'reader' })).toHaveCount(0);
    await page.getByRole('button', { name: 'other-admin' }).click();
    await expect(page.getByText('Адміністратор не змінює роль іншого адміністратора.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Зберегти роль' })).toHaveCount(0);
    await page.getByRole('searchbox', { name: 'Нік або email' }).fill('');
    await page.getByRole('button', { name: 'reader', exact: true }).click();
    await expect(page).toHaveURL(/user=a1/);
    const panel = page.getByRole('region', { name: 'reader' });
    await expect(panel.getByRole('radio', { name: /Адміністратор/ })).toBeDisabled();
    await expect(panel.getByText('Усе, що читач, а також черга всіх правок', { exact: false })).toBeVisible();
    await panel.getByRole('radio', { name: /Редактор/ }).check();
    await panel.getByRole('button', { name: 'Зберегти роль' }).click();
    await expect(panel.getByText('Роль змінено.')).toBeVisible();
    expect(changed).toEqual({ role: 'EDITOR' });
    await expect(page.locator('tr[aria-selected="true"]')).toContainText('Редактор');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('logout notice does not survive signing in again', async ({ page }) => {
    let user: { id: string; username: string; role: string } | null = { ...reader };
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user, registrationOpen: true } }));
    await page.route('**/api/auth/logout', route => { user = null; return route.fulfill({ status: 204 }); });
    await page.route('**/api/auth/login', route => { user = { ...reader }; return route.fulfill({ status: 204 }); });
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/tags?*', route => route.fulfill({ json: { items: [] } }));
    await page.goto('/#/profile');
    await page.route('**/api/profile', route => route.fulfill({ json: { ...reader, email: 'r@example.test', nicknameChanges: 0, nicknameAvailableAt: null } }));
    await page.getByRole('button', { name: 'Вийти' }).click();
    await page.getByRole('link', { name: 'Увійти' }).click();
    await page.getByLabel('Email або нік', { exact: true }).fill('reader');
    await page.getByLabel('Пароль', { exact: true }).fill('long-enough-password');
    await page.getByRole('button', { name: 'Увійти', exact: true }).click();
    await expect(page.getByRole('link', { name: /reader · Читач/ })).toBeVisible();
    await expect(page.getByText('Ви вийшли.')).toHaveCount(0);
});
