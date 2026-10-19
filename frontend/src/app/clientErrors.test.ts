import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { reportError, resetReported } from './clientErrors';

afterEach(() => {
    vi.unstubAllGlobals();
    resetReported();
});

describe('errors in the browser go to the server (етап 23)', () => {
    it('sends each error once, with the page path only, and skips answers the server gave on purpose', () => {
        const fetch = vi.fn(async () => new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetch);
        window.history.pushState({}, '', '/n/mah-vody/3?t=panrid');
        reportError('render', new TypeError('x is undefined'));
        reportError('render', new TypeError('x is undefined'));
        reportError('rejection', new ApiError(404, 'Такої новели немає.'));
        expect(fetch).toHaveBeenCalledTimes(1);
        const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toBe('/api/client-errors');
        expect(init.keepalive).toBe(true);
        expect(JSON.parse(init.body as string)).toMatchObject({ kind: 'render', message: 'TypeError: x is undefined', url: '/n/mah-vody/3' });
    });
});
