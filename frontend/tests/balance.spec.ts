import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
});

test('owner tops up a user balance and sees the history', async ({ page }) => {
    const account = { id: 'a1', username: 'lina', email: 'lina@example.test', role: 'READER', created_at: '2026-09-01T10:00:00Z' };
    const topups: { id: number; amount_usd: number; note: string; created_at: string; actor: string }[] = [];
    let available = 0;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'owner', role: 'OWNER' }, registrationOpen: true } }));
    await page.route('**/api/settings', route => route.fulfill({ json: { revision: 1, registrationOpen: true, segmentChars: 5000, targetUsdPer5000: .1, maxBudgetUsd: 5, stages: [], adminSelfApproval: false } }));
    await page.route('**/api/accounts?*', route => route.fulfill({ json: pageData([account]) }));
    await page.route('**/api/accounts/a1', route => route.fulfill({ json: account }));
    await page.route('**/api/accounts/a1/nicknames', route => route.fulfill({ json: { items: [] } }));
    await page.route('**/api/accounts/a1/balance', route => {
        if (route.request().method() === 'POST') {
            const body = route.request().postDataJSON();
            expect(body).toEqual({ amountUsd: 2.5, note: 'Для першої новели' });
            available += body.amountUsd;
            topups.unshift({ id: 1, amount_usd: body.amountUsd, note: body.note, created_at: '2026-09-24T10:00:00Z', actor: 'owner' });
        }
        return route.fulfill({ json: { balance: { available, toppedUp: available, reserved: 0, spent: 0, unlimited: false }, topups } });
    });
    await page.goto('/#/settings?section=users&user=a1');
    const panel = page.getByRole('region', { name: 'Баланс перекладу' });
    await expect(panel).toContainText('Доступно');
    await panel.getByLabel('Сума, $').fill('2.5');
    await panel.getByLabel('Коментар').fill('Для першої новели');
    await panel.getByRole('button', { name: 'Поповнити' }).click();
    await expect(panel.getByText('Баланс поповнено.')).toBeVisible();
    await expect(panel.getByRole('listitem')).toContainText('Для першої новели');
});

test('a translator sees their balance and why a task was refused', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'u1', username: 'lina', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/manage/novels?*', route => route.fulfill({ json: { items: [{ id: 'm1', title: 'Мій переклад' }] } }));
    await page.route('**/api/tasks/defaults', route => route.fulfill({ json: { models: { translate: 'provider/model' }, maxBudgetUsd: 5 } }));
    await page.route('**/api/tasks?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/models', route => route.fulfill({ json: { provider: 'openrouter', refreshedAt: null, stale: false, error: null, items: [] } }));
    await page.route('**/api/balance', route => route.fulfill({ json: { available: 0, toppedUp: 0, reserved: 0, spent: 0, unlimited: false } }));
    await page.route('**/api/tasks', route => route.fulfill({ status: 402, json: { message: 'Недостатньо коштів: доступно $0.00, бюджет завдання $0.10. Баланс поповнює власник сайту.' } }));
    await page.goto('/#/manage?novel=m1');
    await expect(page.getByRole('link', { name: 'Майстерня' })).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Новела' })).toHaveText(/Мій переклад/);
    await expect(page.locator('.balance-summary')).toContainText('баланс поповнює власник сайту');
    await page.getByLabel(/Додатковий бюджет/).fill('0.1');
    await page.getByLabel(/Дозволяю платні запити/).check();
    await page.getByRole('button', { name: 'Додати в чергу' }).click();
    await expect(page.getByText(/Недостатньо коштів/)).toBeVisible();
});
