import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const EDITOR = {
    number: 3, title: 'Нічний ринок', revisionId: 41, published: true, draft: null, role: 'translator', mayAddPictures: true,
    previous: 2, next: null,
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Ліхтарі спалахнули.', marks: [] }], imageId: null, imageUrl: null }],
};

afterEach(() => vi.unstubAllGlobals());

describe('chapter editor', () => {
    it('keeps a draft on its own and publishes against the version it opened', async () => {
        const calls: { path: string; body: unknown }[] = [];
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'PUT /api/studio/editions/7/chapters/3/draft': (body) => { calls.push({ path: 'draft', body }); return { status: 204 }; },
            'POST /api/studio/editions/7/chapters/3/publish': (body) => { calls.push({ path: 'publish', body }); return { body: { revisionId: 42 } }; },
        });

        const title = await screen.findByLabelText('Назва глави');
        await userEvent.clear(title);
        await userEvent.type(title, 'Ринок уночі');

        await waitFor(() => expect(screen.getByText('чернетку збережено')).toBeInTheDocument(), { timeout: 3000 });
        expect(calls.find((call) => call.path === 'draft')?.body).toMatchObject({ title: 'Ринок уночі', baseRevisionId: 41 });

        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        expect(await screen.findByText('Опубліковано. Читачі вже бачать нову версію.')).toBeInTheDocument();
        expect(calls.find((call) => call.path === 'publish')?.body).toMatchObject({
            title: 'Ринок уночі', baseRevisionId: 41,
            blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Ліхтарі спалахнули.', marks: [] }] }],
        });
    });

    it('shows the team the original of the paragraph next to its translation', async () => {
        localStorage.removeItem('novelka:editor-original');
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: true } },
            'GET /api/studio/editions/7/chapters/3/original': { body: { title: '第3話', blocks: [{ id: 'b1', type: 'paragraph', text: '灯りがともった。' }] } },
        });

        await userEvent.click(await screen.findByRole('button', { name: 'Оригінал' }));
        expect(await screen.findByText(/Поставте курсор в абзац/)).toBeInTheDocument();
        // jsdom does not place a caret on click: put it into the paragraph as the browser would.
        const paragraph = screen.getByText('Ліхтарі спалахнули.');
        screen.getByRole('textbox', { name: 'Текст глави' }).focus();
        const caret = document.createRange();
        caret.setStart(paragraph.firstChild!, 3);
        caret.collapse(true);
        window.getSelection()!.removeAllRanges();
        window.getSelection()!.addRange(caret);
        document.dispatchEvent(new Event('selectionchange'));
        expect(await screen.findByText('灯りがともった。')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Оригінал' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('puts the whole original beside the text on a wide screen and lights the paragraph', async () => {
        localStorage.removeItem('novelka:editor-original');
        vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('1024'), addEventListener: () => {}, removeEventListener: () => {} }));
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: true } },
            'GET /api/studio/editions/7/chapters/3/original': { body: { title: '第3話', blocks: [{ id: 'b1', type: 'paragraph', text: '灯りがともった。' }] } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Оригінал' }));
        const column = await screen.findByRole('complementary', { name: 'Оригінал глави' });
        expect(await within(column).findByText('灯りがともった。')).toBeInTheDocument();
        expect(within(column).getByText('第3話')).toBeInTheDocument();
        expect(screen.queryByRole('complementary', { name: 'Оригінал абзацу' })).not.toBeInTheDocument();
    });

    it('has no original to show for a chapter written by hand', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, hasOriginal: false } },
        });
        await screen.findByLabelText('Назва глави');
        expect(screen.queryByRole('button', { name: 'Оригінал' })).not.toBeInTheDocument();
    });

    it('explains when a colleague published first and keeps the text', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/publish': {
                status: 409, body: { detail: 'Поки ви редагували, главу оновив хтось інший.', reason: 'chapter-changed' },
            },
        });

        expect(await screen.findByRole('button', { name: 'Опублікувати' })).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Назва глави'), '!');
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('главу оновив хтось інший');
        expect(screen.getByRole('button', { name: 'Відкрити главу знову' })).toBeInTheDocument();
        expect(screen.getByLabelText('Назва глави')).toHaveValue('Нічний ринок!');
    });
});

describe('new publication', () => {
    it('creates an own translation and opens it in the Studio', async () => {
        const { router, calls } = await renderAt('/studio/new', {
            'GET /api/me': { body: ME },
            'GET /api/me/teams': { body: [{ handle: 'mika', name: 'mika', role: 'owner' }] },
            'POST /api/studio/editions': { status: 201, body: { editionId: 12, novelSlug: 'sto-nochei' } },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'sto-nochei', title: 'Сто ночей', author: '', description: [], tags: [], kind: 'human', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: true, teamHandle: 'mika', teamName: 'mika', role: 'owner' } },
            'GET /api/studio/editions/12/chapters': { body: [] },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });

        await userEvent.type(await screen.findByLabelText('Назва'), 'Сто ночей');
        await userEvent.type(screen.getByLabelText('Теги'), 'фентезі, затишне');
        await userEvent.click(screen.getByRole('button', { name: 'Створити' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12'));
        expect(calls.find((call) => call.path === '/api/studio/editions')?.body).toMatchObject({
            kind: 'human', title: 'Сто ночей', tags: ['фентезі', 'затишне'], team: 'mika',
        });
        expect(await screen.findByRole('button', { name: 'Нова глава' })).toBeInTheDocument();
    });
});

describe('machine translation', () => {
    it('leads any translator of it to the autotranslation, not only the site owner', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 0, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner' } },
            'GET /api/studio/editions/12/chapters': { body: [] },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });

        expect(await screen.findByRole('link', { name: 'Автопереклад' })).toHaveAttribute('href', '/studio/12/translate');
        expect(screen.getByRole('link', { name: 'Словник' })).toBeInTheDocument();
    });
});

