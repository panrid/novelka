import source from '../../public/sw.js?raw';

type Rules = {
    mayKeepApi: (response: { ok: boolean; type: string; headers: Headers }) => boolean;
    apiRule: (pathname: string) => 'chapter' | 'other' | null;
    route: (request: { method: string; url: string; mode?: string; headers: Headers }, origin: string) => string | null;
    shellAssets: (html: string) => string[];
};

/** The service worker's own functions, without a service worker around them. */
const rules = new Function('self', `${source}\nreturn { apiRule, route, shellAssets, mayKeepApi };`)({ addEventListener: () => {} }) as Rules;

const ORIGIN = 'https://novelka.in.ua';
const request = (path: string, method = 'GET', mode = 'cors', headers: Record<string, string> = {}) =>
    ({ method, url: ORIGIN + path, mode, headers: new Headers(headers) });

describe('the service worker', () => {
    it('never caches a private response even when it is successful', () => {
        expect(rules.mayKeepApi({ ok: true, type: 'basic', headers: new Headers() })).toBe(true);
        expect(rules.mayKeepApi({ ok: true, type: 'basic', headers: new Headers({ 'X-Novelka-Private': 'true' }) })).toBe(false);
    });

    it('keeps what the reader needs offline', () => {
        expect(rules.apiRule('/api/novels/mah-vody/chapters/12')).toBe('chapter');
        for (const path of ['/api/novels/mah-vody', '/api/novels/mah-vody/hints', '/api/novels/mah-vody/terms', '/api/library', '/api/home', '/api/me']) {
            expect(rules.apiRule(path)).toBe('other');
        }
    });

    it('never keeps Studio, admin, messages or other personal answers', () => {
        for (const path of ['/api/studio/editions/7/chapters/12', '/api/admin/users', '/api/messages', '/api/conversations/3',
            '/api/notifications', '/api/me/wallet', '/api/inbox', '/api/novels/mah-vody/chapters/12/suggestions', '/api/progress/7',
            '/api/auth/providers', '/api/catalog']) {
            expect(rules.apiRule(path)).toBeNull();
        }
    });

    it('leaves every change to the network', () => {
        for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
            expect(rules.route(request('/api/novels/mah-vody/chapters/12', method), ORIGIN)).toBeNull();
            expect(rules.route(request('/api/library', method), ORIGIN)).toBeNull();
        }
        expect(rules.route(request('/api/novels/mah-vody/chapters/12'), ORIGIN)).toBe('api');
    });

    it('answers pages, built files and the app’s own files, nothing from elsewhere', () => {
        expect(rules.route(request('/n/mah-vody/12', 'GET', 'navigate'), ORIGIN)).toBe('page');
        expect(rules.route(request('/library', 'GET', 'navigate'), ORIGIN)).toBe('page');
        expect(rules.route(request('/assets/index-abc123.js'), ORIGIN)).toBe('asset');
        expect(rules.route(request('/manifest.webmanifest'), ORIGIN)).toBe('static');
        expect(rules.route(request('/login/oauth2/code/google?code=1', 'GET', 'navigate'), ORIGIN)).toBeNull();
        expect(rules.route(request('/media/covers/1.jpg'), ORIGIN)).toBeNull();
        expect(rules.route(request('/api/studio/editions/7'), ORIGIN)).toBeNull();
        expect(rules.route({ ...request('/x.js'), url: 'https://accounts.google.com/x.js' }, ORIGIN)).toBeNull();
        expect(rules.route(request('/assets/a.mp3', 'GET', 'cors', { Range: 'bytes=0-' }), ORIGIN)).toBeNull();
    });

    it('finds the built files index.html names', () => {
        const html = '<script type="module" crossorigin src="/assets/index-a1.js"></script>'
            + '<link rel="modulepreload" crossorigin href="/assets/vendor-b2.js"><link rel="stylesheet" href="/assets/index-c3.css">'
            + '<link rel="icon" href="/favicon.svg"><script src="/appearance-boot.js"></script>';
        expect(rules.shellAssets(html)).toEqual(['/assets/index-a1.js', '/assets/vendor-b2.js', '/assets/index-c3.css']);
    });
});
