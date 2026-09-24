import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { authApi } from '../../auth/api';
import { meQuery, useMe } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import styles from '../pages.module.css';

/** The «Я» tab: for a guest — sign in; for a member — profile and everything personal. */
export function MePage() {
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const logout = useMutation({
        mutationFn: authApi.logout,
        onSuccess: () => {
            client.setQueryData(meQuery.queryKey, null);
            client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' });
            void navigate({ to: '/' });
        },
    });

    if (!me) {
        return (
            <section className={styles.narrow}>
                <h1 className={styles.title}>Я</h1>
                <p className={styles.lead}>Увійдіть, щоб мати бібліотеку на всіх пристроях, коментувати й пропонувати правки.</p>
                <div className={styles.form}>
                    <LinkButton to="/login" wide>Увійти</LinkButton>
                    <LinkButton to="/register" wide variant="secondary">Зареєструватися</LinkButton>
                </div>
            </section>
        );
    }
    return (
        <section className={styles.narrow}>
            <Link to="/u/$nick" params={{ nick: me.nick }} className={styles.card}>
                <Avatar nick={me.nick} url={me.avatarUrl} size={56} />
                <div>
                    <div style={{ font: '600 18px var(--font-reading)' }}>{me.nick}</div>
                    <div className={styles.muted}>Переглянути профіль</div>
                </div>
            </Link>
            <nav className={styles.menu} aria-label="Особисте">
                <Link to="/me/settings" className={styles.menuItem}>Налаштування</Link>
                <Link to="/me/settings/privacy" className={styles.menuItem}>Приватність</Link>
            </nav>
            <div style={{ marginTop: 24 }}>
                <Button variant="danger" wide onPress={() => logout.mutate()} pending={logout.isPending} pendingLabel="Виходимо…">
                    Вийти
                </Button>
            </div>
        </section>
    );
}
