import { expect, test, type Page } from '@playwright/test';
import { pageData } from './pageData';

const reader = { id: 'reader', username: 'reader', role: 'READER' };
const owner = { id: 'owner', username: 'owner', role: 'OWNER' };
const novel = { id: 'n0022gd', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 1, aliases: [], tags: [] };

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
    await page.getByLabel('Email або нік', { exact: true }).fill('reader');
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
    await page.getByRole('button', { name: 'Зберегти правку' }).click();
    await expect(page.getByText('Ваша чернетка · ще не надіслана')).toBeVisible();
    await expect(page.getByText('Він крокував. <b>Текст</b>', { exact: true })).toBeVisible();
    await expect(page.locator('.reading-text b')).toHaveCount(0);
    await page.route('**/api/corrections/submit', route => {
        expect(route.request().postDataJSON()).toMatchObject({ novelId: novel.id });
        return route.fulfill({ json: { batchId: 'batch1', count: 1 } });
    });
    const bar = page.getByRole('region', { name: 'Неподані правки' });
    await expect(bar).toContainText('Неподаних правок: 1');
    await page.locator('body').click({ position: { x: 5, y: 5 } });
    await page.keyboard.press('ControlOrMeta+Enter');
    await expect(bar).toContainText('Правки надіслано редакторам.');
    await expect(page.getByText('Ваша версія · очікує перевірки')).toBeVisible();
});

test('reader edits, withdraws and replaces every occurrence as drafts', async ({ page }) => {
    await session(page, reader);
    await page.route('**/api/novels/n0022gd', route => route.fulfill({ json: { ...novel, chapters: [{ number: 1, title: 'Пролог', revision: 1 }] } }));
    await page.route('**/api/novels/n0022gd/chapters/1', route => route.fulfill({ json: {
        novelId: novel.id, number: 1, revision: 1, jobId: 'job1', title: 'Пролог', draftCount: 1,
        personalReplacements: { 1: 'Він біг.' }, personalStates: { 1: 'draft' }, personalIds: { 1: 'c1' },
        blocks: [{ id: 'title', kind: 'heading', text: 'Пролог' }, { id: 'p1', kind: 'paragraph', text: 'Він ішов. Ліна мовчала.' }],
    } }));
    const edits: unknown[] = [];
    await page.route('**/api/corrections', route => { edits.push(route.request().postDataJSON()); return route.fulfill({ json: { id: 'c1' } }); });
    await page.route('**/api/corrections/c1', route => {
        expect(route.request().method()).toBe('DELETE');
        return route.fulfill({ status: 204 });
    });
    await page.route('**/api/corrections/replace-preview**', route => route.fulfill({ json: { total: 3, chapters: [{ chapter: 1, count: 1 }, { chapter: 2, count: 2 }] } }));
    await page.route('**/api/corrections/replace', route => {
        expect(route.request().postDataJSON()).toMatchObject({ find: 'Ліна', replacement: 'Ріна', scope: 'novel', baseJobId: 'job1' });
        return route.fulfill({ json: { id: 'r1' } });
    });
    await page.goto('/#/novels/n0022gd/chapters/1');
    await expect(page.getByText('Ваша чернетка · ще не надіслана')).toBeVisible();
    await page.getByRole('button', { name: 'Змінити', exact: true }).click();
    await expect(page.getByLabel('Виправлений абзац')).toHaveValue('Він біг.');
    await page.getByLabel('Виправлений абзац').fill('Він побіг.');
    await page.getByRole('button', { name: 'Зберегти правку' }).click();
    await expect(page.getByText('Він побіг.', { exact: true })).toBeVisible();
    await expect.poll(() => edits.length).toBe(1);
    await expect(page.getByRole('region', { name: 'Неподані правки' })).toContainText('Неподаних правок: 1');
    await page.getByRole('button', { name: 'Відкликати' }).click();
    await expect(page.getByText('Він ішов. Ліна мовчала.', { exact: true })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Неподані правки' })).toHaveCount(0);

    await page.locator('.reading-text .editable-content p').evaluate(element => {
        const text = element.firstChild!;
        const start = text.textContent!.indexOf('Ліна');
        const range = document.createRange();
        range.setStart(text, start);
        range.setEnd(text, start + 4);
        window.getSelection()?.removeAllRanges();
        window.getSelection()?.addRange(range);
    });
    await page.getByRole('button', { name: 'Замінити всі входження' }).click();
    await expect(page.getByLabel('Замінити')).toHaveValue('Ліна');
    await page.getByLabel('На', { exact: true }).fill('Ріна');
    await page.getByLabel('В усіх опублікованих главах новели').check();
    await expect(page.getByText('Знайдено 3 входжень у 2 главах.')).toBeVisible();
    await page.getByRole('button', { name: 'Зберегти заміну' }).click();
    await expect(page.getByRole('region', { name: 'Неподані правки' })).toContainText('Неподаних правок: 1');
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
    await page.getByText('Розширені параметри', { exact: true }).click();
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
            aliases: [], chapters: [], jobs: [], glossary: { revision: 0, entries: [] }, proposals: [], tags: [], aiTranslated: false,
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
        stages: ['analyze', 'translate', 'proofread'].map(stage => ({ stage, model: 'openai/gpt-4o-mini', inputUsdM: .15, outputUsdM: .6, catalogPricing: false })),
        adminSelfApproval: false,
    } }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([{
        id: 'task2', novel_id: novel.id, operation: 'translate', state: 'failed', spent_usd: .02,
        username: owner.username, current_job_id: 'job2', message: 'Dictionary changed; run proofread or translate --force',
        current_chapter: 2, request: { first: 2, last: 2 },
    }]) }));
    await page.goto('/#/settings?section=ai');
    await expect(page.getByText('74,75')).toBeVisible();
    await page.goto('/#/manage');
    const task = page.getByRole('article', { name: 'Переклад n0022gd' });
    await expect(task.getByText('Глава 2')).toBeVisible();
    await expect(task.getByText('Потрібна нова ревізія перекладу')).toBeVisible();
    await task.getByText('Подробиці').click();
    await expect(task.getByText('Повторно вичитати одну главу', { exact: false })).toBeVisible();
    await expect(task.getByText('job2')).toBeVisible();
});

