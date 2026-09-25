import { screen, waitFor } from '@testing-library/react';
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
