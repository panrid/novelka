import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { studioApi, type DocumentDetails } from '../../studio/api';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'dmytro', email: 'author@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

const OVERVIEW = {
    editionId: 10, novelSlug: 'tvorets-mahii', title: 'Творець Магії', author: 'Дмитро', description: [], tags: [],
    kind: 'original', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 16, ownNovel: true,
    teamHandle: 'panrid', teamName: 'panrid', role: 'owner' as const, sourceChapters: null, pendingSuggestions: 0,
    drafts: 0, newWords: 0, originalUrl: null, pausedUntil: null, source: 'original', originals: 0,
    facts: { titleOriginal: null, titleEnglish: null, altTitles: [], sourceStatus: null, sourceChapterCount: 0 },
    downloadAllowed: true, visibility: 'private' as const,
};

const DOCS = [
    {
        id: 101,
        slug: 'stan-roboty',
        title: '00_Стан роботи',
        kind: 'status',
        version: 1,
        updatedAt: new Date().toISOString(),
        updatedByNick: 'dmytro',
        snippet: 'Поточна зупинка: розділ 16. Наступний крок — реткон мани.',
    },
    {
        id: 102,
        slug: 'zakony-mahii',
        title: 'Мана та Закони Магії',
        kind: 'canon',
        version: 2,
        updatedAt: new Date().toISOString(),
        updatedByNick: 'dmytro',
        snippet: 'Мана відчувається прохолодною.',
    },
];

const DOC_DETAILS = {
    id: 102,
    slug: 'zakony-mahii',
    title: 'Мана та Закони Магії',
    kind: 'canon',
    content: 'Мана відчувається прохолодною. Посилання на [[stan-roboty]].',
    version: 2,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    updatedByNick: 'dmytro',
    revisionsCount: 2,
    links: ['stan-roboty'],
    attachments: [],
};

