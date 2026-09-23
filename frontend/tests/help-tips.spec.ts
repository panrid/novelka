import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' } } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/accounts?*', route => route.fulfill({ json: pageData([{ id: 'a1', username: 'reader', role: 'READER' }]) }));
});

test('help opens by keyboard, closes with Escape and stays inside the viewport', async ({ page }) => {
    await page.goto('/#/accounts');
    const help = page.getByRole('button', { name: 'Пояснення: Поточна роль' });
    await help.focus();
    await help.press('Enter');
    await expect(help).toHaveAttribute('aria-expanded', 'true');
    const bubble = page.getByRole('note');
    await expect(bubble).toHaveText('Права користувача на сайті.');
    const box = await bubble.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    await page.keyboard.press('Escape');
    await expect(bubble).toHaveCount(0);
    await expect(help).toBeFocused();
});

test('help toggles by tap or click and closes on outside press', async ({ page }) => {
    await page.goto('/#/accounts');
    const help = page.getByRole('button', { name: 'Пояснення: Логін' });
    await help.click();
    await expect(page.getByRole('note')).toHaveText('Ім’я облікового запису.');
    await page.getByRole('heading', { name: 'Користувачі та ролі' }).click();
    await expect(page.getByRole('note')).toHaveCount(0);
    await help.click();
    await help.click();
    await expect(page.getByRole('note')).toHaveCount(0);
});

test('mouse hover previews help without clicking', async ({ page, isMobile }) => {
    test.skip(isMobile, 'Touch screens have no hover.');
    await page.goto('/#/accounts');
    await page.getByRole('button', { name: 'Пояснення: Логін' }).hover();
    await expect(page.getByRole('note')).toBeVisible();
    await page.getByRole('heading', { name: 'Користувачі та ролі' }).hover();
    await expect(page.getByRole('note')).toHaveCount(0);
});
