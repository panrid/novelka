import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { authApi } from '../../auth/api';
import { safeNext, useSetMe } from '../../auth/me';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../pages.module.css';

/** The first sign-in with Google: the person picks a nick, and the account is ready. */
export function GoogleNewcomerPage() {
    const newcomer = useQuery({ queryKey: ['google-newcomer'], queryFn: authApi.googleNewcomer, retry: false, staleTime: Infinity });
    if (newcomer.isError) {
        return (
            <section className={styles.narrow}>
                <h1 className={styles.title}>Вхід через Google</h1>
                <Notice tone="error">{newcomer.error.message}</Notice>
                <div className={styles.links}><Link to="/login">До входу</Link></div>
            </section>
        );
    }
    if (!newcomer.data) {
        return <section className={styles.narrow}><p className={styles.muted}>Завантажуємо…</p></section>;
    }
    return <NickForm email={newcomer.data.email} suggested={newcomer.data.nick} next={newcomer.data.next} />;
}

function NickForm({ email, suggested, next }: { email: string; suggested: string; next: string }) {
    const navigate = useNavigate();
    const setMe = useSetMe();
    const [nick, setNick] = useState(suggested);
    const create = useMutation({
        mutationFn: () => authApi.googleRegister(nick.trim()),
        onSuccess: (me) => {
            setMe(me);
            const to = safeNext(next);
            void navigate({ to: to === '/' ? '/welcome' : to, replace: true });
        },
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        create.mutate();
    }

    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Ще один крок</h1>
            <p className={styles.lead}>
                Google підтвердив пошту <b>{email}</b>. Оберіть нік — його бачать усі. Пароль не потрібен: входьте через Google.
            </p>
            <form className={styles.form} onSubmit={submit}>
                <TextInput label="Нік" value={nick} onChange={setNick} autoComplete="username" isRequired
                    hint="3–30 символів: латиниця або кирилиця, цифри, «_» і «-»." />
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                <Button type="submit" wide pending={create.isPending} pendingLabel="Створюємо…">Створити акаунт</Button>
            </form>
        </section>
    );
}