test('settings are grouped into categories that keep unsaved edits and save together', async ({ page }) => {
    await session(page, owner);
    let saved: Record<string, unknown> | undefined;
    const settings = {
        revision: 3, registrationOpen: true, segmentChars: 1500, targetUsdPer5000: .1, maxBudgetUsd: 5,
        stages: ['analyze', 'translate', 'proofread'].map(stage => ({ stage, model: 'openai/gpt-4o-mini', inputUsdM: .15, outputUsdM: .6, catalogPricing: false })),
        adminSelfApproval: false,
    };
    await page.route('**/api/settings', route => {
        if (route.request().method() === 'POST') { saved = route.request().postDataJSON(); return route.fulfill({ json: { ...saved, revision: 4 } }); }
        return route.fulfill({ json: settings });
    });
    await page.goto('/#/settings');
    const nav = page.getByRole('navigation', { name: 'Розділи налаштувань' });
    await expect(nav.getByRole('link', { name: 'Загальні' })).toHaveAttribute('aria-current', 'page');
    await page.getByLabel('Дозволити реєстрацію нових читачів').uncheck();
    await nav.getByRole('link', { name: 'Редагування та погодження' }).click();
    await expect(page).toHaveURL(/section=editing/);
    await page.getByLabel('Адміністратор може погоджувати власні правки').check();
    await page.getByRole('button', { name: 'Зберегти налаштування' }).click();
    await expect(page.getByText('Налаштування збережено.')).toBeVisible();
    expect(saved).toMatchObject({ registrationOpen: false, adminSelfApproval: true, revision: 3 });
    await nav.getByRole('link', { name: 'Вигляд' }).click();
    await page.getByRole('radio', { name: 'Світла', exact: true }).last().check();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('AI settings suggest suitable catalog models and explain prices', async ({ page }) => {
    await session(page, owner);
    await page.route('**/api/settings/openrouter-credits', route => route.fulfill({ json: { configured: false, totalCredits: null, totalUsage: null, remainingUsd: null } }));
    let saved: { stages: { model: string; catalogPricing: boolean }[] } | undefined;
    await page.route('**/api/settings', route => {
        if (route.request().method() === 'POST') { saved = route.request().postDataJSON(); return route.fulfill({ json: { ...saved, revision: 2 } }); }
        return route.fulfill({ json: { revision: 1, registrationOpen: true, segmentChars: 1500, targetUsdPer5000: .1, maxBudgetUsd: 5,
            stages: ['analyze', 'translate', 'proofread'].map(stage => ({ stage, model: 'openai/gpt-4o-mini', inputUsdM: .15, outputUsdM: .6, catalogPricing: false })),
            adminSelfApproval: false } });
    });
    const catalog = (refreshedAt: string) => ({ provider: 'openrouter', refreshedAt, stale: false, error: null, items: [
        { id: 'openai/gpt-4o-mini', name: 'GPT-4o mini', contextLength: 128000, inputUsdM: .15, outputUsdM: .6, suitable: true, limitation: null },
        { id: 'vendor/cheap', name: 'Cheap', contextLength: 32000, inputUsdM: .05, outputUsdM: .1, suitable: true, limitation: null },
        { id: 'vendor/no-tools', name: 'No tools', contextLength: 8000, inputUsdM: .01, outputUsdM: .02, suitable: false, limitation: 'Немає інструментів для пошуку в словнику.' },
    ] });
    await page.route('**/api/models', route => route.fulfill({ json: catalog('2026-09-24T08:00:00Z') }));
    let refreshed = false;
    await page.route('**/api/models/refresh', route => { refreshed = true; return route.fulfill({ json: catalog('2026-09-24T09:30:00Z') }); });
    await page.goto('/#/settings?section=ai');
    await expect(page.getByText(/Каталог OpenRouter: 2 придатних моделей/)).toBeVisible();
    const translate = page.getByRole('group', { name: 'Переклад' });
    await expect(translate.locator('.model-info')).toContainText(/GPT-4o mini · контекст 128/);
    await translate.getByLabel('Модель').fill('vendor/no-tools');
    await expect(translate.locator('.model-info')).toHaveText('Не підходить для перекладу: Немає інструментів для пошуку в словнику.');
    await translate.getByLabel('Модель').fill('vendor/cheap');
    await translate.getByLabel('Брати ціну з каталогу провайдера').check();
    await expect(translate.getByText(/Діє ціна з каталогу: Cheap/)).toBeVisible();
    await expect(translate.getByLabel('Резервна ціна входу, $ / млн')).toHaveValue('0.15');
    const status = page.locator('.catalog-status > span');
    const before = await status.textContent();
    await page.getByRole('button', { name: 'Оновити список моделей' }).click();
    await expect(status).not.toHaveText(before!);
    expect(refreshed).toBe(true);
    await page.getByRole('button', { name: 'Зберегти налаштування' }).click();
    await expect(page.getByText('Налаштування збережено.')).toBeVisible();
    expect(saved!.stages[1]).toMatchObject({ model: 'vendor/cheap', catalogPricing: true });
    expect(saved!.stages[0]).toMatchObject({ model: 'openai/gpt-4o-mini', catalogPricing: false });
});

test('advanced options override the model only for one task', async ({ page }) => {
    await session(page, owner);
    let submitted: Record<string, unknown> | undefined;
    await page.route('**/api/tasks**', route => {
        if (route.request().method() === 'POST') { submitted = route.request().postDataJSON(); return route.fulfill({ json: { id: 'task9' } }); }
        return route.fulfill({ json: pageData([]) });
    });
    await page.route('**/api/tasks/defaults', route => route.fulfill({ json: { models: { analyze: 'openai/gpt-4o-mini', translate: 'openai/gpt-4o-mini', proofread: 'openai/gpt-4o-mini' }, maxBudgetUsd: 5 } }));
    await page.route('**/api/models', route => route.fulfill({ json: { provider: 'openrouter', refreshedAt: '2026-09-24T08:00:00Z', stale: false, error: null, items: [
        { id: 'vendor/strong', name: 'Strong', contextLength: 200000, inputUsdM: 3, outputUsdM: 15, suitable: true, limitation: null }] } }));
    await page.goto('/#/manage');
    await page.getByRole('combobox', { name: 'Новела', exact: true }).click();
    await page.getByRole('option', { name: 'Водяний маг · n0022gd', exact: true }).click();
    const advanced = page.locator('details.advanced-options');
    await expect(advanced).not.toHaveAttribute('open', '');
    await page.getByText('Розширені параметри', { exact: true }).click();
    await expect(advanced.getByText('Буде використано модель за замовчуванням: openai/gpt-4o-mini.')).toBeVisible();
    await advanced.getByLabel('Модель для цього завдання').fill('vendor/strong');
    await expect(advanced.locator('.model-info')).toContainText('Strong · контекст 200');
    await page.getByLabel('Додатковий бюджет').fill('.5');
    await page.getByLabel('Дозволяю платні запити').check();
    await page.getByRole('button', { name: 'Додати в чергу' }).click();
    await expect.poll(() => submitted).toMatchObject({ operation: 'translate', overrides: { model: 'vendor/strong' } });
});
