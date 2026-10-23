import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../test/render';
import { Loading } from '../ui/Loading';
import lib from '../../../doc/redesign/mockup-src/lib.mjs?raw';
import { clean, type SiteAppearance } from './model';
import { apply, setSite, setSiteDefault } from './store';
import { THEMES, themeVars } from './themes';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true, appearance: {},
};

const root = document.documentElement;
const bg = () => root.style.getPropertyValue('--bg').toLowerCase();

afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    act(() => {
        setSite({ frame: undefined, theme: undefined, mode: undefined, anim: undefined } as unknown as SiteAppearance);
        setSiteDefault({ frame: 'pult', theme: 'slanets' });
    });
});

describe('каркас і тема (етап 20, #292)', () => {
    it('is Пульт · Сланець by default, dark, and the sun in the header turns the same theme light and back', async () => {
        apply(); // as main.tsx does before the app draws
        const { queryClient } = await renderAt('/rules');
        await waitFor(() => expect(queryClient.getQueryState(['me'])?.status).toBe('success'));
        expect(root.dataset.frame).toBe('pult');
        expect(bg()).toBe('#1c1f25');
        expect(root.style.getPropertyValue('--accent').toLowerCase()).toBe('#f2b35b');
        await userEvent.click(await screen.findByRole('button', { name: 'Світла тема' }));
        expect(bg()).toBe('#f3f4f6');
        expect(root.style.getPropertyValue('--accent').toLowerCase()).toBe('#a86200');
        expect(root.dataset.theme).toBe('light');
        await userEvent.click(screen.getByRole('button', { name: 'Темна тема' }));
        expect(bg()).toBe('#1c1f25');
    });

    it('has the four drawn themes exactly as the mockup has them', () => {
        let seen = 0;
        for (const [, id, lt, body] of lib.matchAll(/^\.th-(\w+)(\.lt)?\{([^}]*)\}/gm)) {
            const drawn = Object.fromEntries(body!.split(';').map((pair: string) => {
                const [name, value] = pair.split(':');
                return [name!.trim().slice(2), value!.trim()];
            }));
            expect(themeVars(id as 'slanets', { bg: 0, accent: '#000000' }, Boolean(lt)), `${id}${lt ?? ''}`).toEqual(drawn);
            seen++;
        }
        expect(seen).toBe(8);
        expect(THEMES).toHaveLength(15);
    });

    it('keeps the colours of a style chosen before: a тема, light or dark as it was, or «Свої кольори»', () => {
        expect(clean({ site: { preset: 'parchment', custom: false, base: 'light', bg: 3, accent: '#7a5a2b', ui: 'merriweather', radius: 6, catalog: 'shelf' } }))
            .toEqual({ site: { theme: 'parchment', mode: 'light', catalog: 'shelf' } });
        expect(clean({ site: { preset: 'ocean', base: 'light', bg: 2, accent: '#4fb3d9', nav: 'side' } }))
            .toEqual({ site: { theme: 'ocean', mode: 'light' } });
        expect(clean({ site: { preset: 'default', base: 'light', bg: 6, accent: '#2a4bd7' } }))
            .toEqual({ site: { mode: 'light' } });
        expect(clean({ site: { preset: 'default', custom: true, scale: 1.1, anim: 'full', icons: 'tabler' }, reader: { size: 20 } }))
            .toEqual({ site: { scale: 1.1 }, reader: { size: 20 } });
        expect(clean({ site: { preset: 'forest', custom: true, base: 'dark', bg: 3, accent: '#c2456b' } }))
            .toEqual({ site: { theme: 'own', own: { bg: 3, accent: '#c2456b' } } });
        expect(clean({ site: { preset: 'terminal', custom: false }, siteOwn: { preset: 'default', custom: true, bg: 2, accent: '#ff3ea5' } }))
            .toEqual({ site: { theme: 'terminal', own: { bg: 2, accent: '#ff3ea5' } } });
        // Already in the new shape: as it is; what this version does not know is dropped.
        expect(clean({ site: { frame: 'obl', theme: 'slyva', mode: 'system', own: { bg: 9, accent: '#fff' }, radius: 4 } }))
            .toEqual({ site: { frame: 'obl', theme: 'slyva', mode: 'system' } });
    });

    it('follows the site\'s own каркас and тема until a person picks theirs', async () => {
        const { calls } = await renderAt('/rules');
        act(() => setSiteDefault({ frame: 'obl', theme: 'slyva' }));
        expect(root.dataset.frame).toBe('obl');
        expect(bg()).toBe('#191120');
        act(() => setSite({ theme: 'sukno' }));
        expect(bg()).toBe('#0e1613');
        expect(root.dataset.frame).toBe('obl');
        act(() => setSiteDefault({ frame: 'zv', theme: 'graphite' }));
        expect(bg()).toBe('#0e1613');
        expect(root.dataset.frame).toBe('zv');
        expect(JSON.parse(localStorage.getItem('novelka:site-look')!)).toEqual({ frame: 'zv', theme: 'graphite' });
        expect(calls.some((call) => call.path === '/api/site/look')).toBe(false); // asked once, when the app starts (main.tsx)
    });

    it('is chosen in «Вигляд сайту»: каркас, тема, «Свої кольори», light or dark — and kept with the account', async () => {
        const saved: unknown[] = [];
        await renderAt('/me/settings/appearance', {
            'GET /api/me': { body: ME },
            'PUT /api/me/appearance': (body) => { saved.push(body); return { body: { ...ME, appearance: body } }; },
        });
        const frames = await screen.findByRole('radiogroup', { name: 'Каркас' });
        expect(within(frames).getByRole('radio', { name: /Пульт.*за замовчуванням/ })).toBeChecked();
        await userEvent.click(within(frames).getByRole('radio', { name: /Палітурка/ }));
        expect(root.dataset.frame).toBe('pal');

        const themes = screen.getByRole('radiogroup', { name: 'Тема' });
        expect(within(themes).getAllByRole('radio')).toHaveLength(8);
        await userEvent.click(within(themes).getByRole('radio', { name: 'Неон' }));
        expect(root.style.getPropertyValue('--accent')).toBe('#ff3ea5');
        await userEvent.click(screen.getByRole('button', { name: 'Усі теми · 16 ›' }));
        await userEvent.click(within(themes).getByRole('radio', { name: 'Ретро-термінал' }));
        expect(bg()).toBe('#0a0f0a');

        await userEvent.click(within(themes).getByRole('radio', { name: 'Свої кольори' }));
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Тло' })).getByRole('radio', { name: 'Мох' }));
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Акцент' })).getByRole('radio', { name: '#c2456b' }));
        expect(bg()).toBe('#172016');
        expect(root.style.getPropertyValue('--accent')).toBe('#c2456b');

        // The same colours, light: the tone in the same place among the light ones.
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Світла чи темна' })).getByRole('radio', { name: 'Світла' }));
        expect(root.dataset.theme).toBe('light');
        expect(bg()).toBe('#e9dcc0');
        await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Анімації' })).getByRole('radio', { name: 'Вимкнені' }));
        expect(root.dataset.motion).toBe('off');

        await waitFor(() => expect(saved.at(-1)).toEqual({
            site: { frame: 'pal', theme: 'own', own: { bg: 3, accent: '#c2456b' }, mode: 'light', anim: 'off' },
        }), { timeout: 2000 });
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
