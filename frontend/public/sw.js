/*
 * Новелка's service worker (етап 21, п. 12): the reader works without the internet.
 *
 * - The app shell (index.html and the files it names) is kept, so the site opens offline.
 *   Pages always try the network first: a new deploy is what people get as soon as they are
 *   online, and every page load refreshes the kept shell. The worker itself takes over at once
 *   (skipWaiting + clients.claim), so nobody stays on an old version of it either.
 * - Built files (/assets/*) carry a hash in their names: kept once, served from the cache.
 * - Only the API answers the reader needs offline are kept (see apiRule): chapters, the novel,
 *   the library, the home page, the glossary hints and the account they belong to. Network
 *   first; without the network the kept answer comes back marked with X-Novelka-Cache: offline,
 *   so the app says «Ви офлайн — показуємо збережені глави». Studio, admin, messages and any
 *   change (POST, PUT, PATCH, DELETE) never touch the cache. The app deletes the API cache on
 *   sign-out and whenever the signed-in account changes (src/offline/personal.ts).
 *
 * Plain JavaScript served as is from /sw.js; the rules are tested in src/offline/sw.test.ts.
 */
'use strict';

const SHELL_CACHE = 'novelka-shell-v1';
const ASSET_CACHE = 'novelka-assets-v1';
const API_CACHE = 'novelka-api-v1';
const KEPT = [SHELL_CACHE, ASSET_CACHE, API_CACHE];
const SHELL_URL = '/index.html';
const STATIC_FILES = ['/appearance-boot.js', '/favicon.svg', '/manifest.webmanifest', '/icon-192.png', '/icon-512.png',
    '/icon-maskable-512.png', '/apple-touch-icon.png'];
/** Chapters kept at most; older ones go first. A chapter is 10–60 KB of JSON. */
const MAX_CHAPTERS = 80;
/** Other kept API answers at most (novel pages, library pages, hints). */
const MAX_OTHER_API = 120;
/** Built files at most: old deploys' chunks go first. */
const MAX_ASSETS = 300;
/** A slow network (a train, a basement) gives way to the kept answer after this long. */
const NETWORK_PATIENCE_MS = 7000;
/** The shell is refreshed at most this often while pages load. */
const SHELL_REFRESH_MS = 10 * 60 * 1000;

/** What the worker keeps of an API answer: 'chapter', 'other', or null (never kept). */
function apiRule(pathname) {
    if (/^\/api\/novels\/[^/]+\/chapters\/\d+$/.test(pathname)) return 'chapter';
    if (/^\/api\/novels\/[^/]+$/.test(pathname)) return 'other';
    if (/^\/api\/novels\/[^/]+\/(hints|terms)$/.test(pathname)) return 'other';
    if (pathname === '/api/library' || pathname === '/api/home' || pathname === '/api/me') return 'other';
    return null;
}

/** How the worker answers a request: 'api', 'asset', 'static', 'page', or null (left to the browser). */
function route(request, origin) {
    if (request.method !== 'GET' || request.headers.get('Range')) return null;
    const url = new URL(request.url);
    if (url.origin !== origin) return null;
    if (url.pathname.startsWith('/api/')) return apiRule(url.pathname) ? 'api' : null;
    if (url.pathname.startsWith('/assets/')) return 'asset';
    if (STATIC_FILES.includes(url.pathname)) return 'static';
    if (request.mode === 'navigate' && !/^\/(media|login|oauth2)(\/|$)/.test(url.pathname)) return 'page';
    return null;
}

/** The built files index.html names: scripts, styles, preloads. */
function shellAssets(html) {
    const found = new Set();
    for (const match of html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)) found.add(match[1]);
    return [...found];
}

