import { useEffect, useState } from 'react';
import { getJson } from '../api/client';

export function useResource<T>(path: string, retainOnRetry = false) {
    const [attempt, setAttempt] = useState(0);
    const [state, setState] = useState<{ path: string; data?: T; error?: string; loading?: boolean }>({ path, loading: true });
    useEffect(() => {
        const controller = new AbortController();
        setState(previous => retainOnRetry ? { path, data: previous.data, loading: true } : { path, loading: true });
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
    const current = state.path === path ? state : retainOnRetry ? { path, data: state.data, loading: true } : { path, loading: true };
    return { ...current, retry: () => setAttempt(value => value + 1) };
}