describe('studio side menu', () => {
    it('lists the translations and the open one\'s sections', async () => {
        await renderAt('/studio/12', {
            'GET /api/me': { body: ME },
            'GET /api/studio': { body: [
                { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', coverUrl: null, kind: 'machine', status: 'ongoing', chapterCount: 2,
                    teamHandle: 'mika', teamName: 'mika', role: 'owner', drafts: 0, pendingSuggestions: 3 },
                { editionId: 14, novelSlug: 'sto-nochei', title: 'Сто ночей', coverUrl: null, kind: 'human', status: 'ongoing', chapterCount: 1,
                    teamHandle: 'mika', teamName: 'mika', role: 'owner', drafts: 0, pendingSuggestions: 0 },
            ] },
            'GET /api/studio/editions/12': { body: { editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner' } },
            'GET /api/studio/editions/12/chapters': { body: [] },
            'GET /api/studio/editions/12/contributions': { body: [] },
        });
        const rail = await screen.findByRole('navigation', { name: 'Студія' });
        expect(await within(rail).findByRole('link', { name: /Сто ночей/ })).toHaveAttribute('href', '/studio/14');
        expect(within(rail).getByRole('link', { name: /Лиходійка/ })).toHaveTextContent('3');
        expect(within(rail).getByRole('link', { name: 'Словник' })).toHaveAttribute('href', '/studio/12/glossary');
        expect(within(rail).getByRole('link', { name: 'Глави' })).toHaveAttribute('href', '/studio/12');
    });
});

describe('new chapter', () => {
    const EDITION = (kind: string) => ({ editionId: 12, novelSlug: 'lykhodiika', title: 'Лиходійка', author: '', description: [], tags: [], kind, status: 'ongoing', adult: false, coverUrl: null, chapterCount: 2, ownNovel: false, teamHandle: 'mika', teamName: 'mika', role: 'owner' });
    const base = (kind: string) => ({
        'GET /api/me': { body: ME },
        'GET /api/studio/editions/12': { body: EDITION(kind) },
        'GET /api/studio/editions/12/chapters': { body: [] },
        'GET /api/studio/editions/12/contributions': { body: [] },
    });

    it('offers the autotranslation of the next chapters first, with their price', async () => {
        const { calls, router } = await renderAt('/studio/12', {
            ...base('machine'),
            'GET /api/studio/editions/12/autotranslate': { body: {
                configured: true, showShah: true, sourceChapters: 10, nextNumber: 3, publishedChapters: 2, lastAnalyzed: 2, nextToAnalyze: 3,
                averageChars: 5000, balance: null, usdPerShah: 0.07, settings: {}, jobs: [], personal: true, reserved: 0,
            } },
            'POST /api/studio/editions/12/autotranslate/quote': { body: {
                kind: 'translate', from: 3, to: 5, chapters: 3, skipped: 0, shah: 3, usd: 0.2, expectedUsd: 0.1, estimated: true, unanalyzed: 3,
            } },
            'POST /api/studio/editions/12/autotranslate/jobs': { status: 201, body: {} },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.getByRole('radio', { name: /Автопереклад/ })).toHaveAttribute('aria-checked', 'true');
        await userEvent.type(await screen.findByLabelText('Перекласти з глави 3 до глави…'), '5');
        await userEvent.click(await screen.findByRole('button', { name: 'Перекласти глави 3–5' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12/translate'));
        expect(calls.find((call) => call.path.endsWith('/autotranslate/jobs'))?.body).toEqual({ kind: 'translate', to: 5 });
    });

    it('opens the editor when the chapter is written by hand', async () => {
        const { router } = await renderAt('/studio/12', {
            ...base('human'),
            'POST /api/studio/editions/12/chapters': { status: 201, body: { number: 3 } },
            'GET /api/studio/editions/12/chapters/3': { body: { ...EDITOR, number: 3 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Нова глава' }));
        expect(screen.queryByRole('radio', { name: /Автопереклад/ })).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: /з файлу/ })).toHaveAttribute('href', '/studio/12/import');
        await userEvent.click(screen.getByRole('button', { name: 'Відкрити редактор' }));
        await waitFor(() => expect(router.state.location.pathname).toBe('/studio/12/chapters/3'));
    });
});

describe('team page', () => {
    const TEAM = {
        handle: 'kitsune', name: 'Кіцуне', personal: false,
        members: [{ nick: 'mika', avatarUrl: null, role: 'owner' }, { nick: 'oleh', avatarUrl: null, role: 'editor' }],
        editions: [], viewerRole: 'owner',
    };

    it('lets the owner manage members, and shows only names to others', async () => {
        await renderAt('/team/kitsune', { 'GET /api/me': { body: ME }, 'GET /api/teams/kitsune': { body: TEAM } });
        expect(await screen.findByLabelText('Роль oleh')).toHaveValue('editor');
        expect(screen.getByLabelText('Додати людину за ніком')).toBeInTheDocument();
    });

    it('hides the controls from visitors', async () => {
        await renderAt('/team/kitsune', { 'GET /api/teams/kitsune': { body: { ...TEAM, viewerRole: null } } });
        expect(await screen.findByText('редактор')).toBeInTheDocument();
        expect(screen.queryByLabelText('Додати людину за ніком')).not.toBeInTheDocument();
    });
});
