import { useEffect, useRef, useState } from 'react';
import { mutate } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/** Opens from the confirmation letter and confirms the address once; works with or without signing in. */
export function VerifyEmailPage({ token }: { token: string }) {
    const { user } = useAuth();
    const [state, setState] = useState<{ ok: boolean; message: string } | null>(null);
    const sent = useRef(false);
    useEffect(() => {
        if (sent.current) return;
        sent.current = true;
        if (!token) { setState({ ok: false, message: 'У посиланні немає коду підтвердження.' }); return; }
        mutate<{ message: string }>('/auth/verify-email', { token })
            .then(result => setState({ ok: true, message: result.message }))
            .catch(error => setState({ ok: false, message: error instanceof Error ? error.message : 'Не вдалося підтвердити email.' }));
    }, [token]);
    return <div className="auth-card">
        <p className="eyebrow">Підтвердження email</p>
        <h1>{!state ? 'Підтверджуємо…' : state.ok ? 'Email підтверджено' : 'Не вдалося підтвердити'}</h1>
        {state && <p role={state.ok ? 'status' : 'alert'}>{state.message}</p>}
        {state && (user ? <a className="button" href="#/profile">До профілю</a> : <a className="button" href="#/login">Увійти</a>)}
    </div>;
}
