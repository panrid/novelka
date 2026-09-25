import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router';
import { render } from '@testing-library/react';
import { vi } from 'vitest';
import { createAppRouter } from '../app/router';

type Route = { status?: number; body?: unknown };

/**
 * Renders the whole app at `path` with a fake backend: `api` maps "METHOD /path" to a reply.
 * Unknown calls answer 404, and every call is recorded in `calls`.
 */
export async function renderAt(path: string, api: Record<string, Route | ((body: unknown) => Route)> = {}) {
    const calls: { method: string; path: string; query: string; body: unknown }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: string, init: RequestInit = {}) => {
        const method = (init.method ?? 'GET').toUpperCase();
        const url = new URL(input, 'http://localhost');
        const pathname = decodeURIComponent(url.pathname);
        const body = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
        calls.push({ method, path: pathname, query: url.search, body });
        const handler = api[`${method} ${pathname}`];
        const reply = typeof handler === 'function' ? handler(body) : handler;
        if (!reply) {
            return method === 'GET' && pathname === '/api/me'
                ? new Response(null, { status: 204 })
                : new Response(JSON.stringify({ detail: 'Такої сторінки немає.' }), { status: 404 });
        }
        const status = reply.status ?? 200;
        return new Response(status === 204 ? null : JSON.stringify(reply.body ?? {}), { status });
    }));

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const router = createAppRouter(queryClient, createMemoryHistory({ initialEntries: [path] }));
    await router.load();
    render(
        <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
        </QueryClientProvider>,
    );
    return { router, calls, queryClient };
}
