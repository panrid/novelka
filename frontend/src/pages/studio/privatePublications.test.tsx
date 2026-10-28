import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

const OVERVIEW = {
    editionId: 7, novelSlug: 'mag-vody', title: 'Маг води', author: 'Міка', description: [], tags: [],
    kind: 'original', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 3, ownNovel: true,
    teamHandle: 'mika', teamName: 'Міка', role: 'owner' as const, sourceChapters: null, pendingSuggestions: 0,
    drafts: 0, newWords: 0, originalUrl: null, pausedUntil: null, source: 'original', originals: 0,
    facts: { titleOriginal: null, titleEnglish: null, altTitles: [], sourceStatus: null, sourceChapterCount: 0 },
    downloadAllowed: true, visibility: 'public' as const,
};

describe('private publications in Studio settings', () => {
    it('allows the owner to switch visibility between public and private', async () => {
        const calls: { visibility: string }[] = [];
        await renderAt('/studio/7/settings', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7': { body: OVERVIEW },
            'GET /api/studio/editions/7/contributions': { body: [] },
            'GET /api/studio/editions/7/readers': { body: [] },
            'PUT /api/studio/editions/7/visibility': (body) => {
                const req = body as { visibility: 'public' | 'private' };
                calls.push(req);
                return { body: { ...OVERVIEW, visibility: req.visibility } };
            },
        });

        expect(await screen.findByRole('heading', { name: 'Видимість' })).toBeInTheDocument();
        const privateOption = screen.getByRole('radio', { name: 'Приватна' });
        await userEvent.click(privateOption);

        await waitFor(() => expect(calls).toHaveLength(1));
        expect(calls[0]).toEqual({ visibility: 'private' });
    });

    it('allows inviting and revoking readers by nick', async () => {
        const added: string[] = [];
        const removed: string[] = [];
        let readers = [{ nick: 'friend_reader', avatarUrl: null, grantedAt: new Date().toISOString() }];

        await renderAt('/studio/7/settings', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/7': { body: { ...OVERVIEW, visibility: 'private' as const } },
            'GET /api/studio/editions/7/contributions': { body: [] },
            'GET /api/studio/editions/7/readers': () => ({ body: readers }),
            'POST /api/studio/editions/7/readers': (body) => {
                const req = body as { nick: string };
                added.push(req.nick);
                readers = [...readers, { nick: req.nick, avatarUrl: null, grantedAt: new Date().toISOString() }];
                return { status: 204 };
            },
            'DELETE /api/studio/editions/7/readers/friend_reader': () => {
                removed.push('friend_reader');
                readers = [];
                return { status: 204 };
            },
        });

        expect(await screen.findByRole('heading', { name: 'Запрошені читачі' })).toBeInTheDocument();
        expect(await screen.findByText('friend_reader')).toBeInTheDocument();

        // Invite a new reader
        const input = screen.getByLabelText('Нік читача');
        await userEvent.type(input, 'new_reader');
        await userEvent.click(screen.getByRole('button', { name: 'Запросити' }));

        await waitFor(() => expect(added).toContain('new_reader'));

        // Revoke access
        const revokeButtons = await screen.findAllByRole('button', { name: 'Відкликати' });
        await userEvent.click(revokeButtons[0]);

        await waitFor(() => expect(removed).toContain('friend_reader'));
    });
});
