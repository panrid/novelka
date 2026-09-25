/**
 * The only way the app talks to the backend. Same origin, session cookie, CSRF header
 * on every change. Failures become {@link ApiError} carrying the server's human message.
 */

export class ApiError extends Error {
    readonly status: number;
    /** A stable code from the server for cases the page reacts to, e.g. 'email-not-verified'. */
    readonly reason: string | undefined;

    constructor(status: number, message: string, reason?: string) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.reason = reason;
    }
}

const FALLBACK_MESSAGE = 'Щось пішло не так. Спробуйте ще раз трохи пізніше.';
const OFFLINE_MESSAGE = 'Немає зʼєднання з сервером. Перевірте інтернет і спробуйте ще раз.';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

function csrfToken(): string | undefined {
    const cookie = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='));
    return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : undefined;
}

async function problem(response: Response): Promise<ApiError> {
    try {
        const body: unknown = await response.json();
        if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
            const reason = 'reason' in body && typeof body.reason === 'string' ? body.reason : undefined;
            return new ApiError(response.status, body.detail, reason);
        }
    } catch {
        // Not JSON (a proxy page, an empty body): fall through to the generic text.
    }
    return new ApiError(response.status, FALLBACK_MESSAGE);
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
        throw await problem(response);
    }
    // A change that returns nothing may answer 200 with an empty body, not only 204.
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
}
