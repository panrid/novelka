/**
 * The only way the app talks to the backend. Same origin, session cookie, CSRF header
 * on every change. Failures become {@link ApiError} carrying the server's human message.
 */

export class ApiError extends Error {
    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
    }
}

const FALLBACK_MESSAGE = 'Щось пішло не так. Спробуйте ще раз трохи пізніше.';
const OFFLINE_MESSAGE = 'Немає зʼєднання з сервером. Перевірте інтернет і спробуйте ще раз.';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

function csrfToken(): string | undefined {
    const cookie = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='));
    return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : undefined;
}

async function problemMessage(response: Response): Promise<string> {
    try {
        const body: unknown = await response.json();
        if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
            return body.detail;
        }
    } catch {
        // Not JSON (a proxy page, an empty body): fall through to the generic text.
    }
    return FALLBACK_MESSAGE;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body !== undefined && !(init.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json');
    }
    if (!SAFE_METHODS.has(method)) {
        const token = csrfToken();
        if (token) {
            headers.set('X-XSRF-TOKEN', token);
        }
    }

    let response: Response;
    try {
        response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' });
    } catch {
        throw new ApiError(0, OFFLINE_MESSAGE);
    }
    if (!response.ok) {
        throw new ApiError(response.status, await problemMessage(response));
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return (await response.json()) as T;
}
