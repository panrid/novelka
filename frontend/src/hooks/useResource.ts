import { useEffect, useState } from 'react';
import { getJson } from '../api/client';

export function useResource<T>(path: string, retainOnRetry = false) {
    const [attempt, setAttempt] = useState(0);
    const [state, setState] = useState<{ path: string; data?: T; error?: string }>({ path });
    useEffect(() => {
        const controller = new AbortController();
        setState(previous => retainOnRetry && previous.path === path ? { path, data: previous.data } : { path });
        getJson<T>(path, controller.signal)
            .then(data => { if (!controller.signal.aborted) setState({ path, data }); })
            .catch(error => {
                if (!controller.signal.aborted) setState({
                    path,
                    error: error instanceof TypeError
                        ? 'Немає зв’язку із сервером. Перевірте підключення й спробуйте ще раз.'
                        : error.message,
                });
            });
        return () => controller.abort();
    }, [path, attempt, retainOnRetry]);
    const current = state.path === path ? state : { path };
    return { ...current, retry: () => setAttempt(value => value + 1) };
}
