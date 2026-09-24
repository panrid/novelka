import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { mutate, login } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

export function AuthPage() {
    const auth = useAuth();
    const action = useAction();
    const [register, setRegister] = useState(false);
    const [identifier, setIdentifier] = useState('');
    const [nickname, setNickname] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    if (auth.user) return <div className="page workspace"><h1>Ви вже увійшли як {auth.user.username}</h1><a href="#/">До каталогу</a></div>;
    return <div className="auth-card">
        <p className="eyebrow">Ваша участь робить переклад кращим</p>
        <h1>{register ? 'Створити обліковий запис' : 'З поверненням'}</h1>
        <p className="muted">Читайте без входу. Увійдіть, щоб пропонувати правки.</p>
        <form className="stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                if (register) {
                    await mutate('/auth/register', { username: nickname, email, password });
                    setRegister(false); setIdentifier(email); setPassword('');
                } else {
                    await login(identifier, password); await auth.refresh(); window.location.hash = '/';
                }
            }, register ? 'Обліковий запис створено. Ми надіслали лист для підтвердження email — тепер увійдіть.' : 'Вхід виконано.');
        }}>
            {register ? <>
                <label>Нік<input autoComplete="nickname" pattern="[a-zA-Z0-9][a-zA-Z0-9_-]{2,39}" required value={nickname} onChange={event => setNickname(event.target.value)} /></label>
                <label>Email<input type="email" autoComplete="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} /></label>
            </> : <label>Email або нік<input autoComplete="username" required value={identifier} onChange={event => setIdentifier(event.target.value)} /></label>}
            <label>Пароль<input type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 12 : undefined} required value={password} onChange={event => setPassword(event.target.value)} /></label>
            {register && <small className="muted">Нік — публічне ім’я: 3–40 латинських літер, цифр, _ або -. Email не показується іншим і завжди підходить для входу. Пароль: від 12 символів, до 72 байтів UTF-8.</small>}
            <button className="button" disabled={action.busy}>{register ? 'Зареєструватися' : 'Увійти'}</button>
            <ActionNotice {...action} />
        </form>
        {!register && <a className="plain-button" href="#/forgot-password">Забули пароль?</a>}
        {auth.registrationOpen && <button className="plain-button" onClick={() => setRegister(value => !value)}>{register ? 'Уже є обліковий запис? Увійти' : 'Створити обліковий запис'}</button>}
    </div>;
}
