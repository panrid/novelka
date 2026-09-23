import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test('glossary shows grouped pending suggestions and persists dismissal', async ({ page }) => {
    const novel = { id: 'n0022gd', title: 'Водяний маг', author: 'Автор', chapterCount: 10, readyChapters: 1, aliases: [] };
    const entry = { key: 'person', japanese: '涼', ukrainian: 'Рьо', reading: '', aliases: [], kind: 'character', gender: 'male', facts: '', certainty: 'confirmed', sourceChapter: 1, manual: true };
    const proposals = [
        { id: 1, proposal: entry, status: 'in_dictionary', occurrences: 3 },
        { id: 2, proposal: { ...entry, key: 'alternative', ukrainian: 'Рьоу' }, canonicalKey: 'person', status: 'pending', occurrences: 2 },
    ];
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' } } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0 } }));
    await page.route('**/api/novels', route => route.fulfill({ json: [novel] }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([novel]) }));
    await page.route('**/api/manage/n0022gd', route => route.fulfill({ json: { novel, importedChapters: 0, aliases: [], glossary: { revision: 1, entries: [] } } }));
    await page.route('**/api/manage/n0022gd/glossary/entries?*', route => route.fulfill({ json: pageData([entry]) }));
    await page.route('**/api/manage/n0022gd/glossary/similar?*', route => route.fulfill({ json: [entry] }));
    await page.route('**/api/manage/n0022gd/proposals?*', route => route.fulfill({ json: pageData(proposals) }));
    await page.route('**/api/manage/n0022gd/proposals/2/dismiss', route => {
        expect(route.request().method()).toBe('POST');
        expect(route.request().headers()['x-csrf-token']).toBe('test');
        proposals[1].status = 'dismissed';
        return route.fulfill({ json: { message: 'Відхилено' } });
    });
    await page.goto('/#/manage?novel=n0022gd&tab=glossary');
    await page.getByText('Пропозиції ШІ: 2 груп', { exact: true }).click();
    await expect(page.locator('.suggestion-card')).toHaveCount(2);
    await expect(page.locator('.suggestion-list')).toContainText('На цій сторінці потребують перевірки: 1');
    await page.getByRole('button', { name: 'Відкрити у формі' }).click();
    await expect(page.getByLabel('Українською', { exact: true })).toHaveValue('Рьоу');
    await page.locator('.glossary-form').getByText('Додаткові дані', { exact: true }).click();
    await expect(page.getByLabel('Технічний ключ')).toHaveValue('person');
    await page.route('**/api/manage/n0022gd/glossary', async route => {
        await new Promise(resolve => setTimeout(resolve, 800));
        await route.fulfill({ status: 409, json: { message: 'Словник зайнятий перекладом.' } });
    });
    await page.getByRole('button', { name: 'Зберегти запис' }).click();
    await expect(page.getByRole('button', { name: 'Відхилити', exact: true })).toBeEnabled();
    await page.getByRole('button', { name: 'Відхилити', exact: true }).click();
    await expect(page.locator('.suggestion-list')).toContainText('На цій сторінці потребують перевірки: 0');
    await page.reload();
    await page.getByText('Пропозиції ШІ: 2 груп', { exact: true }).click();
    await expect(page.locator('.suggestion-card')).toHaveCount(2);
    await expect(page.locator('.suggestion-card').last()).toContainText('Відхилено');
    await expect(page.getByRole('button', { name: 'Відхилити', exact: true })).toHaveCount(0);
});
