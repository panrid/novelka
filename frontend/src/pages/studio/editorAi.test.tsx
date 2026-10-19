import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'panrid', email: 'panrid@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, ai: true,
};
const EDITOR = {
    number: 3, title: 'Маяк', revisionId: 41, published: true, draft: null, role: 'owner', mayAddPictures: true, hasOriginal: true,
    previous: 2, next: null, label: null,
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Рьо стояв біля маяка.', marks: [] }], imageId: null, imageUrl: null }],
};

afterEach(() => vi.unstubAllGlobals());

describe('AI in the editor (етап 19, п. 5)', () => {
    it('offers other versions of the paragraph and puts the one picked in its place', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/variants': { body: { variants: ['Рьо стояв коло маяка.', 'Біля маяка стояв Рьо.'] } },
            'PUT /api/studio/editions/7/chapters/3/draft': { status: 204 },
            'POST /api/studio/editions/7/chapters/3/publish': { body: { revisionId: 42 } },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        act(() => screen.getByRole('textbox', { name: 'Текст глави' }).focus());
        await userEvent.click(screen.getByRole('button', { name: 'Інший варіант' }));
        const sheet = await screen.findByRole('dialog', { name: 'Інший варіант' });
        expect(calls.find((call) => call.path.endsWith('/variants'))?.body).toEqual({ blockId: 'b1', text: 'Рьо стояв біля маяка.' });
        await within(sheet).findByText('Біля маяка стояв Рьо.');
        await userEvent.click(within(sheet).getAllByRole('button', { name: 'Взяти цей' })[1]!);
        expect(await screen.findByText('Біля маяка стояв Рьо.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/publish'))?.body).toMatchObject({
            blocks: [{ id: 'b1', content: [{ text: 'Біля маяка стояв Рьо.' }] }],
        }));
    });

    it('proofreads the paragraph and takes the change only on «Прийняти»', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/proofread-paragraph': { body: { text: 'Рьо стояв коло маяка.' } },
            'PUT /api/studio/editions/7/chapters/3/draft': { status: 204 },
            'POST /api/studio/editions/7/chapters/3/publish': { body: { revisionId: 42 } },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        act(() => screen.getByRole('textbox', { name: 'Текст глави' }).focus());
        await userEvent.click(screen.getByRole('button', { name: 'Вичитати абзац' }));
        const sheet = await screen.findByRole('dialog', { name: 'Вичитка ШІ' });
        expect(calls.find((call) => call.path.endsWith('/proofread-paragraph'))?.body).toEqual({ blockId: 'b1', text: 'Рьо стояв біля маяка.' });
        await userEvent.click(await within(sheet).findByRole('button', { name: 'Прийняти' }));
        expect(await screen.findByText('Рьо стояв коло маяка.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Опублікувати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.path.endsWith('/publish'))?.body).toMatchObject({
            blocks: [{ id: 'b1', content: [{ text: 'Рьо стояв коло маяка.' }] }],
        }));
    });

    it('checks the published chapter and says how many changes wait', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/check': { body: { proposed: 2 } },
        });
        await userEvent.click(await screen.findByRole('button', { name: 'Вичитати главу з ШІ' }));
        expect(await screen.findByText(/ШІ пропонує правок: 2/)).toBeInTheDocument();
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/check'))).toBe(true);
    });

    it('offers no AI to those from whom it is hidden', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: { ...OWNER, role: 'reader', ai: false } },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
        });
        await screen.findByText('Рьо стояв біля маяка.');
        expect(screen.queryByRole('button', { name: 'Інший варіант' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Вичитати абзац' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Вичитати главу з ШІ' })).not.toBeInTheDocument();
    });
});
