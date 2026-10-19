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
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Рьо', marks: [] }], imageId: null, imageUrl: null }],
};

afterEach(() => vi.unstubAllGlobals());

describe('«Словник» in the editor (етап 19)', () => {
    it('shows what the selected word was translated from and adds both parts to the glossary', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'POST /api/studio/editions/7/chapters/3/glossary/source': { body: { paragraph: 'リョウは灯台のそばに立っていた。', original: 'リョウ', language: 'ja' } },
            'POST /api/studio/editions/7/glossary': { status: 201, body: { id: 5 } },
        });
        await screen.findByText('Рьо');
        expect(screen.getByRole('button', { name: 'До словника' })).toBeDisabled();
        // jsdom has no layout to drag over: the whole paragraph is selected from the keyboard.
        act(() => screen.getByRole('textbox', { name: 'Текст глави' }).focus());
        await userEvent.keyboard('{Control>}a{/Control}');
        await userEvent.click(screen.getByRole('button', { name: 'До словника' }));

        const sheet = await screen.findByRole('dialog', { name: 'До словника' });
        expect(within(sheet).getByLabelText(/У перекладі/)).toHaveValue('Рьо');
        expect(await within(sheet).findByText('リョウ')).toBeInTheDocument();
        expect(within(sheet).getByLabelText(/В оригіналі/)).toHaveValue('リョウ');
        expect(calls.find((call) => call.path.endsWith('/glossary/source'))?.body).toEqual({ blockId: 'b1', translation: 'Рьо', selection: 'Рьо' });

        await userEvent.clear(within(sheet).getByLabelText(/В оригіналі/));
        await userEvent.type(within(sheet).getByLabelText(/В оригіналі/), 'リョウ君');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Додати до словника' }));
        expect(await within(sheet).findByText(/у словнику, погоджено/)).toBeInTheDocument();
        expect(calls.find((call) => call.path === '/api/studio/editions/7/glossary')?.body)
            .toEqual({ chapter: 3, ukrainian: 'Рьо', original: 'リョウ君', kind: 'character', gender: 'unknown' });
    });

    it('is not offered to those from whom AI is hidden', async () => {
        await renderAt('/studio/7/chapters/3', {
            'GET /api/me': { body: { ...OWNER, role: 'reader', ai: false } },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
        });
        await screen.findByText('Рьо');
        expect(screen.queryByRole('button', { name: 'До словника' })).not.toBeInTheDocument();
    });
});
