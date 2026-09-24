import { RouterProvider, createMemoryHistory } from '@tanstack/react-router';
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createAppRouter } from './router';

async function renderAt(path: string) {
    const router = createAppRouter();
    router.update({ history: createMemoryHistory({ initialEntries: [path] }) });
    await router.load();
    render(<RouterProvider router={router} />);
    return router;
}

describe('Shell', () => {
    it('offers the five sections in Ukrainian', async () => {
        await renderAt('/');

        const nav = screen.getAllByRole('navigation', { name: 'Розділи' })[0]!;
        const labels = within(nav).getAllByRole('link').map((link) => link.textContent);
        expect(labels).toEqual(['Головна', 'Пошук', 'Бібліотека', 'Вхідні', 'Я']);
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
