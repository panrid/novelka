import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { authApi } from '../../auth/api';
import { safeNext, useSetMe } from '../../auth/me';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../pages.module.css';

export function LoginPage() {
    const search: { next?: string } = useSearch({ strict: false });
    const navigate = useNavigate();
    const setMe = useSetMe();
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [resendEmail, setResendEmail] = useState('');

    const signIn = useMutation({
        mutationFn: () => authApi.login(login.trim(), password),
        onSuccess: (me) => {
            setMe(me);
            void navigate({ to: safeNext(search.next), replace: true });
        },
    });
    const resend = useMutation({ mutationFn: () => authApi.resendVerification(resendEmail.trim()) });

    const error = signIn.error instanceof ApiError ? signIn.error : null;
    const notVerified = error?.reason === 'email-not-verified';

    function submit(event: FormEvent) {
        event.preventDefault();
        if (login.includes('@')) {
            setResendEmail(login.trim());
        }
        signIn.mutate();
    }

    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Вхід</h1>
            <p className={styles.lead}>Щоб читати, достатньо просто відкрити новелу. Акаунт потрібен для бібліотеки, коментарів і правок.</p>
            <form className={styles.form} onSubmit={submit}>
                <TextInput label="Нік або пошта" value={login} onChange={setLogin} autoComplete="username" isRequired />
                <TextInput label="Пароль" type="password" value={password} onChange={setPassword} autoComplete="current-password" isRequired />
                {error && !notVerified && <Notice tone="error">{error.message}</Notice>}
                {notVerified && (
                    <Notice tone="info">
                        <p>{error.message}</p>
                        {resend.isSuccess ? (
                            <p style={{ marginTop: 8 }}>Надіслали ще раз. Перевірте пошту, зокрема «Спам».</p>
                        ) : (
                            <div className={styles.form} style={{ marginTop: 10 }}>
                                <TextInput label="Пошта для листа" type="email" value={resendEmail} onChange={setResendEmail} />
                                <Button variant="secondary" onPress={() => resend.mutate()} pending={resend.isPending} pendingLabel="Надсилаємо…" isDisabled={!resendEmail.includes('@')}>
                                    Надіслати лист ще раз
                                </Button>
                            </div>
                        )}
                    </Notice>
                )}
                <Button type="submit" wide pending={signIn.isPending} pendingLabel="Входимо…">
                    Увійти
                </Button>
            </form>
            <div className={styles.links}>
                <Link to="/reset">Забули пароль?</Link>
                <Link to="/register" search={search.next ? { next: search.next } : {}}>Зареєструватися</Link>
            </div>
        </section>
    );
}
