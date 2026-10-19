import { QueryClient } from '@tanstack/react-query';
import { AHEAD, aheadCount, prefetchAhead } from './prefetch';

/** A novel of chapters 12–15: each answer names the next one, the last names none. */
function backend(missing: number[] = []) {
    const asked: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: string) => {
        const url = new URL(input, 'http://localhost');
        asked.push(url.pathname + url.search);
        const number = Number(/\/chapters\/(\d+)$/.exec(url.pathname)?.[1]);
        if (missing.includes(number) || number < 12 || number > 15) {
            return new Response(JSON.stringify({ detail: 'Такої глави немає.' }), { status: 404 });
        }
        return new Response(JSON.stringify({ novelSlug: 'mah-vody', number, title: `Глава ${number}`, blocks: [], previous: number - 1, next: number < 15 ? number + 1 : null }));
    }));
    return asked;
}

describe('loading chapters ahead', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('loads the next three chapters one after another', async () => {
        const asked = backend();
        const client = new QueryClient();

        expect(await prefetchAhead(client, 'mah-vody', 'panrid', 12, 3)).toEqual([12, 13, 14]);

        expect(asked).toEqual(['/api/novels/mah-vody/chapters/12?t=panrid', '/api/novels/mah-vody/chapters/13?t=panrid', '/api/novels/mah-vody/chapters/14?t=panrid']);
        expect(client.getQueryData(['chapter', 'mah-vody', 'panrid', 14])).toMatchObject({ title: 'Глава 14' });
    });

    it('stops at the last chapter', async () => {
        backend();
        expect(await prefetchAhead(new QueryClient(), 'mah-vody', undefined, 14, 3)).toEqual([14, 15]);
    });

    it('stops at the first failure', async () => {
        const asked = backend([13]);
        expect(await prefetchAhead(new QueryClient(), 'mah-vody', undefined, 12, 3)).toEqual([12]);
        expect(asked).toHaveLength(2);
    });

    it('does not ask again for chapters already loaded', async () => {
        const asked = backend();
        const client = new QueryClient();
        await prefetchAhead(client, 'mah-vody', undefined, 12, 2);
        await prefetchAhead(client, 'mah-vody', undefined, 13, 2);

        expect(asked.map((path) => path.split('/').at(-1))).toEqual(['12', '13', '14']);
    });

    it('loads nothing when there is no next chapter or nothing to load', async () => {
        const asked = backend();
        expect(await prefetchAhead(new QueryClient(), 'mah-vody', undefined, null, 3)).toEqual([]);
        expect(await prefetchAhead(new QueryClient(), 'mah-vody', undefined, 12, 0)).toEqual([]);
        expect(asked).toEqual([]);
    });

    it('loads less offline or when the phone saves data', () => {
        const onLine = vi.spyOn(navigator, 'onLine', 'get');
        onLine.mockReturnValue(true);
        expect(aheadCount()).toBe(AHEAD);

        Object.defineProperty(navigator, 'connection', { value: { saveData: true }, configurable: true });
        expect(aheadCount()).toBe(1);

        onLine.mockReturnValue(false);
        expect(aheadCount()).toBe(0);

        Reflect.deleteProperty(navigator, 'connection');
        onLine.mockRestore();
    });
});
