import { expect, test } from '@playwright/test';

const message = (id: number, author: string, body: string, own = false) => ({
    id, author_id: own ? 'me' : 'other', author, body, created_at: '2026-09-24T10:00:00Z', can_delete: own,
});

test('chat shows latest messages, sends, polls new and deleted ones and loads older history', async ({ page }) => {
    let serverMessages = [message(40, 'anna', 'Привіт'), message(41, 'bohdan', 'Як справи?')];
    const deleted: number[] = [];
    let sent: unknown;
    await page.clock.install();
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'me', username: 'me', role: 'READER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/chat?*', route => {
        const before = Number(new URL(route.request().url()).searchParams.get('before'));
        return route.fulfill({ json: before ? { items: [message(3, 'old', 'Давнє повідомлення')], nextCursor: 0 }
            : { items: [...serverMessages].reverse(), nextCursor: 40 } });
    });
    await page.route('**/api/chat', route => {
        if (route.request().method() === 'GET') return route.fulfill({ json: { items: [...serverMessages].reverse(), nextCursor: 40 } });
        sent = route.request().postDataJSON();
        serverMessages = [...serverMessages, message(42, 'me', (sent as { body: string }).body, true)];
        return route.fulfill({ json: { id: 42 } });
    });
    await page.route('**/api/chat/updates?*', route => {
        const after = Number(new URL(route.request().url()).searchParams.get('after'));
        return route.fulfill({ json: { items: serverMessages.filter(item => item.id > after), deleted } });
    });
    await page.route('**/api/chat/42', route => { serverMessages = serverMessages.filter(item => item.id !== 42); return route.fulfill({ json: { message: 'ok' } }); });
    await page.goto('/#/chat');
    await expect(page.getByRole('link', { name: 'Чат', exact: true })).toBeVisible();
    const box = page.getByRole('region', { name: 'Повідомлення чату' });
    await expect(box.locator('.chat-message p')).toHaveText(['Привіт', 'Як справи?']);
    await box.getByLabel('Повідомлення').fill('Усе добре');
    await box.getByLabel('Повідомлення').press('Enter');
    await expect(box.locator('.chat-message p')).toHaveText(['Привіт', 'Як справи?', 'Усе добре']);
    expect(sent).toEqual({ body: 'Усе добре' });

    serverMessages = [...serverMessages, message(43, 'anna', 'Новина від Анни')];
    deleted.push(41);
    await page.clock.runFor(4100);
    await expect(box.locator('.chat-message p')).toHaveText(['Привіт', 'Усе добре', 'Новина від Анни']);

    await box.getByRole('button', { name: 'Завантажити старіші' }).click();
    await expect(box.locator('.chat-message p').first()).toHaveText('Давнє повідомлення');
    await expect(box.getByRole('button', { name: 'Завантажити старіші' })).toHaveCount(0);

    page.once('dialog', dialog => dialog.accept());
    await box.getByRole('button', { name: 'Видалити повідомлення me' }).click();
    await expect(box.getByText('Усе добре')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('chat requires signing in', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.goto('/#/chat');
    await expect(page.getByRole('heading', { name: 'Потрібен доступ' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Чат', exact: true })).toHaveCount(0);
});
