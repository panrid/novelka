import { expect, test, type Page } from '@playwright/test';
import { pageData } from './pageData';

const editor = { id: 'editor', username: 'editor', role: 'EDITOR' };
const novels = [{ id: 'n1', title: 'Водяний маг' }, { id: 'n2', title: 'Тінь міста' }];
const corrections = Array.from({ length: 30 }, (_, index) => ({
    id: 'c' + (index + 1), author_id: index % 3 ? 'reader' : 'writer', author: index % 3 ? 'reader' : 'writer',
    novel_id: novels[index % 2].id, novel_title: novels[index % 2].title, chapter: index % 4 + 1, chapter_title: 'Пролог',
    state: index === 0 ? 'pending' : index % 5 ? 'pending' : 'rejected', created_at: `2026-09-${String(index % 28 + 1).padStart(2, '0')}T10:00:00Z`,
    reviewed_at: null,
}));

async function workspace(page: Page, options: { failing?: () => boolean; empty?: boolean } = {}) {
    const requests: URLSearchParams[] = [];
    let approved = false;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: editor, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf-test', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/search?**', route => {
        const q = new URL(route.request().url()).searchParams.get('q') || '';
        return route.fulfill({ json: pageData(novels.filter(novel => novel.title.includes(q))) });
    });
    await page.route('**/api/corrections?**', route => {
        if (options.failing?.()) return route.fulfill({ status: 503, json: { message: 'Сервер недоступний' } });
        const params = new URL(route.request().url()).searchParams;
        requests.push(params);
        const q = params.get('q') || '';
        const size = Number(params.get('size')), current = Number(params.get('page'));
        const rows = options.empty ? [] : corrections.map(row => row.id === 'c1' && approved ? { ...row, state: 'approved' } : row)
            .filter(row => (!q || row.author.includes(q) || row.novel_title.includes(q))
                && (!params.get('state') || row.state === params.get('state'))
                && (!params.get('novel') || row.novel_id === params.get('novel')));
        const key = (params.get('sort') || 'created') as 'author' | 'created_at';
        rows.sort((a, b) => String(a[key === 'author' ? 'author' : 'created_at']).localeCompare(String(b[key === 'author' ? 'author' : 'created_at']))
            * (params.get('direction') === 'asc' ? 1 : -1) || a.id.localeCompare(b.id));
        return route.fulfill({ json: pageData(rows.slice((current - 1) * size, current * size), current, size, rows.length) });
    });
    await page.route('**/api/corrections/c1', route => route.fulfill({ json: {
        id: 'c1', author_id: 'writer', original: 'Він ішов повільно. Дощ не вщухав.', replacement: 'Він крокував повільно. Дощ не вщухав.',
        reason: 'Точніше дієслово', review_note: approved ? 'Погоджую' : null, base_revision: 3, published_revision: approved ? 4 : null,
        can_review: !approved,
    } }));
    await page.route('**/api/corrections/c2', route => route.fulfill({ json: {
        id: 'c2', author_id: 'editor', original: 'Було.', replacement: 'Стало.', reason: '', review_note: null,
        base_revision: 1, published_revision: null, can_review: false,
    } }));
    await page.route('**/api/corrections/c1/review', route => {
        expect(route.request().postDataJSON()).toEqual({ approve: true, note: 'Погоджую' });
        approved = true;
        return route.fulfill({ json: { message: 'Рішення збережено.' } });
    });
    return requests;
}

