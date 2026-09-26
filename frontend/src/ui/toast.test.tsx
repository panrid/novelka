import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

describe('an action without its own place for errors', () => {
    it('says what went wrong at the bottom of the screen', async () => {
        await renderAt('/me/suggestions', {
            'GET /api/me': { body: { id: 1, nick: 'mika', email: 'm@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
                dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true } },
            'GET /api/me/suggestions': { body: [{ id: 5, novelSlug: 'mah-vody', novelTitle: 'Маг води', teamHandle: 'panrid', chapter: 3,
                chapterLabel: '3', kind: 'block', preview: 'Текст', state: 'pending', reviewNote: null, updatedAt: new Date().toISOString() }] },
            'DELETE /api/suggestions/5': { status: 404, body: { detail: 'Цієї правки вже немає або її перевірили.' } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Відкликати' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Цієї правки вже немає або її перевірили.');
    });
});
