import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';
import { Loading } from '../ui/Loading';
import { NOVELKA_ACCENT, NOVELKA_TONE, siteLook } from './model';
import { setSitePreset, toggleSiteBase, useAppearance } from './store';

afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    act(() => setSitePreset('default'));
});

describe('the new design (етап 20, крок 1)', () => {
    it('is dark by default, and the sun in the header turns it light and back', async () => {
        act(() => setSitePreset('default'));
        const { queryClient } = await renderAt('/rules');
        // The account's look arrives with /api/me and is applied first; the button works on top of it.
        await waitFor(() => expect(queryClient.getQueryState(['me'])?.status).toBe('success'));
        expect(document.documentElement.style.getPropertyValue('--bg')).toBe('#0f1115');
        expect(document.documentElement.style.getPropertyValue('--accent')).toBe(NOVELKA_ACCENT.dark);
        await userEvent.click(await screen.findByRole('button', { name: 'Світла тема' }));
        expect(document.documentElement.style.getPropertyValue('--bg')).toBe('#f3f4f6');
        expect(document.documentElement.style.getPropertyValue('--accent')).toBe(NOVELKA_ACCENT.light);
        await userEvent.click(screen.getByRole('button', { name: 'Темна тема' }));
        expect(document.documentElement.style.getPropertyValue('--bg')).toBe('#0f1115');
    });

    it('keeps a look a person chose, only light or dark', () => {
        act(() => setSitePreset('ocean'));
        let look = siteLook(readAppearance());
        expect(look.accent).toBe('#4fb3d9');
        act(() => toggleSiteBase());
        look = siteLook(readAppearance());
        expect(look.base).toBe('light');
        expect(look.accent).toBe('#4fb3d9');
        expect(look.bg).not.toBe(NOVELKA_TONE);
    });

    it('waits quietly, then shows shapes, then offers to try again', () => {
        vi.useFakeTimers();
        const retry = vi.fn();
        const { container } = render(<Loading onRetry={retry} />);
        expect(container.querySelectorAll('span:not(.visually-hidden)').length).toBe(0);
        act(() => vi.advanceTimersByTime(300));
        expect(container.querySelectorAll('span:not(.visually-hidden)').length).toBeGreaterThan(3);
        expect(screen.queryByText('Довше, ніж зазвичай.')).not.toBeInTheDocument();
        act(() => vi.advanceTimersByTime(8_000));
        expect(screen.getByText('Довше, ніж зазвичай.')).toBeInTheDocument();
        act(() => screen.getByRole('button', { name: 'Спробувати ще раз' }).click());
        expect(retry).toHaveBeenCalledTimes(1);
    });
});

function readAppearance() {
    let seen: ReturnType<typeof useAppearance> = {};
    function Probe() {
        seen = useAppearance();
        return null;
    }
    render(<Probe />);
    return seen;
}
