import { expect, test, type Page } from '@playwright/test';

async function guest(page: Page) {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
}

test('forgotten password: request a link, then set a new password from it', async ({ page }) => {
    await guest(page);
    let requested: unknown, confirmed: unknown;
    await page.route('**/api/auth/password-reset', route => { requested = route.request().postDataJSON(); return route.fulfill({ json: { message: 'ok' } }); });
    await page.route('**/api/auth/password-reset/confirm', route => { confirmed = route.request().postDataJSON(); return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/login');
    await page.getByRole('link', { name: 'Забули пароль?' }).click();
    await expect(page.getByRole('heading', { name: 'Забули пароль?' })).toBeVisible();
    await page.getByLabel('Email', { exact: true }).fill('lina@example.test');
    await page.getByRole('button', { name: 'Надіслати посилання' }).click();
    await expect(page.getByText(/Якщо акаунт із цією адресою існує/)).toBeVisible();
    expect(requested).toEqual({ email: 'lina@example.test' });

    await page.goto('/#/reset-password?token=abc_DEF-123');
    await page.getByLabel('Новий пароль').fill('new-password-1234');
    await page.getByLabel('Повторіть пароль').fill('new-password-12');
    await expect(page.getByText('Паролі не збігаються.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Зберегти пароль' })).toBeDisabled();
    await page.getByLabel('Повторіть пароль').fill('new-password-1234');
    await page.getByRole('button', { name: 'Зберегти пароль' }).click();
    await expect(page.getByText('Пароль змінено. Тепер увійдіть із новим паролем.')).toBeVisible();
    expect(confirmed).toEqual({ token: 'abc_DEF-123', password: 'new-password-1234' });
});

test('an expired reset link explains itself and offers a new one', async ({ page }) => {
    await guest(page);
    await page.route('**/api/auth/password-reset/confirm', route => route.fulfill({ status: 400, json: { message: 'Посилання недійсне або застаріле. Попросіть новий лист.' } }));
    await page.goto('/#/reset-password?token=old');
    await page.getByLabel('Новий пароль').fill('new-password-1234');
    await page.getByLabel('Повторіть пароль').fill('new-password-1234');
    await page.getByRole('button', { name: 'Зберегти пароль' }).click();
    await expect(page.getByText('Посилання недійсне або застаріле. Попросіть новий лист.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Надіслати нове посилання' })).toHaveAttribute('href', '#/forgot-password');
});

test('the confirmation link confirms the email once', async ({ page }) => {
    await guest(page);
    const tokens: string[] = [];
    await page.route('**/api/auth/verify-email', route => { tokens.push(route.request().postDataJSON().token); return route.fulfill({ json: { message: 'Email підтверджено. Дякуємо!' } }); });
    await page.goto('/#/verify-email?token=tok_123');
    await expect(page.getByRole('heading', { name: 'Email підтверджено' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Увійти' }).last()).toHaveAttribute('href', '#/login');
    expect(tokens).toEqual(['tok_123']);
});

test('profile shows an unconfirmed email and resends the letter', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'u', username: 'lina', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/balance', route => route.fulfill({ json: { available: 0, toppedUp: 0, reserved: 0, spent: 0, unlimited: false } }));
    await page.route('**/api/profile', route => route.fulfill({ json: { id: 'u', username: 'lina', email: 'lina@example.test', emailVerified: false,
        role: 'READER', nicknameChanges: 0, nicknameAvailableAt: null } }));
    let sent = 0;
    await page.route('**/api/profile/email/verification', route => { sent++; return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/profile');
    await expect(page.getByText('не підтверджено')).toBeVisible();
    await page.getByRole('button', { name: 'Надіслати лист ще раз' }).click();
    await expect(page.getByText(/Лист надіслано/)).toBeVisible();
    expect(sent).toBe(1);
});
