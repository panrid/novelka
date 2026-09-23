import { expect, test, type Page } from '@playwright/test';

const novel = {
    id: 'n0022gd', title: 'Водяний маг', author: 'Кубо Тадаші', chapterCount: 100,
    description: 'Історія про мага води та його нове життя.', readyChapters: 2, aliases: ['water'],
};
const chapters = [
    { number: 1, title: 'Пролог', revision: 1 },
    { number: 3, title: 'Інший світ', revision: 2 },
];

async function library(page: Page) {
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: null, registrationOpen: true } }));
    await page.route('**/api/novels**', async route => {
        const path = new URL(route.request().url()).pathname;
        let body;
        if (path === '/api/novels') body = [novel];
        else if (path === '/api/novels/n0022gd') body = { ...novel, chapters };
        else {
            const number = Number(path.split('/').pop());
            body = {
                novelId: novel.id, number, revision: 1,
                title: number === 1 ? 'Пролог' : 'Інший світ',
                blocks: [
                    { id: 'title', kind: 'heading', text: number === 1 ? 'Пролог' : 'Інший світ' },
                    { id: 'p1', kind: 'paragraph', text: 'Ранкове світло торкнулося води. Історія тільки починалася.' },
                    { id: 'p2', kind: 'paragraph', text: '<script>window.injected = true</script>' },
                    { id: 'hr', kind: 'separator', text: '' },
                    { id: 'note', kind: 'afterword', text: 'Післямова автора.' },
                ],
            };
        }
        await route.fulfill({ json: body });
    });
}

test('catalog search, contents and navigation follow available chapter numbers', async ({ page }) => {
    await library(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Каталог новел' })).toBeVisible();
    await page.getByRole('searchbox').fill('water');
    await page.getByRole('link').filter({ has: page.getByRole('heading', { name: novel.title }) }).click();
    await page.getByRole('link', { name: 'Почати читання' }).click();
    await expect(page.getByRole('heading', { name: 'Пролог', exact: true })).toHaveCount(1);
    await expect(page.getByText('Післямова автора.')).toBeVisible();
    await expect(page.getByText('<script>window.injected = true</script>')).toBeVisible();
    expect(await page.evaluate(() => 'injected' in window)).toBe(false);
    await page.getByRole('link', { name: 'Наступна глава' }).click();
    await expect(page).toHaveURL(/chapters\/3$/);
    await expect(page.getByRole('heading', { name: 'Інший світ', exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Попередня глава' }).click();
    await expect(page).toHaveURL(/chapters\/1$/);
});

test('reader preferences survive reload and resume uses the canonical novel id', async ({ page }, testInfo) => {
    await library(page);
    await page.goto('/#/novels/n0022gd/chapters/3');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.getByRole('button', { name: 'Збільшити текст' }).click();
    await page.locator('.reader-toolbar').getByRole('combobox', { name: 'Тема', exact: true }).click();
    await page.getByRole('option', { name: 'Світла', exact: true }).click();
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect(page.locator('article')).toHaveCSS('font-size', '22px');
    await page.locator('.site-header').getByRole('combobox', { name: 'Тема', exact: true }).click();
    await page.getByRole('option', { name: 'Чорна', exact: true }).click();
    await expect(page.locator('.reader-toolbar').getByRole('combobox', { name: 'Тема', exact: true })).toContainText('Чорна');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'black');
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#000000');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('black-theme.png') });
    await page.getByRole('link', { name: '← Зміст', exact: true }).click();
    await expect(page.getByRole('link', { name: 'Продовжити читання' })).toHaveAttribute('href', '#/novels/n0022gd/chapters/3');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'black');
});

test('empty catalog and no search results are distinct', async ({ page }) => {
    await library(page);
    await page.goto('/');
    await page.getByRole('searchbox').fill('невідома історія');
    await expect(page.getByRole('heading', { name: 'Історію не знайдено' })).toBeVisible();
    await page.unrouteAll({ behavior: 'wait' });
    await page.route('**/api/novels', route => route.fulfill({ json: [] }));
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Перша історія ще попереду' })).toBeVisible();
});

test('API errors offer a working retry and never leave stale text', async ({ page }) => {
    await page.route('**/api/novels', route => route.fulfill({ status: 503, json: {} }));
    await page.goto('/');
    await expect(page.getByRole('alert')).toBeVisible();
    await page.unrouteAll({ behavior: 'wait' });
    await library(page);
    await page.getByRole('button', { name: 'Спробувати ще раз' }).click();
    await expect(page.getByRole('heading', { name: novel.title })).toBeVisible();
    await page.route('**/api/novels/n0022gd/chapters/1', route => route.fulfill({ status: 404, json: {} }));
    await page.goto('/#/novels/n0022gd/chapters/1');
    await expect(page.getByRole('alert')).toContainText('ще недоступна');
});

test('readable-only filter hides untranslated novels', async ({ page }) => {
    await page.route('**/api/novels', route => route.fulfill({ json: [novel, {
        ...novel, id: 'untranslated', title: 'Нова історія', readyChapters: 0, aliases: [],
    }] }));
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Нова історія' })).toBeVisible();
    await page.getByRole('button', { name: 'Є готові глави' }).click();
    await expect(page.getByRole('heading', { name: 'Нова історія' })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: novel.title })).toBeVisible();
});

test('layout fits viewport and deep links load after refresh', async ({ page }, testInfo) => {
    await library(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: novel.title })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('catalog.png'), fullPage: true });
    await page.goto('/#/novels/n0022gd/chapters/1');
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Пролог', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('reader.png'), fullPage: true });
});
