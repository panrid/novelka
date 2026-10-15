import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'panrid', email: null, emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const picture = (id: number, fileName: string | null, uses: { editionId: number; novelSlug: string; title: string; number: number }[] = []) => ({
    id, url: `/media/${id}-640.png`, fullUrl: `/media/${id}-1920.png`, width: 1536, height: 1024, fileName,
    drawn: fileName === null, prompt: fileName === null ? 'A boy and thunderstones' : null, fragment: null, sourceUrl: null,
    createdAt: new Date().toISOString(), uses,
});
const MINE = [{ editionId: 3, novelSlug: 'tvorets-mahii', title: 'Творець магії', coverUrl: null, kind: 'machine', status: 'ongoing',
    chapterCount: 207, teamHandle: 'panrid', teamName: 'panrid', role: 'owner', drafts: 0, pendingSuggestions: 0, newWords: 0,
    jobState: null, jobChapter: null }];

afterEach(() => vi.unstubAllGlobals());

describe('«Мої картинки»', () => {
    it('shows drawn and uploaded pictures with where each one stands', async () => {
        await renderAt('/me/pictures', {
            'GET /api/me': { body: ME },
            'GET /api/me/pictures': { body: { total: 2, page: 1, items: [
                picture(11, '27-5.png', [{ editionId: 3, novelSlug: 'tvorets-mahii', title: 'Творець магії', number: 27 }]),
                picture(12, null),
            ] } },
        });
        expect(await screen.findByRole('button', { name: '27-5.png' })).toBeInTheDocument();
        expect(screen.getByText('глава 27')).toBeInTheDocument();
        expect(screen.getByText('ще ніде')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Намальована картинка' }));
        const sheet = await screen.findByRole('dialog');
        expect(within(sheet).getByText(/Намальовано на сайті/)).toBeInTheDocument();
        expect(within(sheet).getByText('Ще в жодній главі.')).toBeInTheDocument();
    });

    it('uploads several files, keeps them chosen and places them by their names', async () => {
        let next = 40;
        const { calls } = await renderAt('/me/pictures', {
            'GET /api/me': { body: ME },
            'GET /api/me/pictures': { body: { total: 0, page: 1, items: [] } },
            'POST /api/media/images': () => ({ status: 201, body: { id: next++, url: '/media/x.png' } }),
            'GET /api/studio': { body: MINE },
            'POST /api/me/pictures/placement/preview': { body: [
                { imageId: 40, fileName: '27-5.png', url: '/media/40.png', chapter: 27, paragraph: 5, quote: 'Склад був повний каменів.', problem: null },
                { imageId: 41, fileName: 'scene.png', url: '/media/41.png', chapter: null, paragraph: null, quote: null,
                    problem: 'З назви файлу не видно, куди її ставити.' },
            ] },
            'POST /api/me/pictures/placement': { body: { chapters: 1, pictures: 1, skipped: ['scene.png: З назви файлу не видно, куди її ставити.'] } },
        });
        const files = [new File(['a'], '27-5.png', { type: 'image/png' }), new File(['b'], 'scene.png', { type: 'image/png' })];
        await userEvent.upload(await screen.findByLabelText('Файли картинок'), files);
        await waitFor(() => expect(calls.filter((call) => call.method === 'POST' && call.path === '/api/media/images')).toHaveLength(2));
        const sent = calls.find((call) => call.path === '/api/media/images')!.body as FormData;
        expect((sent.get('file') as File).name).toBe('27-5.png');
        expect(await screen.findByText('Вибрано: 2')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Розставити в новелі…' }));
        const sheet = await screen.findByRole('dialog');
        await userEvent.selectOptions(await within(sheet).findByLabelText('Новела'), '3');
        expect(await within(sheet).findByText(/Глава 27, після 5-го абзацу: «Склад був повний каменів.»/)).toBeInTheDocument();
        expect(within(sheet).getByText('З назви файлу не видно, куди її ставити.')).toBeInTheDocument();
        await userEvent.click(within(sheet).getByRole('button', { name: 'Вставити 1 у 1 главу' }));
        expect(await within(sheet).findByText('Вставлено 1 картинку у 1 главу. Читачі вже бачать.')).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/me/pictures/placement')?.body).toEqual({ editionId: 3, imageIds: [40, 41] });
    });
});
