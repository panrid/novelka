import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { authApi } from '../../auth/api';
import { useSetMe } from '../../auth/me';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../pages.module.css';

export function ResetPage() {
    const { token }: { token?: string } = useSearch({ strict: false });
    return token ? <NewPassword token={token} /> : <RequestLetter />;
}

function RequestLetter() {
    const [email, setEmail] = useState('');
    const request = useMutation({ mutationFn: () => authApi.requestReset(email.trim()) });

    function submit(event: FormEvent) {
        event.preventDefault();
        request.mutate();
    }

    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Відновлення пароля</h1>
            {request.isSuccess ? (
                <Notice tone="success">
                    Якщо на цю пошту є акаунт, ми надіслали лист із посиланням. Воно діє 30 хвилин.
                </Notice>
            ) : (
                <>
                    <p className={styles.lead}>Вкажіть пошту акаунта — надішлемо посилання, щоб задати новий пароль.</p>
                    <form className={styles.form} onSubmit={submit}>
                        <TextInput label="Пошта" type="email" value={email} onChange={setEmail} autoComplete="email" isRequired />
                        {request.isError && <Notice tone="error">{request.error.message}</Notice>}
                        <Button type="submit" wide pending={request.isPending} pendingLabel="Надсилаємо…">
                            Надіслати лист
                        </Button>
                    </form>
                </>
            )}
            <div className={styles.links}>
                <Link to="/login">До входу</Link>
            </div>
        </section>
    );
}

function NewPassword({ token }: { token: string }) {
    const [password, setPassword] = useState('');
    const navigate = useNavigate();
    const setMe = useSetMe();
    const reset = useMutation({
        mutationFn: () => authApi.confirmReset(token, password),
        onSuccess: (me) => {
            setMe(me);
            void navigate({ to: '/', replace: true });
        },
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        reset.mutate();
    }

    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Новий пароль</h1>
            <p className={styles.lead}>Після збереження всі інші пристрої вийдуть з акаунта.</p>
            <form className={styles.form} onSubmit={submit}>
                <TextInput label="Новий пароль" type="password" value={password} onChange={setPassword} autoComplete="new-password" isRequired
                    hint="Щонайменше 10 символів." />
                {reset.isError && <Notice tone="error">{reset.error.message}</Notice>}
                <Button type="submit" wide pending={reset.isPending} pendingLabel="Зберігаємо…">
                    Зберегти пароль
                </Button>
            </form>
        </section>
    );
}
