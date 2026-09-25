import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';

const ME = {
    id: 1, nick: 'mavka', email: 'mavka@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};

afterEach(() => vi.unstubAllGlobals());

describe('composer', () => {
    it('suggests people after @ and puts the markup in with buttons', async () => {
        const { calls } = await renderAt('/inbox/chat', {
            'GET /api/me': { body: ME },
            'GET /api/chat': { body: [] },
            'GET /api/mentions': { body: [{ name: 'lysytsia', title: null, avatarUrl: null }, { name: 'lyra', title: null, avatarUrl: null }] },
            'POST /api/chat': { status: 201, body: { id: 1 } },
        });
        const box = await screen.findByLabelText('Повідомлення в чат');
        await userEvent.type(box, 'Привіт, @ly');
        expect(await screen.findByRole('option', { name: '@lysytsia' })).toHaveAttribute('aria-selected', 'true');
        await userEvent.keyboard('{ArrowDown}{Enter}');
        expect(box).toHaveValue('Привіт, @lyra ');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Форматування' }));
        await userEvent.click(screen.getByRole('button', { name: 'Жирний' }));
        await userEvent.type(box, 'важливо', { skipClick: true });
        expect(box).toHaveValue('Привіт, @lyra **важливо**');

        await userEvent.click(screen.getByRole('button', { name: 'Надіслати' }));
        await vi.waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body)
            .toEqual({ body: 'Привіт, @lyra **важливо**', replyTo: null }));
    });
});
