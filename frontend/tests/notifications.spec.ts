import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const novel = { id: 'n0022gd', title: 'Водяний маг', author: 'Автор', chapterCount: 10, readyChapters: 3, aliases: [] };
const task = { id: 'failed-task', operation: 'translate', novel_id: novel.id, state: 'failed', message: 'Dictionary changed; run proofread or translate --force',
    current_job_id: 'job-2', current_chapter: 2, latest_job_state: 'needs-review', can_resume: false, can_proofread: true,
    spent_usd: .03, cancel_requested: false, username: 'owner', request: { first: 1, last: 10 } };

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/novels', route => route.fulfill({ json: [novel] }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([novel]) }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0 } }));
    await page.route('**/api/tasks?*', route => route.fulfill({ json: pageData([task]) }));
    await page.route('**/api/tasks/failed-task', route => route.fulfill({ json: task }));
    await page.route('**/api/tasks/new-task', route => route.fulfill({ json: { ...task, id: 'new-task', state: 'queued', message: null } }));
    await page.route('**/api/manage/n0022gd', route => route.fulfill({ json: { novel, aliases: [], chapters: [], jobs: [], glossary: { revision: 1, entries: [] }, proposals: [] } }));
});

test('notifications persist read state and open exact task or glossary', async ({ page }, testInfo) => {
    const items = [
        { id: 3, kind: 'task_failed', task_id: task.id, entry_count: null, task_operation: 'translate', task_first: 7, task_last: 10 },
        { id: 2, kind: 'glossary_added', task_id: null, entry_count: 2 },
        { id: 1, kind: 'chapter_published', task_id: null, entry_count: null },
    ].map(item => ({ ...item, novel_id: novel.id, novel_title: novel.title, chapter: 2, created_at: '2026-09-23T08:00:00Z', read: false }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items, unread: items.filter(item => !item.read).length, latestId: 3 } }));
    await page.route('**/api/notifications/*/read', route => {
        const id = Number(route.request().url().split('/').at(-2));
        items.find(item => item.id === id)!.read = true;
        return route.fulfill({ status: 204 });
    });
    await page.route('**/api/notifications/read-all?through=3', route => { items.forEach(item => item.read = true); return route.fulfill({ status: 204 }); });
    await page.goto('/#/manage');
    await page.getByRole('button', { name: 'Сповіщення: 3 непрочитаних', exact: true }).click();
    const panel = page.getByRole('region', { name: 'Сповіщення', exact: true });
    await expect(panel).toBeVisible();
    const box = await panel.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    await page.screenshot({ path: testInfo.outputPath('notifications.png') });
    await expect(panel.getByRole('link', { name: /Нова глава 2/ })).toHaveAttribute('href', '#/novels/n0022gd/chapters/2');
    await expect(panel.getByRole('link', { name: /Переклад зупинився через помилку: глави 7–10/ })).toContainText('Водяний маг');
    await panel.getByRole('link', { name: /Переклад зупинився/ }).click();
    await expect(page).toHaveURL(/task=failed-task/);
    await expect(page.getByRole('button', { name: 'Повторно вичитати', exact: true })).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Сповіщення: 2 непрочитаних', exact: true }).click();
    await panel.getByRole('link', { name: /Нові записи словника/ }).click();
    await expect(page).toHaveURL(/tab=glossary/);
    await expect(page.getByRole('button', { name: 'Словник', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('button', { name: 'Сповіщення: 1 непрочитаних', exact: true }).click();
    await panel.getByRole('button', { name: 'Прочитати всі' }).click();
    await expect(panel).toContainText('0 непрочитаних');
    await panel.getByRole('button', { name: 'Закрити сповіщення' }).press('Escape');
    await expect(panel).toHaveCount(0);
});

test('quick actions prepare only the failed chapter and require budget approval', async ({ page }) => {
    const submitted: Record<string, unknown>[] = [];
    await page.route('**/api/tasks', route => {
        submitted.push(route.request().postDataJSON());
        return route.fulfill({ json: { id: 'new-task' } });
    });
    await page.goto('/#/manage');
    await page.getByRole('link', { name: 'Відкрити словник' }).click();
    await expect(page.getByRole('button', { name: 'Словник', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('link', { name: 'До завдання', exact: true }).click();
    await page.getByRole('button', { name: 'Повторно вичитати', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Операція', exact: true })).toContainText('Повторно вичитати одну главу');
    await expect(page.getByLabel('Перша глава', { exact: true })).toHaveValue('2');
    await expect(page.getByRole('button', { name: 'Додати в чергу' })).toBeDisabled();
    await page.getByRole('button', { name: 'Перекласти главу заново' }).click();
    await expect(page.getByLabel('Перша глава', { exact: true })).toHaveValue('2');
    await expect(page.getByLabel('Остання глава', { exact: true })).toHaveValue('2');
    await expect(page.getByLabel('Додатковий бюджет для всього запуску, $')).toHaveValue('');
    expect(submitted).toHaveLength(0);
    await page.getByLabel('Додатковий бюджет для всього запуску, $').fill('0.5');
    await page.getByLabel('Дозволяю платні запити OpenRouter').check();
    await page.getByRole('button', { name: 'Додати в чергу' }).click();
    await expect(page).toHaveURL(/task=new-task/);
    expect(submitted).toHaveLength(1);
    expect(submitted[0]).toMatchObject({ operation: 'translate', novelId: novel.id, first: 2, last: 2, force: true, retryUncertain: false, budgetUsd: .5 });
});

test('resume shortcut never grants uncertain retry automatically', async ({ page }) => {
    await page.route('**/api/tasks?*', route => route.fulfill({ json: pageData([{ ...task, message: 'Uncertain previous request', latest_job_state: 'running', can_resume: true, can_proofread: false }]) }));
    await page.goto('/#/manage');
    await page.getByRole('button', { name: 'Відновити', exact: true }).click();
    await expect(page.getByLabel('ID перекладу (job)')).toHaveValue('job-2');
    await expect(page.getByLabel('Я перевірив витрати')).not.toBeChecked();
    await expect(page.getByRole('button', { name: 'Додати в чергу' })).toBeDisabled();
});

test('newly received events update badge and show an in-site notification', async ({ page }) => {
    await page.clock.install();
    let arrived = false;
    await page.route('**/api/notifications?*', route => route.fulfill({ json: {
        latestId: arrived ? 4 : 0, unread: arrived ? 1 : 0,
        items: arrived ? [{ id: 4, kind: 'task_complete', novel_id: novel.id, novel_title: novel.title,
            task_id: task.id, chapter: null, entry_count: null, created_at: '2026-09-23T08:00:00Z', read: false }] : [],
    } }));
    await page.goto('/#/manage');
    await page.getByRole('button', { name: 'Сповіщення', exact: true }).click();
    await expect(page.getByText('Сповіщень поки немає.')).toBeVisible();
    await page.getByRole('button', { name: 'Закрити сповіщення' }).click();
    arrived = true;
    await page.clock.runFor(15000);
    await expect(page.getByRole('button', { name: 'Сповіщення: 1 непрочитаних', exact: true })).toBeVisible();
    await expect(page.locator('.notification-toast')).toContainText('Завдання успішно завершено');
    await page.getByRole('button', { name: 'Приховати сповіщення' }).click();
    await expect(page.locator('.notification-toast')).toHaveCount(0);
});

test('task notifications describe operation and chapters from the stored task', async ({ page }) => {
    const base = { novel_id: novel.id, novel_title: novel.title, chapter: null, entry_count: null, created_at: '2026-09-23T08:00:00Z', read: false };
    const items = [
        { ...base, id: 6, kind: 'task_complete', task_id: 't6', task_operation: 'proofread', task_first: 15, task_last: 15 },
        { ...base, id: 5, kind: 'task_complete', task_id: 't5', task_operation: 'import', task_first: 0, task_last: 0 },
        { ...base, id: 4, kind: 'task_interrupted', task_id: 't4', task_operation: 'resume', task_first: 0, task_last: 0, task_job_chapter: 3 },
        { ...base, id: 3, kind: 'task_complete', task_id: 't3' },
    ];
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items, unread: items.length, latestId: 6 } }));
    await page.goto('/#/manage');
    await page.getByRole('button', { name: 'Сповіщення: 4 непрочитаних', exact: true }).click();
    const panel = page.getByRole('region', { name: 'Сповіщення', exact: true });
    await expect(panel.getByText('Вичитку завершено: глава 15')).toBeVisible();
    await expect(panel.getByText('Імпорт завершено: лише опис новели')).toBeVisible();
    await expect(panel.getByText('Відновлення перекладу перервано після перезапуску сервера: глава 3')).toBeVisible();
    await expect(panel.getByText('Завдання успішно завершено')).toBeVisible();
});
