import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'panrid', email: 'panrid@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, ai: true,
};
const EDITOR = {
    number: 3, title: 'Маяк', revisionId: 41, published: true, draft: null, role: 'owner', mayAddPictures: true,
    previous: 2, next: null, label: null,
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Рьо стояв біля маяка.', marks: [] }], imageId: null, imageUrl: null }],
};

afterEach(() => vi.unstubAllGlobals());

describe('drawing a scene', () => {
    it('describes the paragraph, shows the price, draws and puts the picture after it', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'GET /api/studio/editions/7/illustrations/price': { body: { usd: 0.04, shah: 2, showShah: true, model: 'google/gemini-2.5-flash-image' } },
            'POST /api/studio/editions/7/illustrations/prompt': { body: { prompt: 'A young man by a lighthouse at night' } },
            'POST /api/studio/editions/7/illustrations': { status: 201, body: { imageId: 90, url: '/media/x-1280.jpg', costUsd: 0.039, costShah: 2 } },
            'PUT /api/studio/editions/7/chapters/3/draft': { status: 204 },
            'POST /api/studio/editions/7/chapters/3/publish': { body: { revisionId: 42 } },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        // The cursor goes into the first paragraph (jsdom cannot place it by clicking a point).
        act(() => screen.getByRole('textbox', { name: 'Текст глави' }).focus());
        await userEvent.click(screen.getByRole('button', { name: 'Намалювати сцену' }));
        const sheet = await screen.findByRole('dialog', { name: 'Намалювати сцену' });
        expect(within(sheet).getByLabelText('Фрагмент глави')).toHaveValue('Рьо стояв біля маяка.');

        await userEvent.click(within(sheet).getByRole('button', { name: 'Скласти опис для художника' }));
        expect(await within(sheet).findByLabelText('Опис для художника')).toHaveValue('A young man by a lighthouse at night');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Намалювати · 2 шаги' }));
        expect(calls.find((call) => call.path === '/api/studio/editions/7/illustrations')?.body)
            .toEqual({ prompt: 'A young man by a lighthouse at night', fragment: 'Рьо стояв біля маяка.', aspect: '3:4', chapter: 3 });

        await userEvent.click(await within(sheet).findByRole('button', { name: 'Вставити в главу' }));
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/publish'))?.body).toMatchObject({
            blocks: [{ id: 'b1', type: 'paragraph' }, { type: 'image', imageId: 90 }],
        }));
    });

    it('puts pictures from «Мої картинки» after the paragraph with the cursor', async () => {
        const mine = (id: number, fileName: string) => ({ id, url: `/media/${id}-640.png`, fullUrl: `/media/${id}.png`, width: 1536, height: 1024,
            fileName, drawn: false, prompt: null, fragment: null, sourceUrl: null, createdAt: new Date().toISOString(), uses: [] });
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: { ...OWNER, role: 'reader' } },
            'GET /api/studio/editions/7/chapters/3': { body: { ...EDITOR, blocks: [...EDITOR.blocks,
                { id: 'b2', type: 'paragraph', content: [{ text: 'Світло згасло.', marks: [] }], imageId: null, imageUrl: null }] } },
            'GET /api/me/pictures': { body: { total: 2, page: 1, items: [mine(70, '3-1.png'), mine(71, 'lighthouse.png')] } },
            'PUT /api/studio/editions/7/chapters/3/draft': { status: 204 },
            'POST /api/studio/editions/7/chapters/3/publish': { body: { revisionId: 42 } },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        act(() => screen.getByRole('textbox', { name: 'Текст глави' }).focus());
        await userEvent.click(screen.getByRole('button', { name: 'З моїх картинок' }));
        const sheet = await screen.findByRole('dialog', { name: 'Мої картинки' });
        await userEvent.click(await within(sheet).findByRole('button', { name: 'lighthouse.png' }));
        await userEvent.click(within(sheet).getByRole('button', { name: '3-1.png' }));
        await userEvent.click(within(sheet).getByRole('button', { name: 'Вставити 2' }));
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/publish'))?.body).toMatchObject({
            blocks: [{ id: 'b1' }, { type: 'image', imageId: 71 }, { type: 'image', imageId: 70 }, { id: 'b2' }],
        }));
    });

    it('is not offered while the site keeps AI hidden from the person (етап 19, п. 7)', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: { ...OWNER, role: 'reader', ai: false } },
            'GET /api/me/shahs': { body: { available: 5, reserved: 0, usdPerShah: 0.07, hasMore: false, running: [], history: [] } },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        expect(screen.queryByRole('button', { name: 'Намалювати сцену' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Картинка з файлу' })).toBeInTheDocument();
    });

    it('is not offered to translators other than the site owner', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: { ...OWNER, role: 'reader' } },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        expect(screen.queryByRole('button', { name: 'Намалювати сцену' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Картинка з файлу' })).toBeInTheDocument();
    });
});
