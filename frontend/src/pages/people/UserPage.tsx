import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams } from '@tanstack/react-router';
import { peopleApi } from '../../auth/api';
import { useMe } from '../../auth/me';
import { messagingApi } from '../../inbox/api';
import { Cover } from '../../reading/Cover';
import { chaptersWord, readingApi } from '../../reading/api';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
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
            {me && me.nick !== person.nick && <WriteButton nick={person.nick} />}
            <Works nick={person.nick} />
        </section>
    );
}

/** «Написати»: opens the conversation with this person, starting it if needed. */
function WriteButton({ nick }: { nick: string }) {
    const navigate = useNavigate();
    const start = useMutation({
        mutationFn: () => messagingApi.direct(nick),
        onSuccess: ({ id }) => void navigate({ to: '/inbox/messages/$id', params: { id: String(id) } }),
    });
    return (
        <div style={{ marginTop: 18 }}>
            <Button onPress={() => start.mutate()} pending={start.isPending} pendingLabel="Відкриваємо…">Написати</Button>
            {start.isError && <Notice tone="error">{start.error.message}</Notice>}
        </div>
    );
}

/** Translations and works of the teams the person owns or belongs to. */
function Works({ nick }: { nick: string }) {
    const works = useQuery({ queryKey: ['works', nick.toLowerCase()], queryFn: () => readingApi.works(nick) });
    if (!works.data?.length) return null;
    return (
        <div className={styles.section} style={{ marginTop: 24 }}>
            <h2 className={styles.sectionTitle}>Переклади й твори</h2>
            {works.data.map((card) => (
                <Link key={card.editionId} to="/n/$slug" params={{ slug: card.novelSlug }} search={{ t: card.teamHandle }} className={styles.row}
                    style={{ padding: '8px 0', color: 'var(--text)', textDecoration: 'none' }}>
                    <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={40} />
                    <div>
                        <div>{card.title}</div>
                        <div className={styles.muted}>{card.chapterCount} {chaptersWord(card.chapterCount)} · ${card.teamHandle}</div>
                    </div>
                </Link>
            ))}
        </div>
    );
}
