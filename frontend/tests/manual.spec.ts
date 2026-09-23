import { expect, test } from '@playwright/test';
import { pageData } from './pageData';

test('owner creates a manual novel and publishes a chapter from a private draft', async ({ page }) => {
    const calls: string[] = [];
    let created: unknown;
    let drafts: { chapter: number; title: string; updated_at: string; updated_by: string }[] = [];
    let published: { chapter: number; title: string; revision: number }[] = [];
    let draft: { title: string; text: string } | null = null;
    await page.route('**/api/auth/me', route => route.fulfill({ json: { user: { id: 'owner', username: 'owner', role: 'OWNER' }, registrationOpen: true } }));
    await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }));
    await page.route('**/api/notifications?*', route => route.fulfill({ json: { items: [], unread: 0, latestId: 0, nextCursor: 0 } }));
    await page.route('**/api/novels/search?*', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/tasks**', route => route.fulfill({ json: pageData([]) }));
    await page.route('**/api/tags?*', route => route.fulfill({ json: { items: [] } }));
    await page.route('**/api/manage/novels', route => { created = route.request().postDataJSON(); return route.fulfill({ json: { id: 'm123' } }); });
    await page.route('**/api/manage/m123', route => route.fulfill({ json: {
        novel: { id: 'm123', title: 'Власний переклад', titleUk: 'Власний переклад', author: '', authorUk: null, descriptionUk: null, chapterCount: published.length, url: 'manual:m123' },
        aliases: [], importedChapters: published.length, glossary: { revision: 0, entries: [] }, proposals: [], tags: [], aiTranslated: false,
    } }));
    await page.route('**/api/manage/m123/manual', route => route.fulfill({ json: { drafts, published } }));
    await page.route('**/api/manage/m123/manual/1', route => {
        const method = route.request().method();
        calls.push(method + ' 1');
        if (method === 'POST') { draft = route.request().postDataJSON(); drafts = [{ chapter: 1, title: draft!.title, updated_at: '2026-09-24T10:00:00Z', updated_by: 'owner' }]; return route.fulfill({ json: { message: 'ok' } }); }
        if (method === 'DELETE') { draft = null; drafts = []; return route.fulfill({ json: { message: 'ok' } }); }
        return route.fulfill({ json: { draft, published: published.length ? { title: 'Початок', text: 'Абзац.', revision: published[0].revision } : null } });
    });
    await page.route('**/api/manage/m123/manual/1/publish', route => {
        calls.push('publish 1');
        published = [{ chapter: 1, title: draft!.title, revision: 1 }]; draft = null; drafts = [];
        return route.fulfill({ json: { message: 'Главу опубліковано як ревізію 1.', revision: 1 } });
    });
    await page.goto('/#/manage');
    await page.getByRole('navigation', { name: 'Керування перекладами' }).getByRole('button', { name: 'Дані новели' }).click();
    const form = page.getByRole('region', { name: /Створити новелу вручну/ });
    await form.getByLabel('Українська назва').fill('Власний переклад');
    await form.getByLabel('Це машинний переклад').check();
    await form.getByRole('button', { name: 'Створити новелу' }).click();
    await expect.poll(() => created).toMatchObject({ titleUk: 'Власний переклад', tags: ['Машинний переклад'] });
    await page.getByRole('button', { name: 'Глави й експорт' }).click();
    await expect(page.getByText('Вставити оригінальний текст вручну')).toHaveCount(0);
    await page.getByRole('button', { name: 'Нова глава 1' }).click();
    await page.getByLabel('Назва глави').fill('Початок');
    await page.getByLabel('Текст перекладу').fill('Абзац.');
    await page.getByRole('button', { name: 'Опублікувати' }).click();
    await expect(page.getByText('Главу опубліковано. Читачі вже бачать нову ревізію.')).toBeVisible();
    expect(calls).toEqual(['GET 1', 'POST 1', 'publish 1', 'GET 1']);
    await expect(page.getByText('опублікована ревізія 1; зміни стануть новою ревізією', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Зберегти чернетку' }).click();
    await expect(page.getByRole('button', { name: '1. Початок' }).last()).toBeVisible();
    await page.getByRole('button', { name: '1. Початок' }).last().click();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Видалити чернетку' }).click();
    await expect(page.getByText('Чернетку видалено.')).toBeVisible();
    expect(calls).toContain('DELETE 1');
});