describe('Workspace materials in Studio', () => {
    it('lists workspace documents and filters by kind', async () => {
        await renderAt('/studio/10/workspace', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/10': { body: OVERVIEW },
            'GET /api/studio/editions/10/workspace/documents': { body: DOCS },
            'GET /api/studio/editions/10/workspace/attachments': { body: [] },
        });

        expect(await screen.findByRole('heading', { name: 'Матеріали авторського простору' })).toBeInTheDocument();
        expect(await screen.findByText('00_Стан роботи')).toBeInTheDocument();
        expect(screen.getByText('Мана та Закони Магії')).toBeInTheDocument();

        // Filter by canon
        const canonTab = screen.getByRole('tab', { name: 'Канон' });
        await userEvent.click(canonTab);

        await waitFor(() => {
            expect(screen.queryByText('00_Стан роботи')).not.toBeInTheDocument();
            expect(screen.getByText('Мана та Закони Магії')).toBeInTheDocument();
        });
    });

    it('creates and saves a workspace document and handles version conflicts', async () => {
        let savedTitle = '';
        let conflictOccurred = false;

        await renderAt('/studio/10/workspace', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/10': { body: OVERVIEW },
            'GET /api/studio/editions/10/workspace/documents': { body: DOCS },
            'GET /api/studio/editions/10/workspace/documents/zakony-mahii': { body: DOC_DETAILS },
            'GET /api/studio/editions/10/workspace/attachments': { body: [] },
            'PUT /api/studio/editions/10/workspace/documents/zakony-mahii': (body) => {
                const req = body as { title: string; version: number };
                if (req.version === 2 && !conflictOccurred) {
                    conflictOccurred = true;
                    return {
                        status: 409,
                        body: {
                            status: 409,
                            detail: 'Цей документ уже змінено іншим учасником (поточна версія: 3).',
                        },
                    };
                }
                savedTitle = req.title;
                return {
                    body: { ...DOC_DETAILS, title: req.title, version: 4 },
                };
            },
        });

        // Click on document to edit
        await userEvent.click(await screen.findByText('Мана та Закони Магії'));

        expect(await screen.findByDisplayValue('Мана та Закони Магії')).toBeInTheDocument();
        expect(await screen.findByDisplayValue('Мана відчувається прохолодною. Посилання на [[stan-roboty]].')).toBeInTheDocument();

        // Edit title and submit -> triggers 409 conflict
        const titleInput = screen.getByLabelText('Назва матеріалу');
        await userEvent.clear(titleInput);
        await userEvent.type(titleInput, 'Закони Магії та Світла');

        const saveButton = screen.getByRole('button', { name: 'Зберегти' });
        await userEvent.click(saveButton);

        // Conflict alert is shown
        expect(await screen.findByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/Конфлікт версій/)).toBeInTheDocument();

        // Keep the user's text; never bypass the server version check by sending null.
        expect(screen.getByRole('button', { name: 'Завантажити мій текст' })).toBeInTheDocument();
        expect(screen.getByDisplayValue('Закони Магії та Світла')).toBeInTheDocument();
        expect(savedTitle).toBe('');
    });

    it('ignores a late response from the previously selected document', async () => {
        await renderAt('/studio/10/workspace', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/10': { body: OVERVIEW },
            'GET /api/studio/editions/10/workspace/documents': { body: DOCS },
            'GET /api/studio/editions/10/workspace/attachments': { body: [] },
        });
        let release!: (doc: DocumentDetails) => void;
        const pending = new Promise<DocumentDetails>((resolve) => { release = resolve; });
        const spy = vi.spyOn(studioApi, 'workspaceDocument').mockImplementation(async (_id, slug) =>
            slug === 'stan-roboty' ? pending : DOC_DETAILS);
        try {
            await userEvent.click(await screen.findByText('00_Стан роботи'));
            await userEvent.click(screen.getByText('Мана та Закони Магії'));
            expect(await screen.findByDisplayValue(DOC_DETAILS.content)).toBeInTheDocument();
            release({ ...DOC_DETAILS, slug: 'stan-roboty', title: '00_Стан роботи', content: 'Застаріла відповідь' });
            await waitFor(() => expect(screen.queryByDisplayValue('Застаріла відповідь')).not.toBeInTheDocument());
            expect(screen.getByDisplayValue(DOC_DETAILS.content)).toBeInTheDocument();
        } finally { spy.mockRestore(); }
    });

    it('uploads an illustration into the private workspace from the site', async () => {
        let attachment: unknown;
        await renderAt('/studio/10/workspace', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/10': { body: OVERVIEW },
            'GET /api/studio/editions/10/workspace/documents': { body: DOCS },
            'GET /api/studio/editions/10/workspace/attachments': { body: [] },
            'POST /api/media/images': { body: { id: 77, url: '/media/private.jpg' } },
            'POST /api/studio/editions/10/workspace/attachments': (body) => {
                attachment = body;
                return { body: { id: 9, imageId: 77, name: 'map.png', url: '/media/private.jpg' } };
            },
        });
        await userEvent.upload(await screen.findByLabelText('Додати ілюстрації'), new File(['test'], 'map.png', { type: 'image/png' }));
        await waitFor(() => expect(attachment).toEqual({ imageId: 77, name: 'map.png' }));
    });

    it('shows revision history and allows restoring an earlier version', async () => {
        let restoredId: number | null = null;
        const revisions = [
            { id: 201, version: 2, title: 'Закони Магії v2', content: 'Текст v2', authorNick: 'dmytro', createdAt: new Date().toISOString(), comment: null },
            { id: 202, version: 1, title: 'Закони Магії v1', content: 'Текст v1', authorNick: 'dmytro', createdAt: new Date().toISOString(), comment: null },
        ];

        await renderAt('/studio/10/workspace', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/10': { body: OVERVIEW },
            'GET /api/studio/editions/10/workspace/documents': { body: [DOCS[1]] },
            'GET /api/studio/editions/10/workspace/documents/zakony-mahii': { body: DOC_DETAILS },
            'GET /api/studio/editions/10/workspace/documents/zakony-mahii/revisions': { body: revisions },
            'GET /api/studio/editions/10/workspace/attachments': { body: [] },
            'POST /api/studio/editions/10/workspace/documents/zakony-mahii/revisions/202/restore': () => {
                restoredId = 202;
                return {
                    body: { ...DOC_DETAILS, title: 'Закони Магії v1', content: 'Текст v1', version: 3 },
                };
            },
        });

        await userEvent.click(await screen.findByText('Мана та Закони Магії'));

        const historyBtn = await screen.findByRole('button', { name: 'Історія змін' });
        await userEvent.click(historyBtn);

        expect(await screen.findByText('Історія змін документа')).toBeInTheDocument();
        expect(screen.getByText('Версія 2')).toBeInTheDocument();
        expect(screen.getByText('Версія 1')).toBeInTheDocument();

        const restoreBtns = screen.getAllByRole('button', { name: 'Відновити' });
        await userEvent.click(restoreBtns[1]); // restore version 1

        await waitFor(() => {
            expect(restoredId).toBe(202);
        });
    });
});
