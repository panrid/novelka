import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const chapter = (number: number, label: string | null, title: string) => ({ number, title, label, manual: label !== null, published: true });

afterEach(() => vi.unstubAllGlobals());

describe('structure and volumes', () => {
    it('makes the first chapter a prologue, shows the new numbers first and saves', async () => {
        const { calls } = await renderAt('/studio/12/structure', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12/structure': { body: {
                numbering: 'continuous', volumes: [],
                chapters: [chapter(1, '0', 'Пролог'), chapter(2, '1', 'Повільне життя'), chapter(3, '2', 'Ліс')],
            } },
            'POST /api/studio/editions/12/structure/preview': { body: { 1: '', 2: '1', 3: '2' } },
            'PUT /api/studio/editions/12/structure': { body: {
                numbering: 'continuous', volumes: [{ firstNumber: 1, title: 'Пролог', kind: 'prologue' }, { firstNumber: 2, title: '', kind: 'volume' }],
                chapters: [chapter(1, '', 'Пролог'), chapter(2, '1', 'Повільне життя'), chapter(3, '2', 'Ліс')],
            } },
        });

        await userEvent.click(await screen.findByRole('checkbox', { name: 'Глава 0' }));
        await userEvent.click(screen.getByRole('button', { name: 'Виокремити в пролог' }));
        expect(screen.getByText('без номерів')).toBeInTheDocument();
        expect(screen.getByText('Том 1')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByText('0').tagName).toBe('S'));

        await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
        await waitFor(() => expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({
            numbering: 'continuous',
            volumes: [{ firstNumber: 1, title: 'Пролог', kind: 'prologue' }, { firstNumber: 2, title: '', kind: 'volume' }],
            automatic: [1], unnumbered: [],
        }));
    });

    it('starts a named volume at a chapter', async () => {
        await renderAt('/studio/12/structure', {
            'GET /api/me': { body: ME },
            'GET /api/studio/editions/12/structure': { body: { numbering: 'continuous', volumes: [], chapters: [chapter(1, null, 'Початок'), chapter(2, null, 'Далі')] } },
            'POST /api/studio/editions/12/structure/preview': { body: { 1: '1', 2: '2' } },
        });
        await userEvent.click(await screen.findByRole('checkbox', { name: 'Глава 2' }));
        await userEvent.click(screen.getByRole('button', { name: 'Почати новий том тут' }));
        const sheet = await screen.findByRole('dialog', { name: 'Том' });
        await userEvent.type(within(sheet).getByLabelText('Назва'), 'Подорож');
        await userEvent.click(within(sheet).getByRole('button', { name: 'Готово' }));
        expect(await screen.findByText('Том 1. Подорож')).toBeInTheDocument();
        expect(screen.getByText('Поза томами')).toBeInTheDocument();
    });
});
