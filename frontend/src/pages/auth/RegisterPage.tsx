import { useMutation } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { authApi } from '../../auth/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../pages.module.css';

export function RegisterPage() {
    const [nick, setNick] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    const register = useMutation({ mutationFn: () => authApi.register(nick.trim(), email.trim(), password) });
    const resend = useMutation({ meta: { errorToast: true }, mutationFn: () => authApi.resendVerification(email.trim()) });

    function submit(event: FormEvent) {
        event.preventDefault();
        register.mutate();
    }

    if (register.isSuccess) {
        return (
            <section className={styles.narrow}>
                <h1 className={styles.title}>Перевірте пошту</h1>
                <p className={styles.lead}>
                    Ми надіслали лист на <b>{email.trim()}</b>. Відкрийте посилання з нього — і все, ви на Новелці.
                </p>
                <Notice tone="info">Лист не прийшов за кілька хвилин? Загляньте в «Спам» або надішліть ще раз.</Notice>
                <div className={styles.buttons} style={{ marginTop: 16 }}>
                    <Button variant="secondary" onPress={() => resend.mutate()} pending={resend.isPending} pendingLabel="Надсилаємо…" isDisabled={resend.isSuccess}>
                        {resend.isSuccess ? 'Надіслали ще раз' : 'Надіслати ще раз'}
                    </Button>
                </div>
            </section>
        );
    }

    const error = register.error instanceof ApiError ? register.error.message : undefined;
    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Реєстрація</h1>
            <p className={styles.lead}>Нік бачать усі. Пошту — лише ви: вона для входу й відновлення пароля.</p>
            <form className={styles.form} onSubmit={submit}>
                <TextInput label="Нік" value={nick} onChange={setNick} autoComplete="username" isRequired
                    hint="3–30 символів: латиниця або кирилиця, цифри, «_» і «-»." />
                <TextInput label="Пошта" type="email" value={email} onChange={setEmail} autoComplete="email" isRequired />
                <TextInput label="Пароль" type="password" value={password} onChange={setPassword} autoComplete="new-password" isRequired
                    hint="Щонайменше 10 символів. Фраза з кількох слів — найзручніший надійний пароль." />
                {error && <Notice tone="error">{error}</Notice>}
                <Button type="submit" wide pending={register.isPending} pendingLabel="Реєструємо…">
                    Зареєструватися
                </Button>
            </form>
            <div className={styles.links}>
                <span className={styles.muted}>Уже є акаунт?</span>
                <Link to="/login">Увійти</Link>
            </div>
        </section>
    );
}
