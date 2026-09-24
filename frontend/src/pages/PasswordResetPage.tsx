import { useState } from 'react';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

/**
 * Without a token: asks for the email and sends a one-time link. With a token (the link from the letter):
 * sets a new password. The answer never reveals whether an address has an account.
 */
export function PasswordResetPage({ token }: { token: string }) {
    const action = useAction();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [repeat, setRepeat] = useState('');
    const [done, setDone] = useState(false);
    if (!token) return <div className="auth-card">
        <p className="eyebrow">Відновлення доступу</p>
        <h1>Забули пароль?</h1>
        <p className="muted">Вкажіть email акаунта. Ми надішлемо посилання, щоб задати новий пароль. Воно діє 30 хвилин.</p>
        <form className="stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                const result = await mutate<{ message: string }>('/auth/password-reset', { email });
                setDone(true);
                return result;
            }, 'Якщо акаунт із цією адресою існує, ми надіслали лист. Перевірте також папку «Спам».');
        }}>
            <label>Email<input type="email" autoComplete="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} /></label>
            <button className="button" disabled={action.busy || done}>Надіслати посилання</button>
            <ActionNotice {...action} />
        </form>
        <a className="plain-button" href="#/login">Згадали пароль? Увійти</a>
    </div>;
    return <div className="auth-card">
        <p className="eyebrow">Відновлення доступу</p>
        <h1>Новий пароль</h1>
        {done ? <><p role="status">Пароль змінено. Тепер увійдіть із новим паролем.</p><a className="button" href="#/login">Увійти</a></>
            : <form className="stack-form" onSubmit={event => {
                event.preventDefault();
                if (password !== repeat) return;
                void action.run(async () => { await mutate('/auth/password-reset/confirm', { token, password }); setDone(true); });
            }}>
                <label>Новий пароль<input type="password" autoComplete="new-password" minLength={12} required value={password} onChange={event => setPassword(event.target.value)} /></label>
                <label>Повторіть пароль<input type="password" autoComplete="new-password" minLength={12} required value={repeat} onChange={event => setRepeat(event.target.value)} /></label>
                {repeat && password !== repeat && <p className="field-error" role="alert">Паролі не збігаються.</p>}
                <small className="muted">Від 12 символів, до 72 байтів UTF-8.</small>
                <button className="button" disabled={action.busy || password !== repeat}>Зберегти пароль</button>
                <ActionNotice {...action} />
                {action.error && <a href="#/forgot-password">Надіслати нове посилання</a>}
            </form>}
    </div>;
}