test('editor combines search, filters, sorting and pages in a restorable URL', async ({ page, isMobile }) => {
    const requests = await workspace(page);
    await page.goto('/#/corrections?queue=true');
    await expect(page.getByText('Сторінка 1 з 2 · 30 записів')).toBeVisible();
    await expect(page.getByText('Він ішов повільно')).toHaveCount(0);
    await page.getByRole('button', { name: 'Далі →' }).click();
    await expect(page).toHaveURL(/page=2/);
    if (isMobile) {
        await page.getByLabel('Порядок').selectOption('author');
        await page.getByRole('button', { name: 'Змінити напрямок сортування' }).click();
    } else {
        await page.getByRole('button', { name: 'Автор', exact: true }).click();
        await expect(page.getByRole('columnheader', { name: /Автор/ })).toHaveAttribute('aria-sort', 'ascending');
    }
    await expect(page).toHaveURL(/sort=author&direction=asc/);
    await expect(page).not.toHaveURL(/page=2/);
    await page.getByRole('searchbox', { name: 'Новела' }).fill('Тінь');
    await page.getByRole('button', { name: /Тінь міста/ }).click();
    await expect(page).toHaveURL(/novel=n2/);
    await page.getByRole('combobox', { name: 'Стан', exact: true }).click();
    await page.getByRole('option', { name: 'Відхилено' }).click();
    await page.getByRole('searchbox', { name: /Назва, глава, автор/ }).fill('writer');
    await expect(page.getByText('Сторінка 1 з 1 · 1 записів')).toBeVisible();
    const last = requests.at(-1)!;
    expect(Object.fromEntries(['q', 'novel', 'state', 'sort', 'direction', 'page'].map(key => [key, last.get(key)])))
        .toEqual({ q: 'writer', novel: 'n2', state: 'rejected', sort: 'author', direction: 'asc', page: '1' });
    await page.reload();
    await expect(page.getByText('Сторінка 1 з 1 · 1 записів')).toBeVisible();
    await expect(page.locator('.correction-selected')).toContainText('Тінь міста');
    await page.getByRole('searchbox', { name: /Назва, глава, автор/ }).fill('nobody');
    await expect(page.getByText('За заданими параметрами нічого не знайдено.')).toBeVisible();
    await page.getByRole('button', { name: 'Очистити пошук і фільтри' }).first().click();
    await expect(page.getByText('Сторінка 1 з 2 · 30 записів')).toBeVisible();
    await expect(page).toHaveURL(/queue=true/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('diff opens on demand with marked removals and additions, then editor approves', async ({ page }, testInfo) => {
    await workspace(page);
    await page.goto('/#/corrections?queue=true&sort=created&direction=asc');
    const row = page.locator('tbody').first();
    await expect(page.locator('.text-diff')).toHaveCount(0);
    await row.getByRole('button', { name: 'Показати diff' }).click();
    const diff = page.locator('.text-diff');
    await expect(diff.locator('del')).toHaveText('ішов');
    await expect(diff.locator('ins')).toHaveText('крокував');
    await expect(diff.locator('.diff-line.context')).toContainText('Дощ не вщухав.');
    await expect(page.getByText('ревізії 3')).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('corrections-diff.png'), fullPage: true });
    await page.getByLabel('Коментар рішення').fill('Погоджую');
    await page.getByRole('button', { name: 'Погодити й опублікувати' }).click();
    await expect(page.getByText('Опубліковано як ревізію 4.')).toBeVisible();
    await expect(row.getByText('Погоджено', { exact: true })).toBeVisible();
    await row.getByRole('button', { name: 'Сховати diff' }).click();
    await expect(diff).toHaveCount(0);
});

test('distinguishes empty queue from API failure and retries', async ({ page }) => {
    let failing = true;
    await workspace(page, { empty: true, failing: () => failing });
    await page.goto('/#/corrections?queue=true');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText('Правок поки немає.')).toHaveCount(0);
    failing = false;
    await page.getByRole('button', { name: 'Спробувати ще раз' }).click();
    await expect(page.getByText('Правок поки немає.')).toBeVisible();
});

test('own correction shows who must review it when backend denies self-review', async ({ page }) => {
    await workspace(page);
    await page.route('**/api/corrections?**', route => route.fulfill({ json: pageData([{
        id: 'c2', author_id: 'editor', author: 'editor', novel_id: 'n1', novel_title: 'Водяний маг', chapter: 1, chapter_title: 'Пролог',
        state: 'pending', created_at: '2026-09-01T10:00:00Z', reviewed_at: null,
    }]) }));
    await page.goto('/#/corrections?queue=true');
    await page.getByRole('button', { name: 'Показати diff' }).click();
    await expect(page.getByText('Вашу правку має перевірити інший редактор.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Погодити й опублікувати' })).toHaveCount(0);
});
