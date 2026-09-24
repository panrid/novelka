import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

function respond(status: number, body: unknown) {
    return vi.fn().mockImplementation(async () => new Response(JSON.stringify(body), { status }));
}

describe('api', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    });

    it('returns parsed JSON', async () => {
        vi.stubGlobal('fetch', respond(200, { status: 'ok' }));

        await expect(api('/api/health')).resolves.toEqual({ status: 'ok' });
    });

    it('shows the server message from a problem detail', async () => {
        vi.stubGlobal('fetch', respond(404, { status: 404, detail: 'Такої сторінки немає.' }));

        await expect(api('/api/x')).rejects.toEqual(new ApiError(404, 'Такої сторінки немає.'));
    });

    it('keeps the reason code for pages that react to it', async () => {
        vi.stubGlobal('fetch', respond(403, { detail: 'Спершу підтвердьте пошту.', reason: 'email-not-verified' }));

        await expect(api('/api/auth/login')).rejects.toMatchObject({ status: 403, reason: 'email-not-verified' });
    });

    it('falls back to a generic message when the body is not a problem detail', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>502</html>', { status: 502 })));

        await expect(api('/api/x')).rejects.toMatchObject({ status: 502, message: expect.stringContaining('Щось пішло не так') });
    });

    it('explains a lost connection', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

        await expect(api('/api/x')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('Немає зʼєднання') });
    });

    it('sends the CSRF token only with changes', async () => {
        document.cookie = 'XSRF-TOKEN=abc123';
        const fetch = respond(200, {});
        vi.stubGlobal('fetch', fetch);

        await api('/api/x');
        await api('/api/x', { method: 'POST', body: JSON.stringify({}) });

        const [, getInit] = fetch.mock.calls[0] as [string, RequestInit];
        const [, postInit] = fetch.mock.calls[1] as [string, RequestInit];
        expect(new Headers(getInit.headers).get('X-XSRF-TOKEN')).toBeNull();
        expect(new Headers(postInit.headers).get('X-XSRF-TOKEN')).toBe('abc123');
    });
});
