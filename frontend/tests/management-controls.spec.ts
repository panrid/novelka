import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' }, registrationOpen: true } }));
    await page.route('**/api/novels', route => route.fulfill({ json: [{ id: 'n0022gd', title: 'Водяний маг', author: 'Автор', chapterCount: 10, readyChapters: 3, aliases: [], tags: [] }] }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([{ id: 'n0022gd', title: 'Водяний маг', author: 'Автор', chapterCount: 10, readyChapters: 3, aliases: [], tags: [] }]) }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([]) }));
});

test('styled dropdown supports keyboard, dismissal and viewport bounds', async ({ page }, testInfo) => {
    await page.goto('/#/manage');
    const select = page.getByRole('combobox', { name: 'Новела', exact: true });
    await select.click();
    await expect(page.getByRole('listbox')).toBeVisible();
    const box = await page.getByRole('listbox').boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    await page.screenshot({ path: testInfo.outputPath('select-popup.png') });
    await select.press('Escape');
    await expect(page.getByRole('listbox')).toHaveCount(0);
    await expect(select).toContainText('Оберіть новелу');
    await select.dispatchEvent('keydown', { key: 'в' });
    await select.press('Enter');
    await expect(select).toContainText('Водяний маг');
    await expect(select).toBeFocused();
    await select.click();
    await page.getByRole('heading', { name: 'Майстерня перекладу' }).click();
    await expect(page.getByRole('listbox')).toHaveCount(0);
    await select.focus();
    await select.press('Home');
    await select.press('Enter');
    await expect(select).toContainText('Оберіть новелу');
    await select.press('ArrowDown');
    await select.press('Tab');
    await expect(page.getByRole('listbox')).toHaveCount(0);
});

test('costs sort numerically in both directions and retain unknown values', async ({ page }) => {
    const rows = [
        { id: 'a', novel_id: 'n0022gd', chapter: 10, estimated_usd: '0.1', known_actual_usd: null, actual_usd: null, input_tokens: 200, created_at: '2026-09-22T10:00:00Z' },
        { id: 'b', novel_id: 'n0022gd', chapter: 2, estimated_usd: '0.02', known_actual_usd: '0.12', actual_usd: '0.12', input_tokens: 20, created_at: '2026-09-21T10:00:00Z' },
        { id: 'c', novel_id: 'n0022gd', chapter: 3, estimated_usd: '0.09', known_actual_usd: '0.9', actual_usd: '0.9', input_tokens: 100, created_at: '2026-09-20T10:00:00Z' },
    ].map(row => ({ ...row, stage: 'translate', model: 'test/model', state: 'complete', calls: 1, unknown_cost_calls: row.actual_usd === null ? 1 : 0, output_tokens: 10 }));
    await page.route('**/api/manage/costs?**', route => {
        const params = new URL(route.request().url()).searchParams;
        const key = params.get('sort') || 'created';
        const direction = params.get('direction') === 'asc' ? 1 : -1;
        const field = key === 'created' ? 'created_at' : key;
        const sorted = [...rows].sort((a, b) => {
            const left = a[field as keyof typeof a], right = b[field as keyof typeof b];
            if (left == null || right == null) return left == null ? right == null ? 0 : 1 : -1;
            return (['chapter', 'estimated_usd', 'actual_usd', 'known_actual_usd', 'input_tokens', 'output_tokens', 'calls', 'unknown_cost_calls'].includes(field)
                ? Number(left) - Number(right) : String(left).localeCompare(String(right), 'uk', { numeric: true })) * direction;
        });
        return route.fulfill({ json: pageData(sorted) });
    });
    await page.goto('/#/manage');
    await page.getByRole('button', { name: 'Витрати', exact: true }).click();
    const chapters = page.locator('.cost-table tbody tr td:nth-child(2)');
    await page.getByRole('button', { name: 'Глава', exact: true }).click();
    await expect(chapters).toHaveText(['2', '3', '10']);
    await page.getByRole('button', { name: 'Глава', exact: true }).click();
    await expect(chapters).toHaveText(['10', '3', '2']);
    await page.getByRole('button', { name: 'Факт', exact: true }).click();
    await expect(chapters).toHaveText(['2', '3', '10']);
    await page.getByRole('button', { name: 'Факт', exact: true }).click();
    await expect(chapters).toHaveText(['3', '2', '10']);
    await expect(page.getByRole('columnheader', { name: 'Факт' })).toHaveAttribute('aria-sort', 'descending');
    await page.getByLabel('Кожен запит окремо').check();
    await page.getByRole('button', { name: 'Час', exact: true }).click();
    await expect(chapters).toHaveText(['3', '2', '10']);
    await page.getByRole('button', { name: 'Факт', exact: true }).click();
    await expect(chapters).toHaveText(['2', '3', '10']);
});
