import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

const fantasy = { name: 'Фентезі', slug: 'фентезі' };
const machine = { name: 'Машинний переклад', slug: 'машинний переклад' };
const novels = [
    { id: 'n1', title: 'Водяний маг', author: 'Автор', description: '', chapterCount: 10, readyChapters: 3, aliases: [], tags: [fantasy, machine] },
    { id: 'n2', title: 'Тінь міста', author: 'Автор', description: '', chapterCount: 5, readyChapters: 1, aliases: [], tags: [fantasy] },
];

test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/tags?*', route => route.fulfill({ json: { items: [{ ...fantasy, novels: 2 }, { ...machine, novels: 1 }] } }));
});

test('catalog filters by several tags together and keeps them in the URL', async ({ page }) => {
    const requests: string[][] = [];
    await page.route('**/api/novels/search?*', route => {
        const tags = new URL(route.request().url()).searchParams.getAll('tag');
        requests.push(tags);
        return route.fulfill({ json: pageData(novels.filter(novel => tags.every(tag => novel.tags.some(item => item.slug === tag)))) });
    });
    await page.goto('/');
    await expect(page.locator('.novel-card')).toHaveCount(2);
    await expect(page.locator('.novel-card').first().locator('.tag-chip')).toHaveText(['Фентезі', 'Машинний переклад']);
    await page.getByLabel('Тег', { exact: true }).fill('  ФЕНТЕЗІ ');
    await page.getByRole('button', { name: 'Фільтрувати' }).click();
    await expect(page).toHaveURL(/tags=/);
    await page.getByLabel('Тег', { exact: true }).fill('Машинний переклад');
    await page.getByLabel('Тег', { exact: true }).press('Enter');
    await expect(page.locator('.novel-card')).toHaveCount(1);
    expect(requests.at(-1)).toEqual(['фентезі', 'машинний переклад']);
    await page.reload();
    await expect(page.getByRole('group', { name: 'Вибрані теги' }).getByRole('button', { name: /Прибрати тег/ })).toHaveCount(2);
    await page.getByRole('button', { name: 'Прибрати тег машинний переклад' }).click();
    await expect(page.locator('.novel-card')).toHaveCount(2);
    await page.getByRole('button', { name: 'Очистити теги' }).click();
    await expect(page.getByRole('group', { name: 'Вибрані теги' })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('novel page links each tag to the filtered catalog', async ({ page }) => {
    await page.route('**/api/novels/n1', route => route.fulfill({ json: { ...novels[0], firstChapter: 1, resumeChapter: null } }));
    await page.route('**/api/novels/n1/contents?*', route => route.fulfill({ json: pageData([]) }));
    await page.goto('/#/novels/n1');
    await expect(page.getByRole('link', { name: 'Машинний переклад' })).toHaveAttribute('href', '#/?tags=' + encodeURIComponent('машинний переклад'));
});

test('workshop edits tags and suggests the machine translation tag for Novelka translations', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData(novels) }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/manage/n1', route => route.fulfill({ json: {
        novel: { id: 'n1', title: '物語', titleUk: 'Водяний маг', author: 'Автор', authorUk: null, descriptionUk: null, chapterCount: 10 },
        aliases: [], importedChapters: 3, glossary: { revision: 1, entries: [] }, proposals: [], tags: [fantasy], aiTranslated: true,
    } }));
    let saved: unknown;
    await page.route('**/api/manage/n1/tags', route => {
        saved = route.request().postDataJSON();
        return route.fulfill({ json: { tags: [fantasy, machine, { name: 'Магія', slug: 'магія' }] } });
    });
    await page.goto('/#/manage?novel=n1');
    await page.getByRole('navigation', { name: 'Керування перекладами' }).getByRole('button', { name: 'Дані новели' }).click();
    const editor = page.getByRole('region', { name: /Теги/ });
    await editor.getByRole('button', { name: 'Додати тег «Машинний переклад»' }).click();
    await expect(editor.getByRole('button', { name: 'Додати тег «Машинний переклад»' })).toHaveCount(0);
    await editor.getByLabel('Новий тег').fill('магія');
    await editor.getByRole('button', { name: 'Додати', exact: true }).click();
    await editor.getByLabel('Новий тег').fill('ФЕНТЕЗІ');
    await editor.getByRole('button', { name: 'Додати', exact: true }).click();
    await expect(editor.locator('.tag-chip')).toHaveCount(3);
    await editor.getByRole('button', { name: 'Зберегти теги' }).click();
    await expect(editor.getByText('Теги збережено.')).toBeVisible();
    expect(saved).toEqual({ tags: ['Фентезі', 'Машинний переклад', 'магія'] });
    await expect(editor.getByRole('button', { name: 'Прибрати тег Магія' })).toBeVisible();
});
