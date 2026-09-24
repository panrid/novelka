import { useEffect, useState } from 'react';
import { roleNames, useAuth, type Role } from '../auth/AuthContext';
import { mutate } from '../api/client';
import { useResource } from '../hooks/useResource';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { BalanceSummary } from '../components/BalanceSummary';
import { HelpTip } from '../components/HelpTip';

interface Profile {
    id: string; username: string; email: string | null; role: Role; nicknameChanges: number; nicknameAvailableAt: string | null;
}
const dateTime = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'long', timeStyle: 'short' });

export function ProfilePage() {
    const auth = useAuth();
    const resource = useResource<Profile>('/profile');
    const [profile, setProfile] = useState<Profile>();
    const [nickname, setNickname] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const nicknameAction = useAction();
    const emailAction = useAction();
    useEffect(() => { if (resource.data) setProfile(resource.data); }, [resource.data]);
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!profile) return <Loading />;
    const waiting = profile.nicknameAvailableAt ? new Date(profile.nicknameAvailableAt) : null;
    return <div className="page workspace profile-page"><p className="eyebrow">Обліковий запис</p><h1>Профіль</h1>
        <dl className="profile-summary">
            <div><dt>Нік</dt><dd>{profile.username}</dd></div>
            <div><dt>Email</dt><dd>{profile.email ?? <span className="muted">не вказано</span>}</dd></div>
            <div><dt>Роль</dt><dd>{roleNames[profile.role]}</dd></div>
        </dl>
        <section className="profile-section" aria-labelledby="balance-title">
            <h2 id="balance-title">Баланс перекладу<HelpTip label="Що таке баланс">З балансу оплачується автоматичний переклад ваших новел. Запуск резервує бюджет завдання, а після завершення списується лише фактична вартість. Баланс поповнює власник сайту.</HelpTip></h2>
            <BalanceSummary />
        </section>
        <section className="profile-section" aria-labelledby="nickname-title">
            <h2 id="nickname-title">Змінити нік<HelpTip label="Правила зміни ніка">Нік — публічне ім’я й спосіб входу. Перша зміна доступна одразу, друга — через 2 години, третя — через 2 тижні, далі — не частіше ніж раз на 2 місяці. Після зміни старий нік більше не підходить для входу, а email працює як раніше.</HelpTip></h2>
            <p className={waiting ? 'notice' : 'muted'} role="status">{waiting
                ? `Наступна зміна буде доступна ${dateTime.format(waiting)}.` : 'Змінити нік можна зараз.'}</p>
            <form className="inline-form" onSubmit={event => {
                event.preventDefault();
                void nicknameAction.run(async () => {
                    setProfile(await mutate<Profile>('/profile/nickname', { nickname })); setNickname(''); await auth.refresh();
                }, 'Нік змінено. Для входу використовуйте новий нік або email.');
            }}>
                <label>Новий нік<input autoComplete="nickname" pattern="[a-zA-Z0-9][a-zA-Z0-9_-]{2,39}" required disabled={!!waiting}
                    value={nickname} onChange={event => setNickname(event.target.value)} /></label>
                <button className="button" disabled={!!waiting || nicknameAction.busy}>Змінити нік</button>
            </form>
            <ActionNotice {...nicknameAction} />
        </section>
        <section className="profile-section" aria-labelledby="email-title">
            <h2 id="email-title">Змінити email</h2>
            <form className="inline-form" onSubmit={event => {
                event.preventDefault();
                void emailAction.run(async () => {
                    setProfile(await mutate<Profile>('/profile/email', { email, password })); setEmail(''); setPassword('');
                }, 'Email змінено.');
            }}>
                <label>Новий email<input type="email" autoComplete="email" maxLength={254} required value={email} onChange={event => setEmail(event.target.value)} /></label>
                <label>Поточний пароль<input type="password" autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} /></label>
                <button className="button" disabled={emailAction.busy}>Змінити email</button>
            </form>
            <ActionNotice {...emailAction} />
        </section>
    </div>;
}
