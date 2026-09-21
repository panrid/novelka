export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
    const response = await fetch('/api' + path, { signal, headers: { Accept: 'application/json' } });
    if (!response.ok) {
        await fail(response);
    }
    try {
        return await response.json() as T;
    } catch {
        throw new Error('Сервер повернув неочікувану відповідь. Перевірте підключення до API.');
    }
}

async function fail(response: Response): Promise<never> {
    const messages: Record<number, string> = {
        401: 'Увійдіть у свій обліковий запис. Перевірте логін і пароль.',
        403: 'Недостатньо прав або сесія застаріла. Оновіть сторінку.',
        404: 'Ця новела або глава ще недоступна для читання.',
        409: 'Дані вже змінилися. Оновіть сторінку та перевірте зміни.',
        429: 'Забагато спроб. Спробуйте пізніше.',
    };
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || messages[response.status] || 'Не вдалося виконати запит. Перевірте сервер Novelka.');
}

async function write<T>(path: string, body?: BodyInit, contentType?: string, method = 'POST'): Promise<T> {
    const csrf = await getJson<{ token: string; headerName: string }>('/auth/csrf');
    const response = await fetch('/api' + path, { method, body, headers: {
        Accept: 'application/json', [csrf.headerName]: csrf.token,
        ...(contentType ? { 'Content-Type': contentType } : {}),
    } });
    if (!response.ok) await fail(response);
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export function mutate<T = unknown>(path: string, body?: unknown, method = 'POST'): Promise<T> {
    return write(path, body === undefined ? undefined : JSON.stringify(body), 'application/json', method);
}

export function login(username: string, password: string) {
    return write('/auth/login', new URLSearchParams({ username, password }), 'application/x-www-form-urlencoded');
}
