import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams } from '@tanstack/react-router';
import { peopleApi } from '../../auth/api';
import { useMe } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { monthYearGenitive } from '../../lib/dates';
import { Notice } from '../../ui/Notice';
import styles from '../pages.module.css';


export function UserPage() {
    const { nick } = useParams({ strict: false }) as { nick: string };
    const me = useMe();
    const profile = useQuery({ queryKey: ['user', nick.toLowerCase()], queryFn: () => peopleApi.profile(nick) });

    if (profile.isPending) {
        return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    }
    if (profile.isError) {
        return (
            <section className={styles.narrow}>
                <Notice tone="error">{profile.error.message}</Notice>
            </section>
        );
    }
    const person = profile.data;
    if (person.nick !== nick) {
        // An old nick or different letter case: show the current address.
        return <Navigate to="/u/$nick" params={{ nick: person.nick }} replace />;
    }
    return (
        <section className={styles.narrow}>
            <div className={styles.row}>
                <Avatar nick={person.nick} url={person.avatarUrl} size={80} />
                <div>
                    <h1 className={styles.title} style={{ marginBottom: 2 }}>{person.nick}</h1>
                    <p className={styles.muted}>{`на Новелці з ${monthYearGenitive(new Date(person.memberSince))}`}</p>
                </div>
            </div>
            {person.bio && <p className={styles.bio} style={{ marginTop: 18 }}>{person.bio}</p>}
            {me?.nick === person.nick && (
                <div className={styles.links}>
                    <Link to="/me/settings">Редагувати профіль</Link>
                </div>
            )}
        </section>
    );
}
