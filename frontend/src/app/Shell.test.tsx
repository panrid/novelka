import { renderAt } from '../test/render';
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, appearance: {},
};

describe('Shell', () => {
    it('offers the five sections in Ukrainian', async () => {
        await renderAt('/');

        const nav = screen.getAllByRole('navigation', { name: 'Розділи' })[0]!;
        const labels = within(nav).getAllByRole('link').map((link) => link.textContent);
        expect(labels).toEqual(['Головна', 'Каталог', 'Бібліотека', 'Вхідні', 'Я']);
    });

    it("puts «Студія» in the computer's menu for a signed-in person, and in the phone's only when asked", async () => {
        await renderAt('/', { 'GET /api/me': { body: ME }, 'GET /api/studio': { body: [] } });

        const [top, bottom] = await waitFor(() => {
            const navs = screen.getAllByRole('navigation', { name: 'Розділи' });
            expect(within(navs[0]!).getByRole('link', { name: 'Студія' })).toHaveAttribute('href', '/studio');
            return navs;
        });
        expect(within(top!).getAllByRole('link').map((link) => link.textContent).slice(0, 5)).toEqual(['Головна', 'Каталог', 'Бібліотека', 'Студія', 'Вхідні']);
        expect(within(bottom!).queryByRole('link', { name: 'Студія' })).not.toBeInTheDocument();
    });

    it("adds «Студія» to the phone's tabs for those who asked for it", async () => {
        await renderAt('/', { 'GET /api/me': { body: { ...ME, studioInMenu: true } }, 'GET /api/studio': { body: [] } });

        await waitFor(() => expect(screen.getAllByRole('link', { name: 'Студія' })).toHaveLength(2));
    });

    it('marks the current section', async () => {
        await renderAt('/library');

        expect(await screen.findByRole('heading', { name: 'Бібліотека' })).toBeInTheDocument();
        const current = screen.getAllByRole('link', { current: 'page' }).map((link) => link.textContent);
        expect(new Set(current)).toEqual(new Set(['Бібліотека']));
    });

    it('explains an unknown address instead of showing a blank page', async () => {
        await renderAt('/n/nothing/here');

        expect(await screen.findByRole('heading', { name: 'Такої сторінки немає' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'На головну' })).toHaveAttribute('href', '/');
    });
});