/** A kept answer, marked so the app knows it is not fresh. */
function markedOffline(response) {
    const headers = new Headers(response.headers);
    headers.set('X-Novelka-Cache', 'offline');
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

async function trim(cacheName, max, matches) {
    const cache = await caches.open(cacheName);
    const keys = (await cache.keys()).filter((request) => matches(new URL(request.url).pathname));
    // Keys come in the order they were put: the oldest first.
    await Promise.all(keys.slice(0, Math.max(0, keys.length - max)).map((request) => cache.delete(request)));
}

async function keepShell() {
    const response = await fetch(SHELL_URL, { cache: 'no-cache', credentials: 'same-origin' });
    if (!response.ok) return;
    const html = await response.clone().text();
    const shell = await caches.open(SHELL_CACHE);
    await shell.put(SHELL_URL, response);
    const assets = await caches.open(ASSET_CACHE);
    await Promise.all(shellAssets(html).map(async (path) => {
        if (await assets.match(path)) return;
        const file = await fetch(path);
        if (file.ok) await assets.put(path, file);
    }));
    await trim(ASSET_CACHE, MAX_ASSETS, () => true);
}

let shellKeptAt = 0;

function refreshShell() {
    if (Date.now() - shellKeptAt < SHELL_REFRESH_MS) return Promise.resolve();
    shellKeptAt = Date.now();
    return keepShell().catch(() => undefined);
}

async function fromNetworkOrCache(event) {
    const { request } = event;
    const cache = await caches.open(API_CACHE);
    const network = fetch(request).then(async (response) => {
        // Only a full, successful answer is kept; an error page never replaces a good copy.
        if (response.ok && response.type === 'basic') {
            const pathname = new URL(request.url).pathname;
            const chapter = apiRule(pathname) === 'chapter';
            await cache.put(request, response.clone());
            // Not awaited: the answer should not wait for the clean-up.
            trim(API_CACHE, chapter ? MAX_CHAPTERS : MAX_OTHER_API, (path) => (apiRule(path) === 'chapter') === chapter)
                .catch(() => undefined);
        }
        return response;
    });
    const kept = await cache.match(request, { ignoreVary: true });
    if (!kept) return network;
    const slow = new Promise((resolve) => setTimeout(() => resolve('slow'), NETWORK_PATIENCE_MS));
    try {
        const first = await Promise.race([network, slow]);
        // A server error (the site restarting during a deploy) is no better than no network.
        if (first !== 'slow' && first.status < 500) return first;
    } catch {
        // No network at all: the kept answer below.
    }
    network.catch(() => undefined);
    return markedOffline(kept);
}

async function cacheFirst(request, cacheName) {
    const cache = await caches.open(cacheName);
    const kept = await cache.match(request);
    if (kept) return kept;
    const response = await fetch(request);
    if (response.ok && response.type === 'basic') await cache.put(request, response.clone());
    return response;
}

async function staleWhileRevalidate(event) {
    const cache = await caches.open(SHELL_CACHE);
    const kept = await cache.match(event.request);
    const fresh = fetch(event.request).then(async (response) => {
        if (response.ok) await cache.put(event.request, response.clone());
        return response;
    });
    if (kept) {
        event.waitUntil(fresh.catch(() => undefined));
        return kept;
    }
    return fresh;
}

async function page(event) {
    try {
        const response = await fetch(event.request);
        event.waitUntil(refreshShell());
        return response;
    } catch (error) {
        const shell = await caches.match(SHELL_URL, { cacheName: SHELL_CACHE });
        if (shell) return shell;
        throw error;
    }
}

self.addEventListener('install', (event) => {
    event.waitUntil((async () => {
        await keepShell().catch(() => undefined);
        const cache = await caches.open(SHELL_CACHE);
        await Promise.all(STATIC_FILES.map((path) => cache.add(path).catch(() => undefined)));
        shellKeptAt = Date.now();
        await self.skipWaiting();
    })());
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const names = await caches.keys();
        await Promise.all(names.filter((name) => name.startsWith('novelka-') && !KEPT.includes(name)).map((name) => caches.delete(name)));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const kind = route(event.request, self.location.origin);
    if (kind === 'api') event.respondWith(fromNetworkOrCache(event));
    else if (kind === 'asset') event.respondWith(cacheFirst(event.request, ASSET_CACHE));
    else if (kind === 'static') event.respondWith(staleWhileRevalidate(event));
    else if (kind === 'page') event.respondWith(page(event));
});
