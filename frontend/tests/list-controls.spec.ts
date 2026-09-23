import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test('accounts combine backend search, role, sorting and pages with restorable URL', async ({ page }, testInfo) => {
    const accounts = Array.from({ length: 28 }, (_, index) => ({
        id: `a${String(index + 1).padStart(2, '0')}`, username: `reader-${String(index + 1).padStart(2, '0')}`,
        role: index % 2 ? 'EDITOR' : 'READER',
    }));
    let queries = 0;
    let failure = false;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' } } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/accounts?*', route => {
        if (failure) return route.fulfill({ status: 503, json: {} });
        const params = new URL(route.request().url()).searchParams;
        const q = params.get('q')?.toLowerCase() || '';
        if (q) queries++;
        const role = params.get('role');
        const size = Number(params.get('size') || 25);
        const current = Number(params.get('page') || 1);
        const filtered = accounts.filter(account => account.username.includes(q) && (!role || account.role === role));
        filtered.sort((a, b) => (params.get('sort') === 'username' ? a.username.localeCompare(b.username) : a.id.localeCompare(b.id))
            * (params.get('direction') === 'asc' ? 1 : -1));
        return route.fulfill({ json: pageData(filtered.slice((current - 1) * size, current * size), current, size, filtered.length) });
    });
    await page.goto('/#/accounts');
    await expect(page.getByText('Сторінка 1 з 2')).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('accounts.png'), fullPage: true });
    await page.getByRole('button', { name: 'Далі →' }).click();
    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText('Сторінка 2 з 2')).toBeVisible();
    await page.getByRole('button', { name: 'Логін', exact: true }).click();
    await expect(page.getByRole('columnheader', { name: /Логін/ })).toHaveAttribute('aria-sort', 'ascending');
    await expect(page).toHaveURL(/page=1/);
    await page.getByLabel('Пояснення: Логін').focus();
    await page.getByLabel('Пояснення: Логін').press('Enter');
    await expect(page.getByText('Ім’я облікового запису.')).toBeVisible();
    await page.getByRole('searchbox', { name: 'Знайти користувача' }).fill('reader-0');
    await expect(page.getByText('Сторінка 1 з 1')).toBeVisible();
    expect(queries).toBe(1);
    await page.getByRole('combobox', { name: 'Роль', exact: true }).click();
    await page.getByRole('option', { name: 'Редактор' }).click();
    await expect(page.locator('tbody tr')).toHaveCount(4);
    await page.reload();
    await expect(page.locator('tbody tr')).toHaveCount(4);
    await expect(page).toHaveURL(/role=EDITOR/);
    await page.getByRole('button', { name: 'Очистити фільтр' }).click();
    await expect(page.locator('tbody tr')).toHaveCount(9);
    await page.getByRole('searchbox', { name: 'Знайти користувача' }).fill('nobody');
    await expect(page.getByText('За заданими параметрами нічого не знайдено.')).toBeVisible();
    failure = true;
    await page.getByRole('searchbox', { name: 'Знайти користувача' }).fill('failed');
    await expect(page.getByRole('alert')).toBeVisible();
    failure = false;
    await page.getByRole('button', { name: 'Спробувати ще раз' }).click();
    await expect(page.getByText('За заданими параметрами нічого не знайдено.')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
