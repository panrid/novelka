import { expect, test, type Page } from '@playwright/test';
import { pageData } from './pageData';

const reader = { id: 'reader', username: 'reader', role: 'READER' };
const owner = { id: 'owner', username: 'owner', role: 'OWNER' };
const novel = { id: 'n0022gd', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 1, aliases: [] };

async function session(page: Page, user: typeof reader | null) {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf-test', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/novels', route => route.fulfill({ json: [novel] }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([novel]) }));
}

test('login uses csrf and exposes only reader navigation', async ({ page }) => {
    await session(page, null);
    await page.route('**/api/auth/login', async route => {
        expect(route.request().headers()['x-csrf-token']).toBe('csrf-test');
        expect(route.request().postData()).toContain('username=reader');
        await page.route('**/api/auth/me', request => request.fulfill({ json: { user: reader, registrationOpen: true } }));
        await route.fulfill({ status: 204 });
    });
    await page.goto('/#/login');
    await page.getByLabel('Логін', { exact: true }).fill('reader');
    await page.getByLabel('Пароль', { exact: true }).fill('example-password');
    await page.getByRole('button', { name: 'Увійти', exact: true }).click();
    await expect(page.getByRole('link', { name: 'Правки', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Майстерня' })).toHaveCount(0);
    await page.goto('/#/manage');
    await expect(page.getByRole('heading', { name: 'Потрібен доступ' })).toBeVisible();
});

test('reader submits a revision-bound private correction without rendering html', async ({ page }) => {
    await session(page, reader);
    await page.route('**/api/novels/n0022gd', route => route.fulfill({ json: { ...novel, chapters: [{ number: 1, title: 'Пролог', revision: 1 }] } }));
    await page.route('**/api/novels/n0022gd/chapters/1', route => route.fulfill({ json: {
        novelId: novel.id, number: 1, revision: 1, jobId: 'job1', personalReplacements: {}, title: 'Пролог',
        blocks: [{ id: 'title', kind: 'heading', text: 'Пролог' }, { id: 'p1', kind: 'paragraph', text: 'Він ішов.' }],
    } }));
    await page.route('**/api/corrections', route => {
        expect(route.request().postDataJSON()).toMatchObject({ baseJobId: 'job1', blockIndex: 1, original: 'Він ішов.', replacement: 'Він крокував. <b>Текст</b>' });
        return route.fulfill({ json: { id: 'correction1' } });
    });
    await page.goto('/#/novels/n0022gd/chapters/1');
    await expect(page.getByRole('button', { name: 'Запропонувати правку' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Режим правок' }).click();
    await expect(page.getByRole('button', { name: 'Запропонувати правку' })).toHaveCount(2);
    await page.getByRole('button', { name: 'Режим правок' }).click();
    await page.locator('.reading-text .editable-content').evaluate(element => {
        const range = document.createRange();
        range.selectNodeContents(element);
        window.getSelection()?.removeAllRanges();
        window.getSelection()?.addRange(range);
    });
    await expect(page.getByRole('button', { name: 'Запропонувати правку' })).toHaveCount(1);
    await page.getByRole('button', { name: 'Запропонувати правку' }).click();
    await expect(page.locator('.correction-form blockquote')).toHaveText('Він ішов.');
    await page.getByLabel('Виправлений абзац').fill('Він крокував. <b>Текст</b>');
    await page.getByRole('button', { name: 'Надіслати', exact: true }).click();
    await expect(page.getByText('Ваша версія · очікує перевірки')).toBeVisible();
    await expect(page.getByText('Він крокував. <b>Текст</b>', { exact: true })).toBeVisible();
    await expect(page.locator('.reading-text b')).toHaveCount(0);
});

test('owner launches budgeted translation and sees persisted queue', async ({ page }, testInfo) => {
    await session(page, owner);
    const tasks: object[] = [];
    await page.route('**/api/tasks**', route => {
        if (route.request().method() === 'POST') {
            expect(route.request().postDataJSON()).toMatchObject({ operation: 'translate', novelId: novel.id, first: 1, last: 10, budgetUsd: .5, dictionarySearchLimit: 12 });
            tasks.push({ id: 'task1', novel_id: novel.id, operation: 'translate', state: 'queued', spent_usd: 0, username: owner.username });
            return route.fulfill({ json: { id: 'task1' } });
        }
        return route.fulfill({ json: pageData(tasks) });
    });
    await page.goto('/#/manage');
    await page.getByRole('combobox', { name: 'Новела', exact: true }).click();
    await page.getByRole('option', { name: 'Водяний маг · n0022gd', exact: true }).click();
    await page.getByLabel('Остання глава').fill('10');
    await page.getByLabel('Додатковий бюджет').fill('.5');
    await expect(page.getByRole('button', { name: 'Додати в чергу' })).toBeDisabled();
    await page.getByLabel('Дозволяю платні запити').check();
    await page.getByText('Додаткові опції словника', { exact: true }).click();
    await page.getByLabel('Ліміт звернень до словника', { exact: true }).fill('12');
    await expect(page.getByRole('button', { name: 'Додати в чергу' })).toBeDisabled();
    await page.getByLabel('Дозволяю платні запити').check();
    await page.getByRole('button', { name: 'Додати в чергу' }).click();
    await expect(page.getByText('У черзі', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Налаштування', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
});

test('owner edits reader metadata and sees an explained legacy task failure', async ({ page }) => {
    await session(page, owner);
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([{
        id: 'failed-task', novel_id: novel.id, operation: 'translate', state: 'failed', spent_usd: .02,
        username: owner.username, current_job_id: 'job-failed', message: 'Dictionary tool limit exceeded',
    }]) }));
    await page.route('**/api/manage/n0022gd', route => {
        if (route.request().method() === 'POST') {
            expect(route.request().postDataJSON()).toEqual({
                titleUk: 'Водяний маг', authorUk: 'Кубо Тадаші', descriptionUk: 'Пригода мага води.',
            });
            return route.fulfill({ json: { message: 'Збережено' } });
        }
        return route.fulfill({ json: {
            novel: { id: novel.id, title: '水属性の魔法使い', titleUk: null, author: '久宝忠', authorUk: null, descriptionUk: null, chapterCount: 10 },
            aliases: [], chapters: [], jobs: [], glossary: { revision: 0, entries: [] }, proposals: [],
        } });
    });
    await page.goto('/#/manage');
    await expect(page.getByText('Попередній запуск зупинився на ліміті пошуків')).toBeVisible();
    await page.getByRole('article', { name: 'Переклад n0022gd' }).getByText('Подробиці').click();
    await expect(page.getByText('Раніше запит понад 6 пошуків у словнику', { exact: false })).toBeVisible();
    await page.getByRole('combobox', { name: 'Новела', exact: true }).click();
    await page.getByRole('option', { name: 'Водяний маг · n0022gd', exact: true }).click();
    await page.getByRole('button', { name: 'Дані новели', exact: true }).click();
    await page.getByLabel('Українська назва').fill('Водяний маг');
    await page.getByLabel('Автор українською').fill('Кубо Тадаші');
    await page.getByLabel('Опис українською').fill('Пригода мага води.');
    await page.getByRole('button', { name: 'Зберегти дані' }).click();
});

test('owner sees account balance and a web action for a changed dictionary', async ({ page }) => {
    await session(page, owner);
    await page.route('**/api/settings/openrouter-credits', route => route.fulfill({ json: {
        configured: true, totalCredits: 100.5, totalUsage: 25.75, remainingUsd: 74.75,
    } }));
    await page.route('**/api/settings', route => route.fulfill({ json: {
        revision: 0, registrationOpen: true, segmentChars: 1500, targetUsdPer5000: .1, maxBudgetUsd: 5,
        stages: ['analyze', 'translate', 'proofread'].map(stage => ({ stage, model: 'openai/gpt-4o-mini', inputUsdM: .15, outputUsdM: .6 })),
    } }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([{
        id: 'task2', novel_id: novel.id, operation: 'translate', state: 'failed', spent_usd: .02,
        username: owner.username, current_job_id: 'job2', message: 'Dictionary changed; run proofread or translate --force',
        current_chapter: 2, request: { first: 2, last: 2 },
    }]) }));
    await page.goto('/#/settings');
    await expect(page.getByText('74,75')).toBeVisible();
    await page.goto('/#/manage');
    const task = page.getByRole('article', { name: 'Переклад n0022gd' });
    await expect(task.getByText('Глава 2')).toBeVisible();
    await expect(task.getByText('Потрібна нова ревізія перекладу')).toBeVisible();
    await task.getByText('Подробиці').click();
    await expect(task.getByText('Повторно вичитати одну главу', { exact: false })).toBeVisible();
    await expect(task.getByText('job2')).toBeVisible();
});
