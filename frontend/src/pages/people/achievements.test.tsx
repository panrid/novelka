import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

afterEach(() => vi.unstubAllGlobals());

describe('achievements on a profile', () => {
    it('shows the level, its progress and which badges are earned', async () => {
        await renderAt('/u/oleh', {
            'GET /api/me': { status: 204 },
            'GET /api/users/oleh': { body: { nick: 'oleh', avatarUrl: null, bio: '', memberSince: '2026-09-01T00:00:00Z' } },
            'GET /api/users/oleh/works': { body: [] },
            'GET /api/users/oleh/activity': { body: { reading: [], acceptedSuggestions: 0 } },
            'GET /api/users/oleh/achievements': { body: {
                level: 2, points: 50, levelPoints: 20, nextLevelPoints: 80, badges: [
                    { code: 'comment_1', title: 'Перше слово', description: 'Залишити коментар', earnedAt: new Date().toISOString(), progress: 1, goal: 1 },
                    { code: 'reader_100', title: 'Книголюб', description: 'Прочитати 100 глав', earnedAt: null, progress: 37, goal: 100 },
                ],
            } },
        });
        expect(await screen.findByText('Рівень 2')).toBeInTheDocument();
        expect(screen.getByRole('progressbar', { name: 'До рівня 3' })).toHaveAttribute('aria-valuenow', '50');
        expect(screen.getByText('Отримано 1 з 2.')).toBeInTheDocument();
        expect(screen.getByText('37 з 100')).toBeInTheDocument();
        expect(screen.getByText('отримано щойно')).toBeInTheDocument();
    });
});
