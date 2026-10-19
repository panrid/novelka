import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'panrid', email: 'panrid@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, ai: true,
};
const EDITOR = {
    number: 3, title: 'Маяк', revisionId: 43, published: true, draft: null, role: 'owner', mayAddPictures: true, hasOriginal: true,
    previous: 2, next: null, label: null, blocks: [],
};
const at = new Date().toISOString();
const REVISIONS = [
    { id: 43, authorNick: 'panrid', origin: 'suggestion', createdAt: at, blocksChanged: 1, charsChanged: 4, published: true,
        stage: null, model: null, jobId: null, proposers: ['lysytsia'], notes: ['Друкарська помилка'] },
    { id: 42, authorNick: 'panrid', origin: 'editor', createdAt: at, blocksChanged: 2, charsChanged: 30, published: false,
        stage: null, model: null, jobId: null, proposers: [], notes: [] },
    { id: 41, authorNick: null, origin: 'ai', createdAt: at, blocksChanged: 5, charsChanged: 900, published: false,
        stage: 'translate', model: 'x-ai/grok-4.3 · вичитка deepseek/deepseek-v4-flash', jobId: 7, proposers: [], notes: [] },
];
const TEXT = (id: number) => ({ id, title: 'Маяк', parentTitle: null, parentBlocks: [],
    blocks: [{ id: 'b1', type: 'paragraph', content: [{ text: 'Рьо стояв біля маяка.', marks: [] }], imageId: null, imageUrl: null }] });

afterEach(() => vi.unstubAllGlobals());

describe('chapter history (етап 20)', () => {
    it('says who made each version, filters them and brings an old one back', async () => {
        const { calls } = await renderAt('/studio/7/chapters/3/history', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/7/chapters/3': { body: EDITOR },
            'GET /api/studio/editions/7/chapters/3/revisions': { body: REVISIONS },
            'GET /api/studio/editions/7/chapters/3/revisions/41': { body: TEXT(41) },
            'POST /api/studio/editions/7/chapters/3/revisions/41/restore': { body: { revisionId: 44 } },
        });
        expect(await screen.findByText(/Правки від lysytsia · прийняв panrid/)).toBeInTheDocument();
        expect(screen.getByText(/«Друкарська помилка»/)).toBeInTheDocument();
        expect(screen.getByText(/x-ai\/grok-4.3 · вичитка deepseek/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'запуск №7' })).toHaveAttribute('href', '/studio/7/translate/jobs/7');

        await userEvent.click(screen.getByRole('radio', { name: 'ШІ' }));
        expect(screen.queryByText(/Правки від lysytsia/)).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /ШІ · переклад/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Повернути цю версію' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Повернути цю версію?' })).getByRole('button', { name: 'Повернути' }));
        expect(await screen.findByText(/Версію повернуто/)).toBeInTheDocument();
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/revisions/41/restore'))).toBe(true);
    });
});
