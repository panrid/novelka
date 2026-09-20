export async function getJson<T>(path: string, signal: AbortSignal): Promise<T> {
    const response = await fetch('/api' + path, { signal, headers: { Accept: 'application/json' } });
    if (!response.ok) {
        if (response.status === 404) throw new Error('Ця новела або глава ще недоступна для читання.');
        throw new Error('Не вдалося отримати дані. Перевірте, чи запущений сервер Novelka.');
    }
    try {
        return await response.json() as T;
    } catch {
        throw new Error('Сервер повернув неочікувану відповідь. Перевірте підключення до API.');
    }
}
